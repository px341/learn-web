package com.learn.mistakeservice.exception;

/**
 * 不应使用相同输入继续重试的分析失败。
 *
 * <p>典型原因包括未配置凭据、不支持的图片类型、请求参数错误、模型拒绝内容以及结构化输出
 * 不符合业务契约。业务编排层捕获该异常后会将任务直接标记为 {@code FAILED}，并正常结束
 * 消息消费，避免无意义地重复调用外部服务。</p>
 */
public class AnalysisPermanentException extends RuntimeException {

    /**
     * 使用可安全持久化和展示的失败文案创建异常。
     *
     * @param safeMessage 不包含凭据、上游响应正文等敏感信息的失败文案
     */
    public AnalysisPermanentException(String safeMessage) {
        super(safeMessage);
    }

    /**
     * 创建永久失败并保留底层原因，供服务端日志和诊断使用。
     *
     * @param safeMessage 可安全持久化和展示的失败文案
     * @param cause 原始解析、校验或 HTTP 客户端异常
     */
    public AnalysisPermanentException(String safeMessage, Throwable cause) {
        super(safeMessage, cause);
    }
}
