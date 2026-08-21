package com.learn.security.currentuser;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 从 Reactor Context 中读取 JWT subject，适用于 WebFlux Gateway。
 */
public final class ReactiveSecurityContextCurrentUserProvider implements ReactiveCurrentUserProvider {

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
