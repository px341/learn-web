package com.learn.mistakeservice.exception;

/** 当前用户没有可用分析额度。 */
public class InsufficientCreditsException extends RuntimeException {
    public InsufficientCreditsException() {
        super("分析额度不足");
    }
}
