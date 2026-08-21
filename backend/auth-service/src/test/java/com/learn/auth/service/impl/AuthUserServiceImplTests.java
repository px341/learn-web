package com.learn.auth.service.impl;

import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.entity.UserEntity;
import com.learn.auth.exception.PasswordConfirmationMismatchException;
import com.learn.auth.mapper.UserMapper;
import com.learn.auth.service.JwtTokenService;
import com.learn.auth.vo.AuthVO;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserServiceImplTests {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void loginChecksBcryptHashAndReturnsToken() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper);

        AuthVO result = service.authUserLogin(new AuthDTO(" Demo@Example.com ", "123456"));

        assertThat(result.token()).isNotBlank();
        assertThat(result.user().id()).isEqualTo(user.getId());
        assertThat(result.user().email()).isEqualTo("demo@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("correct-password"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper);

        assertThatThrownBy(() -> service.authUserLogin(
                new AuthDTO("demo@example.com", "wrong-password")
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void registerStoresHashInsteadOfPlaintextAndReturnsToken() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        AuthUserServiceImpl service = service(mapper);

        AuthVO result = service.authUserRegister(new RegisterRequestDTO(
                " 新用户 ",
                " New@Example.com ",
                "123456",
                "123456"
        ));

        UserEntity inserted = mapper.users.get("new@example.com");
        assertThat(inserted.getPasswordHash()).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches("123456", inserted.getPasswordHash())).isTrue();
        assertThat(inserted.getName()).isEqualTo("新用户");
        assertThat(inserted.getCredits()).isEqualTo(3);
        assertThat(result.token()).isNotBlank();
    }

    @Test
    void registerRejectsDifferentPasswordConfirmation() {
        AuthUserServiceImpl service = service(new InMemoryUserMapper());

        assertThatThrownBy(() -> service.authUserRegister(new RegisterRequestDTO(
                "新用户",
                "new@example.com",
                "123456",
                "654321"
        ))).isInstanceOf(PasswordConfirmationMismatchException.class);
    }

    private AuthUserServiceImpl service(UserMapper mapper) {
        byte[] keyBytes = "test-secret-key-that-is-at-least-32-bytes-long"
                .getBytes(StandardCharsets.UTF_8);
        JwtTokenService tokenService = new JwtTokenService(
                Keys.hmacShaKeyFor(keyBytes),
                Duration.ofMinutes(15)
        );
        return new AuthUserServiceImpl(mapper, passwordEncoder, tokenService);
    }

    private static UserEntity activeUser(String email, String passwordHash) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setName("演示用户");
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setCredits(3);
        user.setStatus("ACTIVE");
        user.setTokenVersion(0);
        return user;
    }

    private static final class InMemoryUserMapper implements UserMapper {

        private final Map<String, UserEntity> users = new HashMap<>();

        @Override
        public Optional<UserEntity> selectByEmail(String email) {
            return Optional.ofNullable(users.get(email));
        }

        @Override
        public int insert(UserEntity user) {
            users.put(user.getEmail(), user);
            return 1;
        }
    }
}
