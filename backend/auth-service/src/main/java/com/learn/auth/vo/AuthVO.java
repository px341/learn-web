package com.learn.auth.vo;

/**
 * 登录或注册成功响应，包含短期 Access Token 和允许前端展示的用户字段。
 */
public record AuthVO(
        String token,
        UserVO user
) {
}
