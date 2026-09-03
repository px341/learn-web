package com.learn.mistakeservice.exception;

/**
 * 可能随时间恢复、允许消息监听器重试的分析失败。
 *
 * <p>典型原因包括连接或读取超时、HTTP 408/409/429、上游 5xx 以及暂时未完成的响应。
 * 业务编排层遇到该异常时会先把任务退回 {@code QUEUED}，再继续抛给监听容器执行带退避的
 * 重试；达到最大次数后，消息会进入失败队列。</p>
 */
public class AnalysisTransientException extends RuntimeException {

    /**
     * 使用安全摘要创建临时异常。
     *
     * @param message 不包含上游敏感响应的失败摘要
     */
    public AnalysisTransientException(String message) {
        super(message);
    }

    /**
     * 创建临时异常并保留底层原因，供重试判断、日志和诊断使用。
     *
     * @param message 安全失败摘要
     * @param cause 原始网络、HTTP 或客户端异常
     */
    public AnalysisTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
