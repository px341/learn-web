package com.learn.mistakeservice.exception;

/** 创建错题时文字和图片均为空。 */
public class MistakeContentRequiredException extends RuntimeException {
    public MistakeContentRequiredException() {
        super("题目文字和图片至少提供一个");
    }
}
