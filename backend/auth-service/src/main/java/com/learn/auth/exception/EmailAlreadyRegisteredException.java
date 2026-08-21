package com.learn.auth.exception;

/**
 * 注册邮箱已被占用；Controller Advice 会将其转换为 HTTP 409。
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("该邮箱已注册");
    }
}
