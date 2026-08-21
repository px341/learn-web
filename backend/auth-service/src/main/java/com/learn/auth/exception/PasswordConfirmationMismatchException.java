package com.learn.auth.exception;

/**
 * 注册请求中的两次密码输入不一致。
 */
public class PasswordConfirmationMismatchException extends RuntimeException {

    public PasswordConfirmationMismatchException() {
        super("两次输入的密码不一致");
    }
}
