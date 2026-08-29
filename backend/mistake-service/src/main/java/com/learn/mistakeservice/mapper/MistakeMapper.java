package com.learn.mistakeservice.mapper;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface MistakeMapper {

    /**
     * 同时限定主键和用户 ID；其他用户的记录与不存在记录都返回 null。
     */
    PersonalQuestionEntity selectActiveByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    int insert(PersonalQuestionEntity mistake);

    Integer selectActiveCreditsForUpdate(@Param("userId") UUID userId);

    int decrementCredit(@Param("userId") UUID userId);

    int updateMasteredByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );
}
