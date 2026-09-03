package com.learn.mistakeservice.exception;

/** 只有分析完成的错题才能修改掌握状态。 */
public class AnalysisNotCompletedException extends RuntimeException {

    public AnalysisNotCompletedException() {
        super("错题分析尚未完成");
    }
}
