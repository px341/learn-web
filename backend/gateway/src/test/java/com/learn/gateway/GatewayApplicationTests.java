package com.learn.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway 基础集成测试。
 *
 * <p>测试时关闭 Nacos 服务发现，避免测试依赖本地基础设施。</p>
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    /** 验证 Gateway 的 Spring 容器能够正常创建。 */
    @Test
    void contextLoads() {
    }

    /** 验证未携带 JWT 的请求无法访问受保护接口。 */
    @Test
    void protectedRequestWithoutJwtIsUnauthorized() {
        webTestClient.get()
                .uri("/api/private")
                .exchange()
                .expectStatus().isUnauthorized();
    }

}
