package com.learn.mistakeservice.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * RabbitMQ 错题分析请求消息。
 *
 * <p>消息只携带错题 ID，不包含题目正文、用户答案、图片字节或对象存储地址。消费者收到消息
 * 后必须从数据库重新读取可信的最新题目快照，这既减小消息体，也避免敏感内容扩散到消息
 * Broker，并让重复消息统一走数据库状态的幂等判断。</p>
 *
 * @param mistakeId 待分析错题的唯一标识，不能为空
 */
public record MistakeAnalysisMessage(UUID mistakeId) {

    /**
     * 校验消息契约，阻止生产者创建缺少业务标识的消息。
     *
     * @throws NullPointerException 当 {@code mistakeId} 为空时
     */
    public MistakeAnalysisMessage {
        Objects.requireNonNull(mistakeId, "mistakeId must not be null");
    }
}
