package com.learn.mistakeservice.exception;

/** Garage 预签名操作失败。 */
public class MistakeStorageException extends RuntimeException {
    public MistakeStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public MistakeStorageException(String message) {
        super(message);
    }
}
