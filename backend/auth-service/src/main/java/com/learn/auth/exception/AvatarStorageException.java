package com.learn.auth.exception;

/** Garage 上传、删除或签名访问地址失败。 */
public class AvatarStorageException extends RuntimeException {

    public AvatarStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
