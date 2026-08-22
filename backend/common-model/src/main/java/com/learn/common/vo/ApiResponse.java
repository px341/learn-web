package com.learn.common.vo;

import lombok.Data;

/**
 * 统一成功响应包装；业务错误使用 ErrorVO 并配合正确的 HTTP 状态码。
 *
 * @param <T> 响应数据的类型
 */
@Data
public class ApiResponse<T> {

    /** 实际业务数据；没有返回内容的成功请求允许为 {@code null}。 */
    private T data;

    /** 供 JSON 反序列化框架使用的无参构造方法。 */
    public ApiResponse() {
    }

    /**
     * 创建包含业务数据的成功响应。
     *
     * @param data 返回给客户端的业务数据
     */
    public ApiResponse(T data) {
        this.data = data;
    }

    /**
     * 以统一格式包装成功结果。
     *
     * @param data 返回给客户端的业务数据
     * @param <T> 业务数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data);
    }
}
