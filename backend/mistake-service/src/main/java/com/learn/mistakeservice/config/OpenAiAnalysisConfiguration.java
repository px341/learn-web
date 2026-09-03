package com.learn.mistakeservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Responses API 的 HTTP 客户端配置。
 *
 * <p>配置集中创建一个具名 {@link RestClient}，统一应用 API 基础地址、连接超时和读取超时。
 * API Key 不在这里设置为全局默认 header，而由请求客户端逐次添加 Bearer 凭据，避免将敏感
 * 配置混入其他 RestClient 实例。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiAnalysisProperties.class)
public class OpenAiAnalysisConfiguration {

    /**
     * 创建错题分析专用的同步 HTTP 客户端。
     *
     * <p>基础地址末尾的斜杠会被移除，调用方可以稳定地使用 {@code /responses} 相对路径。
     * 连接超时限制建立 TCP/TLS 连接的等待时间，读取超时限制请求建立后等待模型响应的时间。</p>
     *
     * @param builder Spring Boot 管理的 RestClient 构建器
     * @param properties 已完成绑定和校验的 OpenAI 分析配置
     * @return Bean 名为 {@code openAiAnalysisRestClient} 的分析专用客户端
     */
    @Bean
    public RestClient openAiAnalysisRestClient(
            RestClient.Builder builder,
            OpenAiAnalysisProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl().toString().replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }
}
