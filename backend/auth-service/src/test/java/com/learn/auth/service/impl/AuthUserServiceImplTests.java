package com.learn.auth.service.impl;

import com.learn.auth.config.S3StorageProperties;
import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.dto.UpdateCurrentUserDTO;
import com.learn.auth.entity.UserEntity;
import com.learn.auth.exception.CurrentUserUnavailableException;
import com.learn.auth.exception.EmailAlreadyRegisteredException;
import com.learn.auth.exception.InvalidAvatarException;
import com.learn.auth.exception.InvalidProfileUpdateException;
import com.learn.auth.exception.PasswordConfirmationMismatchException;
import com.learn.auth.mapper.UserMapper;
import com.learn.auth.model.AvatarMetadata;
import com.learn.auth.service.AvatarStorageService;
import com.learn.auth.service.JwtTokenService;
import com.learn.auth.vo.AuthVO;
import com.learn.security.currentuser.CurrentUserProvider;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.net.URI;
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

    @Test
    void currentUserIsLoadedByJwtSubjectId() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper, user.getId());

        assertThat(service.authUserMe())
                .extracting("id", "email", "credits")
                .containsExactly(user.getId(), "demo@example.com", 3);
    }

    @Test
    void currentUserRejectsUnknownJwtSubjectId() {
        AuthUserServiceImpl service = service(new InMemoryUserMapper(), UUID.randomUUID());

        assertThatThrownBy(service::authUserMe)
                .isInstanceOf(CurrentUserUnavailableException.class);
    }

    @Test
    void currentUserProfileUpdatesOnlyProvidedFields() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper, user.getId());

        assertThat(service.authUserUpdateMe(new UpdateCurrentUserDTO(" 新名称 ", null)))
                .extracting("id", "name", "email")
                .containsExactly(user.getId(), "新名称", "demo@example.com");
    }

    @Test
    void currentUserProfileNormalizesEmail() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper, user.getId());

        assertThat(service.authUserUpdateMe(new UpdateCurrentUserDTO(null, " New@Example.com ")))
                .extracting("email")
                .isEqualTo("new@example.com");
    }

    @Test
    void currentUserProfileRejectsRequestWithoutChanges() {
        AuthUserServiceImpl service = service(new InMemoryUserMapper(), UUID.randomUUID());

        assertThatThrownBy(() -> service.authUserUpdateMe(
                new UpdateCurrentUserDTO(null, null)
        )).isInstanceOf(InvalidProfileUpdateException.class);
    }

    @Test
    void currentUserProfileRejectsEmailOwnedByAnotherUser() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity currentUser = activeUser("current@example.com", passwordEncoder.encode("123456"));
        UserEntity otherUser = activeUser("taken@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(currentUser.getEmail(), currentUser);
        mapper.users.put(otherUser.getEmail(), otherUser);
        AuthUserServiceImpl service = service(mapper, currentUser.getId());

        assertThatThrownBy(() -> service.authUserUpdateMe(
                new UpdateCurrentUserDTO(null, " Taken@Example.com ")
        )).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void currentUserAvatarUsesDetectedContentTypeAndReturnsReadUrl() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper, user.getId());
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };

        var result = service.authUserUpdateAvatar(new MockMultipartFile(
                "avatar",
                "avatar.txt",
                "text/plain",
                pngHeader
        ));

        assertThat(user.getAvatarContentType()).isEqualTo("image/png");
        assertThat(user.getAvatarObjectKey())
                .startsWith("users/" + user.getId() + "/avatars/")
                .endsWith(".png");
        assertThat(user.getAvatarSha256()).matches("[0-9a-f]{64}");
        assertThat(result.avatarUrl()).contains(user.getAvatarObjectKey());
    }

    @Test
    void currentUserAvatarRejectsSpoofedContentType() {
        InMemoryUserMapper mapper = new InMemoryUserMapper();
        UserEntity user = activeUser("demo@example.com", passwordEncoder.encode("123456"));
        mapper.users.put(user.getEmail(), user);
        AuthUserServiceImpl service = service(mapper, user.getId());

        assertThatThrownBy(() -> service.authUserUpdateAvatar(new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                "not an image".getBytes(StandardCharsets.UTF_8)
        ))).isInstanceOf(InvalidAvatarException.class);
    }

    private AuthUserServiceImpl service(UserMapper mapper) {
        return service(mapper, UUID.randomUUID());
    }

    private AuthUserServiceImpl service(UserMapper mapper, UUID currentUserId) {
        byte[] keyBytes = "test-secret-key-that-is-at-least-32-bytes-long"
                .getBytes(StandardCharsets.UTF_8);
        JwtTokenService tokenService = new JwtTokenService(
                Keys.hmacShaKeyFor(keyBytes),
                Duration.ofMinutes(15)
        );
        CurrentUserProvider currentUserProvider = () -> currentUserId;
        return new AuthUserServiceImpl(
                mapper,
                passwordEncoder,
                tokenService,
                currentUserProvider,
                new NoOpAvatarStorageService(),
                new S3StorageProperties(
                        URI.create("http://localhost:3900"),
                        URI.create("http://localhost:3900"),
                        "garage",
                        "mistake-images",
                        "access-key",
                        "secret-key",
                        true,
                        Duration.ofMinutes(15)
                )
        );
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
        public Optional<UserEntity> selectById(UUID id) {
            return users.values().stream()
                    .filter(user -> id.equals(user.getId()))
                    .findFirst();
        }

        @Override
        public int insert(UserEntity user) {
            users.put(user.getEmail(), user);
            return 1;
        }

        @Override
        public int updateUserInfoById(UpdateCurrentUserDTO userDTO, UUID id) {
            UserEntity user = users.values().stream()
                    .filter(candidate -> id.equals(candidate.getId()))
                    .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (user == null) {
                return 0;
            }

            if (userDTO.email() != null) {
                UserEntity emailOwner = users.get(userDTO.email());
                if (emailOwner != null && !emailOwner.getId().equals(id)) {
                    throw new DuplicateKeyException("duplicate email");
                }
                users.remove(user.getEmail());
                user.setEmail(userDTO.email());
                users.put(user.getEmail(), user);
            }
            if (userDTO.name() != null) {
                user.setName(userDTO.name());
            }
            return 1;
        }

        @Override
        public int updateAvatarById(AvatarMetadata avatar, UUID id) {
            UserEntity user = users.values().stream()
                    .filter(candidate -> id.equals(candidate.getId()))
                    .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (user == null) {
                return 0;
            }
            user.setAvatarBucket(avatar.bucket());
            user.setAvatarObjectKey(avatar.objectKey());
            user.setAvatarOriginalName(avatar.originalName());
            user.setAvatarContentType(avatar.contentType());
            user.setAvatarSize(avatar.size());
            user.setAvatarSha256(avatar.sha256());
            user.setAvatarUpdatedAt(avatar.updatedAt());
            return 1;
        }
    }

    private static final class NoOpAvatarStorageService implements AvatarStorageService {

        @Override
        public void put(String bucket, String objectKey, String contentType, byte[] content) {
        }

        @Override
        public void delete(String bucket, String objectKey) {
        }

        @Override
        public String createReadUrl(String bucket, String objectKey) {
            return "http://localhost:3900/" + bucket + "/" + objectKey;
        }
    }
}
