package com.learn.auth.exception;

/** 上传头像为空、超限或文件签名不受支持。 */
public class InvalidAvatarException extends RuntimeException {

    public InvalidAvatarException(String message) {
        super(message);
    }
}
