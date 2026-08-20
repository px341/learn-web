package com.learn.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动类。
 *
 * <p>网关是后端服务的统一入口，负责接收 Nginx 转发的请求，
 * 再根据路由配置将请求转发到对应的微服务。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 启动 Gateway 服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
