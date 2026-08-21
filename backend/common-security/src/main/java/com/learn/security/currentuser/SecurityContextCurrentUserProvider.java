package com.learn.security.currentuser;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * 从当前线程的 Spring SecurityContext 中读取 JWT subject。
 *
 * <p>仅用于基于 Spring MVC 的服务，不得在 WebFlux Gateway 中使用。</p>
 */
public final class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return AuthenticationUserIdResolver.resolve(authentication);
    }
}
