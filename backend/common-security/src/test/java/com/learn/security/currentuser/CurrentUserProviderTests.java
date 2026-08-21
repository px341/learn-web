package com.learn.security.currentuser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserProviderTests {

    private final CurrentUserProvider servletProvider = new SecurityContextCurrentUserProvider();
    private final ReactiveCurrentUserProvider reactiveProvider = new ReactiveSecurityContextCurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void servletProviderReturnsAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(authenticated(userId.toString()));

        assertThat(servletProvider.getUserId()).isEqualTo(userId);
    }

    @Test
    void servletProviderRejectsMissingAuthentication() {
        assertThatThrownBy(servletProvider::getUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void reactiveProviderReturnsAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();

        UUID resolvedUserId = reactiveProvider.getUserId()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        authenticated(userId.toString())
                ))
                .block();

        assertThat(resolvedUserId).isEqualTo(userId);
    }

    @Test
    void reactiveProviderRejectsMissingAuthentication() {
        assertThatThrownBy(() -> reactiveProvider.getUserId().block())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void providersRejectNonUuidSubject() {
        SecurityContextHolder.getContext().setAuthentication(authenticated("not-a-uuid"));

        assertThatThrownBy(servletProvider::getUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("JWT subject is not a valid user UUID");
    }

    private static Authentication authenticated(String subject) {
        return UsernamePasswordAuthenticationToken.authenticated(subject, null, List.of());
    }
}
