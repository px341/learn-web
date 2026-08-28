package com.learn.mistakeservice.vo;

/** 创建错题后的异步受理响应。 */
public record CreateMistakeVO(
        MistakeSummaryVO mistake,
        int creditsRemaining
) {
}
