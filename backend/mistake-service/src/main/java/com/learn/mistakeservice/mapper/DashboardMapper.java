package com.learn.mistakeservice.mapper;

import com.learn.mistakeservice.vo.QuestionTypeCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DashboardMapper {
    DashboardSummaryProjection selectSummary(
            @Param("userId") UUID userId,
            @Param("currentPeriodStart") Instant currentPeriodStart);

    List<QuestionTypeCountVO> selectQuestionTypeCounts(@Param("userId") UUID userId);
}
