package com.learn.mistakeservice.mapper;

import com.learn.mistakeservice.entity.MistakeOutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MistakeOutboxMapper {
    int insertAnalysisRequested(
            @Param("id") UUID id,
            @Param("mistakeId") UUID mistakeId,
            @Param("createdAt") Instant createdAt
    );

    List<MistakeOutboxEventEntity> selectPendingForUpdate(@Param("limit") int limit);

    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    int markRetry(@Param("id") UUID id, @Param("lastError") String lastError);
}
