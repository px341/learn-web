package com.learn.mistakeservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分析成功后的结构化结果。
 *
 * <p>该对象既作为后续分析客户端与业务层之间的结果契约，也作为错题详情接口
 * 返回给前端的只读视图，不包含模型原始响应。</p>
 */
public record MistakeAnalysisVO(
        String summary,
        List<String> knowledge,
        List<String> steps,
        String suggestion,
        String answer,
        @Schema(minimum = "0", maximum = "100") int confidence
) {
    public MistakeAnalysisVO {
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be between 0 and 100");
        }
    }
}
