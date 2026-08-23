package com.learn.auth.exception;

/**
 * 用户资料修改请求没有提供可更新字段，或字段内容不符合资料更新规则。
 */
public class InvalidProfileUpdateException extends RuntimeException {

    public InvalidProfileUpdateException(String message) {
        super(message);
    }
}
