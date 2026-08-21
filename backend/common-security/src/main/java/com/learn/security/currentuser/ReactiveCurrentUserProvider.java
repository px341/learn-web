package com.learn.security.currentuser;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 为 WebFlux 请求提供 Reactor Context 中的当前登录用户。
 */
public interface ReactiveCurrentUserProvider {

    Mono<UUID> getUserId();
}
