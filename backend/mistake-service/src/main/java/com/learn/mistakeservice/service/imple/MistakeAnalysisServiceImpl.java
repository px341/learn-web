package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.mapper.MistakeMapper;
import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.AnalysisPermanentException;
import com.learn.mistakeservice.service.MistakeAnalysisService;
import com.learn.mistakeservice.service.MistakeImageStorageService;
import com.learn.mistakeservice.service.QuestionAnalysisClient;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * 错题异步分析的业务编排服务。
 *
 * <p>该服务由 RabbitMQ 消费者调用，负责串联任务领取、题目数据读取、可选图片加载、
 * 分析模型调用、结果校验和状态持久化。任务通过数据库中的分析状态和版本号实现幂等与
 * 并发保护：只有 {@code QUEUED} 状态能够被领取，写入分析结果时还必须匹配领取后的
 * 版本号，因此重复消息或过期 Worker 不会覆盖较新的处理结果。</p>
 *
 * <p>异常按照是否值得重试分为两类：</p>
 * <ul>
 *   <li>{@link AnalysisPermanentException} 表示内容或模型结果无法通过重试修复，任务会被
 *   直接标记为 {@code FAILED}；</li>
 *   <li>其他运行时异常视为临时故障，任务会先退回 {@code QUEUED}，再把异常继续抛给
 *   消息监听器，由监听器的重试和死信策略决定后续处理。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MistakeAnalysisServiceImpl implements MistakeAnalysisService {

    /** 数据库存储的失败原因最大字符数，避免把异常详情无限制写入业务表。 */
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    /** 提供任务状态领取、版本校验和最终结果写入。 */
    private final MistakeMapper mistakeMapper;

    /** 根据私有对象键读取错题原图；纯文字题不会访问对象存储。 */
    private final MistakeImageStorageService imageStorageService;

    /** 调用外部分析模型并返回结构化分析结果。 */
    private final QuestionAnalysisClient questionAnalysisClient;

    /**
     * 执行一次错题分析尝试。
     *
     * <p>处理流程如下：</p>
     * <ol>
     *   <li>原子地把任务从 {@code QUEUED} 领取为 {@code ANALYZING}；</li>
     *   <li>读取本次分析所需的题目快照及其版本号；</li>
     *   <li>按需从对象存储加载原图，并调用分析客户端；</li>
     *   <li>校验结构化结果后，使用版本号条件更新任务为 {@code COMPLETED}。</li>
     * </ol>
     *
     * <p>领取失败通常表示消息重复、任务已被其他 Worker 处理或任务已经终结，此时直接
     * 返回。若最终状态更新影响零行，则说明本次结果已经过期，仅记录日志并丢弃结果。</p>
     *
     * @param mistakeId 待分析错题的唯一标识，不能为空
     * @throws NullPointerException 当 {@code mistakeId} 为空时
     * @throws RuntimeException 临时依赖故障或其他非永久异常，任务退回队列后继续抛出以触发重试
     */
    @Override
    public void analyze(UUID mistakeId) {
        Objects.requireNonNull(mistakeId, "mistakeId 不能为空");

        int claimed = mistakeMapper.claimAnalysis(mistakeId);
        if (claimed == 0) {
            return;
        }

        PersonalQuestionEntity question = mistakeMapper.selectByIdForAnalysis(mistakeId);
        if (question == null || question.getVersion() == null) {
            // 领取成功后记录理论上必须存在；无法取得版本号时保留 ANALYZING，交给租约恢复器兜底。
            throw new IllegalStateException("领取后的错题记录不存在或版本号为空");
        }

        int expectedVersion = question.getVersion();
        try {
            byte[] imageContent = loadImage(question.getImageObjectKey());
            MistakeAnalysisVO analysis = requireValidAnalysis(
                    questionAnalysisClient.analyze(question, imageContent)
            );

            int updated = mistakeMapper.markAnalysisCompleted(
                    mistakeId, expectedVersion, analysis
            );
            if (updated == 0) {
                log.info("Discarded stale mistake analysis result id={} version={}",
                        mistakeId, expectedVersion);
            }
        } catch (AnalysisPermanentException exception) {
            // 永久失败已经无法通过相同消息重试修复，因此直接落库并正常结束消费。
            int updated = mistakeMapper.markAnalysisFailed(
                    mistakeId,
                    expectedVersion,
                    safeFailureMessage(exception)
            );
            if (updated == 0) {
                log.info("Discarded stale permanent analysis failure id={} version={}",
                        mistakeId, expectedVersion);
            }
        } catch (RuntimeException exception) {
            // 临时故障先退回 QUEUED；继续抛出异常后，消息监听器才能重新投递并再次领取。
            int released = mistakeMapper.releaseAnalysisForRetry(mistakeId, expectedVersion);
            if (released == 0) {
                log.info("Analysis retry release skipped for stale task id={} version={}",
                        mistakeId, expectedVersion);
            }
            throw exception;
        }
    }

    /**
     * 在消息重试全部耗尽后终结仍处于排队状态的任务。
     *
     * <p>该方法由死信消费者调用，并不会再次发起分析。更新条件只接受 {@code QUEUED}
     * 状态，因此已完成、已失败、正在由其他 Worker 处理或不存在的任务都不会被覆盖。
     * 失败信息会先去除首尾空白、补充默认值并截断到数据库允许的长度。</p>
     *
     * @param mistakeId 需要终结的错题唯一标识，不能为空
     * @param failureMessage 可展示给用户的失败原因；为空时使用安全的默认文案
     * @throws NullPointerException 当 {@code mistakeId} 为空时
     */
    @Override
    public void failAfterRetries(UUID mistakeId, String failureMessage) {
        Objects.requireNonNull(mistakeId, "mistakeId 不能为空");
        String safeMessage = safeFailureMessage(failureMessage);
        int updated = mistakeMapper.markQueuedAnalysisFailed(mistakeId, safeMessage);
        if (updated == 0) {
            log.info("Dead-letter state update skipped for terminal or missing task id={}",
                    mistakeId);
        }
    }

    /**
     * 加载错题原图。
     *
     * @param imageObjectKey 私有对象存储键；为空表示纯文字题
     * @return 图片字节；纯文字题返回 {@code null}
     * @throws RuntimeException 对象存储读取失败时由存储适配器抛出
     */
    private byte[] loadImage(String imageObjectKey) {
        return imageObjectKey == null || imageObjectKey.isBlank()
                ? null
                : imageStorageService.get(imageObjectKey);
    }

    /**
     * 对分析客户端返回的核心字段做第二层业务校验。
     *
     * <p>即使具体客户端已经执行 Schema 校验，编排层仍坚持自己的最小结果契约，防止未来
     * 更换客户端实现后把空结果写成成功状态。</p>
     *
     * @param analysis 分析客户端返回的结构化结果
     * @return 原分析对象
     * @throws AnalysisPermanentException 当对象为空、列表为空或任何必需文本为空白时
     */
    private MistakeAnalysisVO requireValidAnalysis(MistakeAnalysisVO analysis) {
        if (analysis == null
                || isBlank(analysis.summary())
                || hasBlank(analysis.knowledge())
                || hasBlank(analysis.steps())
                || isBlank(analysis.suggestion())
                || isBlank(analysis.answer())) {
            throw new AnalysisPermanentException("分析服务返回了不完整的结果");
        }
        return analysis;
    }

    /**
     * 从永久异常中提取适合持久化的失败信息。
     *
     * @param exception 已判定为不可重试的分析异常
     * @return 非空且长度受限的失败文案
     */
    private String safeFailureMessage(AnalysisPermanentException exception) {
        return safeFailureMessage(exception.getMessage());
    }

    /**
     * 将任意失败文案归一化为可安全写入数据库的值。
     *
     * @param message 原始失败文案，可以为空
     * @return 去除首尾空白、具有默认值且不超过长度上限的文案
     */
    private String safeFailureMessage(String message) {
        String safeMessage = isBlank(message) ? "题目无法完成分析" : message.strip();
        return safeMessage.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? safeMessage
                : safeMessage.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    /** 判断字符串是否为 {@code null}、空串或只包含空白字符。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 判断字符串列表是否为空，或其中是否存在空白元素。 */
    private boolean hasBlank(java.util.List<String> values) {
        return values == null || values.isEmpty() || values.stream().anyMatch(this::isBlank);
    }
}
