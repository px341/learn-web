package com.learn.mistakeservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Mistake Service OpenAPI 配置。
 *
 * <p>认证方案名与 Auth Service 保持一致，让聚合 Swagger UI 在切换服务时
 * 可以继续使用同一个 JWT。</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "错题实验室 Mistake API",
                version = "v1",
                description = "错题与 Dashboard 统计接口"
        ),
        servers = @Server(url = "/", description = "当前 Swagger 入口"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "输入登录接口返回的 JWT，无需填写 Bearer 前缀"
)
public class OpenApiConfiguration {
}
