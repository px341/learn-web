package com.learn.auth.exception;

/**
 * JWT 对应的用户已不存在或已不可用，当前登录会话不能继续使用。
 */
public class CurrentUserUnavailableException extends RuntimeException {

    public CurrentUserUnavailableException() {
        super("当前登录用户不存在或已不可用");
    }
}
