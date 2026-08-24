package com.learn.mistakeservice.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DashboardStatsQueryDTOTests {

    @Test
    void usesSevenDaysByDefault() {
        assertThat(new DashboardStatsQueryDTO().days()).isEqualTo(7);
        assertThat(new DashboardStatsQueryDTO(null).days()).isEqualTo(7);
    }

    @Test
    void acceptsSupportedRanges() {
        assertThat(new DashboardStatsQueryDTO(7).days()).isEqualTo(7);
        assertThat(new DashboardStatsQueryDTO(14).days()).isEqualTo(14);
        assertThat(new DashboardStatsQueryDTO(30).days()).isEqualTo(30);
    }

    @Test
    void rejectsUnsupportedRanges() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DashboardStatsQueryDTO(8))
                .withMessage("统计天数只支持 7、14 或 30");
    }
}
