package com.learn.mistakeservice.service.imple;

import com.learn.mistakeservice.mapper.DashboardMapper;
import com.learn.mistakeservice.mapper.DashboardSummaryProjection;
import com.learn.mistakeservice.service.DashboardService;
import com.learn.mistakeservice.vo.DashboardStatsVO;
import com.learn.mistakeservice.vo.QuestionTypeCountVO;
import com.learn.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public DashboardStatsVO getDashboard() {
        UUID userId = currentUserProvider.getUserId();
        Instant currentPeriodStart = Instant.now().minus(7, ChronoUnit.DAYS);

        DashboardSummaryProjection summary = dashboardMapper
                .selectSummary(userId, currentPeriodStart);
        List<QuestionTypeCountVO> questionTypeCounts = dashboardMapper
                .selectQuestionTypeCounts(userId);

        return new DashboardStatsVO(
                summary.getTotal(),
                summary.getWeeklyNew(),
                calculateChangePercent(summary.getTotal(), summary.getPreviousTotal()),
                questionTypeCounts
        );
    }

    private int calculateChangePercent(long currentTotal, long previousTotal) {
        if (previousTotal == 0) {
            return currentTotal == 0 ? 0 : 100;
        }
        return (int) Math.round((currentTotal - previousTotal) * 100.0 / previousTotal);
    }
}
