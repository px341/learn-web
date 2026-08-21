package com.learn.security.currentuser;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * 将已通过 Spring Security 验证的 JWT subject 解析为用户 UUID。
 */
final class AuthenticationUserIdResolver {

    private AuthenticationUserIdResolver() {
    }

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
