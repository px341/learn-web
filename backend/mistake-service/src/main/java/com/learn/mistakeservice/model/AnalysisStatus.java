package com.learn.mistakeservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** 错题分析任务状态；数据库使用大写枚举名，JSON 使用小写值。 */
public enum AnalysisStatus {
    QUEUED,
    ANALYZING,
    COMPLETED,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AnalysisStatus fromValue(String value) {
        return AnalysisStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
