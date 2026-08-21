package com.learn.common.vo;

import lombok.Data;

/**
 * 统一成功响应包装；业务错误使用 ErrorVO 并配合正确的 HTTP 状态码。
 */
@Data
public class ApiResponse<T> {

    private T data;

    public ApiResponse() {
    }

    public ApiResponse(T data) {
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data);
    }
}
