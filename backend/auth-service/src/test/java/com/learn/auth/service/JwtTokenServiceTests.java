package com.learn.auth.service;

import com.learn.auth.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTests {

    @Test
    void tokenContainsUserIdAsSubject() {
        SecretKey key = Keys.hmacShaKeyFor(
                "test-secret-key-that-is-at-least-32-bytes-long"
                        .getBytes(StandardCharsets.UTF_8)
        );
        JwtTokenService service = new JwtTokenService(key, Duration.ofMinutes(15));
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setTokenVersion(2);

        String token = service.createAccessToken(user);
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("tokenVersion", Integer.class)).isEqualTo(2);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
