package com.learn.common.vo;

import lombok.Getter;

import java.util.Map;

/**
 * 跨服务统一错误响应。
 *
 * <p>{@code code} 供客户端进行稳定的分支判断，{@code message} 用于展示；
 * 参数校验失败时，可通过 {@code fieldErrors} 返回字段级提示。</p>
 */
@Getter
public class ErrorVO {

    /** 稳定的机器可读错误码，例如 {@code VALIDATION_ERROR}。 */
    private final String code;

    /** 面向客户端或用户的错误说明。 */
    private final String message;

    /** 字段名到校验提示的只读映射；无字段错误时为空集合。 */
    private final Map<String, String> fieldErrors;

    /**
     * 创建完整错误响应，并对字段错误映射执行防御性复制。
     *
     * @param code 稳定的机器可读错误码
     * @param message 错误说明
     * @param fieldErrors 字段级错误；传入 {@code null} 时转换为空映射
     */
    public ErrorVO(String code, String message, Map<String, String> fieldErrors) {
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors == null
                ? Map.of()
                : Map.copyOf(fieldErrors);
    }

    /**
     * 创建不包含字段级错误的响应。
     *
     * @param code 稳定的机器可读错误码
     * @param message 错误说明
     * @return 字段错误为空的错误响应
     */
    public static ErrorVO of(String code, String message) {
        return new ErrorVO(code, message, Map.of());
    }
}
