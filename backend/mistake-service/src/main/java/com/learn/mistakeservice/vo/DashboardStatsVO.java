package com.learn.mistakeservice.vo;

import java.util.List;

/**
 * Dashboard 总览响应。
 *
 * <p>这是 {@code personal_questions} 的聚合只读视图，不对应单独的数据表。无数据时数值字段
 * 返回 0，集合返回空数组，不返回 {@code null}。</p>
 *
 * @param total                 累计错题数
 * @param weeklyNew             近 7 天新增错题数
 * @param totalChangePercent    近 7 天新增相对先前累计数的变化百分比
 * @param questionTypeCounts    题型分布
 */
public record DashboardStatsVO(
        long total,
        long weeklyNew,
        int totalChangePercent,
        List<QuestionTypeCountVO> questionTypeCounts
) {

    /** 保证响应数组非 null，并避免调用方在构造后修改响应内容。 */
    public DashboardStatsVO {
        questionTypeCounts = questionTypeCounts == null
                ? List.of()
                : List.copyOf(questionTypeCounts);
    }
}
