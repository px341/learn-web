package com.learn.mistakeservice.vo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardStatsVOTests {

    @Test
    void convertsNullCollectionsToEmptyCollections() {
        DashboardStatsVO result = new DashboardStatsVO(0, 0, 0, 0, 0, 0, null, null);

        assertThat(result.typeCounts()).isEmpty();
        assertThat(result.accuracyTrend()).isEmpty();
    }

    @Test
    void makesDefensiveCopiesOfCollections() {
        List<MistakeTypeCountVO> typeCounts = new ArrayList<>(
                List.of(new MistakeTypeCountVO("概念不清", 2))
        );
        List<AccuracyTrendPointVO> trend = new ArrayList<>(
                List.of(new AccuracyTrendPointVO(LocalDate.of(2026, 8, 24), 72))
        );

        DashboardStatsVO result = new DashboardStatsVO(2, 1, 72, 1, 10, 8, typeCounts, trend);
        typeCounts.clear();
        trend.clear();

        assertThat(result.typeCounts()).hasSize(1);
        assertThat(result.accuracyTrend()).hasSize(1);
        assertThatThrownBy(() -> result.typeCounts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
