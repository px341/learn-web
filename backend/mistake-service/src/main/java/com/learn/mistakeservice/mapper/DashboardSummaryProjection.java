package com.learn.mistakeservice.mapper;

import lombok.Data;

/** {@code personal_questions} 聚合查询的内部投影。 */
@Data
public class DashboardSummaryProjection {

    private long total;
    private long weeklyNew;
    private long previousTotal;
}
