package com.learn.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Gateway 基础设施配置，集中声明限流等网关组件需要使用的 Bean。
 */
@Configuration
public class GatewayInfrastructureConfiguration {

    /**
     * 创建基于客户端 IP 的限流键解析器。
     *
     * <p>请求正常经过 Nginx 时优先读取由 Nginx 覆盖写入的 {@code X-Real-IP}；
     * 直接访问 Gateway 时则使用 TCP 连接中的远端地址。解析不到地址时返回固定值，
     * 避免因为空限流键导致请求绕过限流。</p>
     *
     * @return 供 RequestRateLimiter 使用的客户端 IP 解析器
     */
    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            // 线上请求由受信任的 Nginx 统一设置该请求头。
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return Mono.just(realIp);
            }

            // 本地直接请求 Gateway 时，从连接信息中获取客户端地址。
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                return Mono.just("unknown");
            }
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}
