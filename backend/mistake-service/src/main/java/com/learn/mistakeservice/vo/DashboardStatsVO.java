package com.learn.mistakeservice.vo;

import java.util.List;

/**
 * Dashboard 总览响应。
 *
 * <p>这是多个错题及复习记录聚合后的只读视图，不对应单独的数据表。无数据时数值字段
 * 返回 0，集合返回空数组，不返回 {@code null}。</p>
 *
 * @param total                 累计错题数
 * @param weeklyNew             本周新增错题数
 * @param averageAccuracy       平均正确率，取值 0～100
 * @param pendingReview         待复习错题数
 * @param totalChangePercent    累计错题环比变化百分比
 * @param accuracyChangePercent 正确率环比变化百分比
 * @param typeCounts            错因分布
 * @param accuracyTrend         按日期升序排列的正确率趋势
 */
public record DashboardStatsVO(
        long total,
        long weeklyNew,
        int averageAccuracy,
        long pendingReview,
        int totalChangePercent,
        int accuracyChangePercent,
        List<MistakeTypeCountVO> typeCounts,
        List<AccuracyTrendPointVO> accuracyTrend
) {

    /** 保证响应数组非 null，并避免调用方在构造后修改响应内容。 */
    public DashboardStatsVO {
        typeCounts = typeCounts == null ? List.of() : List.copyOf(typeCounts);
        accuracyTrend = accuracyTrend == null ? List.of() : List.copyOf(accuracyTrend);
    }
}
