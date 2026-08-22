package com.learn.security.currentuser;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 为 WebFlux 请求提供 Reactor Context 中的当前登录用户。
 *
 * <p>返回值保持惰性，必须在包含 Spring Security Reactive Context 的响应式链中订阅。</p>
 */
public interface ReactiveCurrentUserProvider {

    /**
     * 获取当前已认证用户的 UUID。
     *
     * @return 发出 JWT subject 对应 UUID 的 Mono；认证缺失或 subject 非法时发出
     *         {@link org.springframework.security.authentication.AuthenticationCredentialsNotFoundException}
     */
    Mono<UUID> getUserId();
}
