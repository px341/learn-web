package com.learn.gateway.config;

import com.learn.security.currentuser.ReactiveCurrentUserProvider;
import com.learn.security.currentuser.ReactiveSecurityContextCurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Gateway 统一认证配置。
 *
 * <p>登录、注册及基础健康检查可以匿名访问，其他请求必须携带合法 JWT。
 * Gateway 只负责验证令牌，不负责查询用户或执行登录、注册业务。</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    /** HS256 算法要求密钥至少为 256 bit，即 32 字节。 */
    private static final int MINIMUM_HS256_SECRET_BYTES = 32;

    /**
     * 提供适用于 WebFlux/Reactor Context 的当前用户读取器。
     */
    @Bean
    public ReactiveCurrentUserProvider reactiveCurrentUserProvider() {
        return new ReactiveSecurityContextCurrentUserProvider();
    }

    /**
     * 配置响应式安全过滤链。
     *
     * <p>Gateway 面向无状态 REST API，因此关闭表单登录、HTTP Basic 和 CSRF；
     * JWT 校验由 OAuth2 Resource Server 过滤器完成。</p>
     *
     * @param http WebFlux 安全配置对象
     * @return Gateway 使用的安全过滤链
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // 放行浏览器发起的跨域预检请求。
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 用户尚未持有 JWT 时也必须能够登录和注册。
                        .pathMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        // 健康检查供 Docker、Nginx 或运维平台探测服务状态。
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Swagger UI 及聚合后的 OpenAPI 文档仅在启用时公开。
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                "/v3/api-docs/**").permitAll()
                        // 未明确公开的接口默认都需要通过 JWT 校验。
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * 创建使用共享密钥的 JWT 解码器。
     *
     * <p>Auth Service 签发令牌与 Gateway 校验令牌时必须使用同一个密钥和
     * HS256 算法。默认校验器会检查令牌时间信息，例如 {@code exp} 是否过期。</p>
     *
     * @param secret 从配置或 {@code JWT_SECRET} 环境变量读取的共享密钥
     * @return 响应式 JWT 解码器
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // 在应用启动阶段尽早拒绝不符合 HS256 安全要求的短密钥。
        if (secretBytes.length < MINIMUM_HS256_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }

        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 使用 Spring Security 默认校验规则检查过期时间等标准声明。
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}
