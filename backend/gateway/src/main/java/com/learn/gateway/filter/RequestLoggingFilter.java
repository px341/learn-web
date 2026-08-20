package com.learn.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Gateway 全局请求日志过滤器。
 *
 * <p>记录请求方法、路径、响应状态和耗时，便于排查路由及性能问题。
 * 出于安全考虑，不记录请求体、查询参数和 Authorization 请求头。</p>
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * 在请求进入和请求结束时分别写入一条日志。
     *
     * <p>{@code doFinally} 在请求成功、失败或取消时都会执行，确保异常请求也能留下
     * 完成日志。日志格式中的 traceId 和 spanId 由 Micrometer 自动补充。</p>
     *
     * @param exchange 当前请求与响应上下文
     * @param chain Gateway 过滤器链
     * @return 表示异步处理完成的响应式结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 使用单调时钟计算耗时，避免系统时间调整影响结果。
        long startedAt = System.nanoTime();
        String requestId = exchange.getRequest().getId();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        log.info("Gateway request started: requestId={}, method={}, path={}", requestId, method, path);

        return chain.filter(exchange).doFinally(signalType -> {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
            // WebFlux 对正常响应可能不显式保存 200 状态码，因此空值按 200 记录。
            int status = statusCode == null ? HttpStatus.OK.value() : statusCode.value();
            log.info(
                    "Gateway request completed: requestId={}, method={}, path={}, status={}, elapsedMs={}",
                    requestId,
                    method,
                    path,
                    status,
                    elapsedMs
            );
        });
    }

    /**
     * 让日志过滤器尽早执行，从而覆盖后续鉴权、限流和路由处理的完整耗时。
     *
     * @return 过滤器执行顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
