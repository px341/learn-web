package com.learn.mistakeservice.vo;

import java.time.LocalDate;

/**
 * 正确率趋势中的一个自然日数据点。
 *
 * @param date     用户时区下的统计日期
 * @param accuracy 当天平均正确率，取值 0～100
 */
public record AccuracyTrendPointVO(
        LocalDate date,
        int accuracy
) {
}
