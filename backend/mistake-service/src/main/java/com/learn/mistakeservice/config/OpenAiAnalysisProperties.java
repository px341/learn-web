package com.learn.mistakeservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * OpenAI Responses API 的外部化配置。
 *
 * <p>属性绑定前缀为 {@code analysis.openai}。除 API Key 外的字段在应用启动时通过 Bean
 * Validation 校验；API Key 允许为空，使应用能在未配置模型凭据的开发环境启动，但真正执行
 * 分析时会被客户端转换为不可重试的配置失败。生产环境应通过环境变量或 Secret 注入 Key，
 * 不应写入仓库。</p>
 *
 * @param baseUrl Responses API 基础地址，通常以 {@code /v1} 结尾
 * @param apiKey Bearer API Key；空值仅允许应用启动，不允许实际分析
 * @param model 每次分析请求使用的模型 ID
 * @param connectTimeout 建立 HTTP 连接的最长等待时间
 * @param readTimeout 等待 Responses API 返回数据的最长时间
 * @param maxOutputTokens 单次响应允许生成的最大 token 数，至少为 1
 */
@Validated
@ConfigurationProperties(prefix = "analysis.openai")
public record OpenAiAnalysisProperties(
        @NotNull URI baseUrl,
        String apiKey,
        @NotBlank String model,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) int maxOutputTokens
) {
}
