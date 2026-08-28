package com.learn.mistakeservice.exception;

/** JWT 对应用户不存在或账号已不可用。 */
public class MistakeUserUnavailableException extends RuntimeException {
    public MistakeUserUnavailableException() {
        super("当前用户不可用");
    }
}
