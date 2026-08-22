package com.learn.security.currentuser;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 从 Reactor Context 中读取 JWT subject，适用于 WebFlux Gateway。
 *
 * <p>不得通过 {@code block()} 在请求链外读取身份；订阅发生在丢失 Reactor Context
 * 的线程或链路中时，会得到未认证错误。</p>
 */
public final class ReactiveSecurityContextCurrentUserProvider implements ReactiveCurrentUserProvider {

    /**
     * 从当前订阅者的 Reactive SecurityContext 中解析用户 UUID。
     *
     * @return 发出当前用户 UUID 的 Mono；没有认证上下文时发出认证缺失错误
     */
    @Override
    public Mono<UUID> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(AuthenticationUserIdResolver::resolve)
                .switchIfEmpty(Mono.error(new AuthenticationCredentialsNotFoundException(
                        "Current request is not authenticated"
                )));
    }
}
