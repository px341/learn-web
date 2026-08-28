package com.learn.mistakeservice.exception;

/** 错题图片超过 10MB 限制。 */
public class MistakeImageTooLargeException extends RuntimeException {
    public MistakeImageTooLargeException() {
        super("错题图片不能超过 10MB");
    }
}
