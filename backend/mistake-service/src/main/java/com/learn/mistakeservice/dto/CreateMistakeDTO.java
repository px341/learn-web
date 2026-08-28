package com.learn.mistakeservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建错题时允许客户端提交的文字字段。
 *
 * <p>图片由 Controller 作为独立的 multipart 文件接收；图片与 {@code text}
 * 至少存在一个的条件由 Service 在读取文件后统一校验。</p>
 */
public record CreateMistakeDTO(
        @Size(max = 100) String title,
        @NotBlank @Size(max = 30) String subject,
        @Size(max = 100) String chapter,
        @NotBlank @Size(max = 30) String type,
        @Size(max = 10_000) String text,
        @Size(max = 10_000) String userAnswer
) {
}
