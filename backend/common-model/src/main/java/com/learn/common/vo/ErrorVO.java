package com.learn.common.vo;

import lombok.Getter;

import java.util.Map;

/**
 * 统一错误响应。
 */
@Getter
public class ErrorVO {

    private final String code;
    private final String message;
    private final Map<String, String> fieldErrors;

    public ErrorVO(String code, String message, Map<String, String> fieldErrors) {
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors == null
                ? Map.of()
                : Map.copyOf(fieldErrors);
    }

    public static ErrorVO of(String code, String message) {
        return new ErrorVO(code, message, Map.of());
    }
}