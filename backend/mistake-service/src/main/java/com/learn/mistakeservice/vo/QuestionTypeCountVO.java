package com.learn.mistakeservice.vo;

/**
 * Dashboard 题型分布中的一个分组。
 *
 * @param questionType 题型；未录入时返回“未分类”
 * @param count        当前用户该题型下的错题数量
 */
public record QuestionTypeCountVO(
        String questionType,
        long count
) {
}
