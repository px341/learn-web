package com.learn.mistakeservice.exception;

/** 错题不存在、已归档或不属于当前用户时统一抛出。 */
public class MistakeNotFoundException extends RuntimeException {
    public MistakeNotFoundException() {
        super("错题不存在");
    }
}
