package com.learn.mistakeservice.messaging;

import com.learn.mistakeservice.config.MistakeMessagingConfiguration;
import com.learn.mistakeservice.dto.MistakeAnalysisMessage;
import com.learn.mistakeservice.service.MistakeAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 主分析队列的消息消费者。
 *
 * <p>消息只包含错题 ID，题目正文和图片对象键始终从服务端数据库重新读取，避免把用户内容
 * 复制到消息系统，也避免消息中的旧数据覆盖数据库中的可信状态。具体的任务领取、幂等控制、
 * 模型调用和结果落库由 {@link MistakeAnalysisService} 完成。</p>
 *
 * <p>该监听器使用专用容器工厂。业务层识别出的永久失败会自行写入 {@code FAILED} 并正常
 * 返回；临时异常会继续传播给容器，由重试拦截器按退避策略重试，耗尽后转发到失败队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MistakeAnalysisConsumer {

    /** 错题分析的业务编排入口。 */
    private final MistakeAnalysisService mistakeAnalysisService;

    /**
     * 消费一条分析请求。
     *
     * <p>显式校验消息及 ID，可以让格式损坏的消息立即失败并进入统一的重试/失败处理链路。
     * 对重复或已终结任务的处理由业务层通过状态条件保证幂等。</p>
     *
     * @param message 反序列化后的分析请求，必须包含非空错题 ID
     * @throws NullPointerException 消息或错题 ID 为空时
     * @throws RuntimeException 分析发生临时故障时继续抛出，由监听容器执行重试
     */
    @RabbitListener(
            queues = MistakeMessagingConfiguration.ANALYSIS_QUEUE,
            containerFactory = "mistakeAnalysisRabbitListenerContainerFactory"
    )
    public void consume(MistakeAnalysisMessage message) {
        Objects.requireNonNull(message, "分析消息不能为空");
        Objects.requireNonNull(message.mistakeId(), "分析消息缺少 mistakeId");
        mistakeAnalysisService.analyze(message.mistakeId());
        log.info("Mistake analysis message processed id={}", message.mistakeId());
    }
}
