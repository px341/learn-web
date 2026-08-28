package com.learn.mistakeservice.vo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardStatsVOTests {

    @Test
    void convertsNullCollectionsToEmptyCollections() {
        DashboardStatsVO result = new DashboardStatsVO(0, 0, 0, null);

        assertThat(result.questionTypeCounts()).isEmpty();
    }

    @Test
    void makesDefensiveCopiesOfCollections() {
        List<QuestionTypeCountVO> typeCounts = new ArrayList<>(
                List.of(new QuestionTypeCountVO("选择题", 2))
        );

        DashboardStatsVO result = new DashboardStatsVO(2, 1, 10, typeCounts);
        typeCounts.clear();

        assertThat(result.questionTypeCounts()).hasSize(1);
        assertThatThrownBy(() -> result.questionTypeCounts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
