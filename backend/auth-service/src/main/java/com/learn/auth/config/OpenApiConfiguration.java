package com.learn.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Auth Service 的 OpenAPI 文档配置。
 *
 * <p>Swagger UI 的 Authorize 按钮使用 bearerAuth；输入登录接口返回的 JWT 后，
 * 页面会在后续受保护请求中自动添加 Authorization: Bearer 请求头。</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "错题实验室 Auth API",
                version = "v1",
                description = "用户注册、登录和 JWT 认证接口"
        ),
        // 使用相对地址，使聚合 Swagger 始终通过当前域名的 Nginx 和 Gateway 调用 API。
        servers = @Server(url = "/", description = "当前 Swagger 入口")
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
