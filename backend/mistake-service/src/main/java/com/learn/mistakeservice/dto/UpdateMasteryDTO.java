package com.learn.mistakeservice.dto;

import jakarta.validation.constraints.NotNull;

/** 标记或取消标记错题已掌握状态的请求。 */
public record UpdateMasteryDTO(
        @NotNull Boolean mastered
) {
}
