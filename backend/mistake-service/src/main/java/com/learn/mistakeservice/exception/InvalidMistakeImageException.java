package com.learn.mistakeservice.exception;

/** 图片真实格式不受支持、内容损坏或无法读取。 */
public class InvalidMistakeImageException extends RuntimeException {
    public InvalidMistakeImageException(String message) {
        super(message);
    }

    public InvalidMistakeImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
