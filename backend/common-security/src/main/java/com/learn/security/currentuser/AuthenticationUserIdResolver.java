package com.learn.security.currentuser;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * 将已通过 Spring Security 验证的 JWT subject 解析为用户 UUID。
 *
 * <p>Spring Security 的 JWT Authentication 默认以 {@code sub} 作为
 * {@link Authentication#getName()}。该解析器集中处理空认证、匿名认证以及
 * 非 UUID subject，保证 Servlet 与 WebFlux 使用相同的失败语义。</p>
 */
final class AuthenticationUserIdResolver {

    /** 工具类不允许实例化。 */
    private AuthenticationUserIdResolver() {
    }

    /**
     * 从认证对象中解析当前用户 UUID。
     *
     * @param authentication Spring Security 已建立的认证对象
     * @return JWT subject 表示的用户 UUID
     * @throws AuthenticationCredentialsNotFoundException 认证缺失、匿名、未通过认证，
     *         或 subject 缺失、不是合法 UUID 时抛出
     */
    static UUID resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Current request is not authenticated");
        }

        String subject = authentication.getName();
        if (subject == null || subject.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("JWT subject is missing");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT subject is not a valid user UUID",
                    exception
            );
        }
    }
}
