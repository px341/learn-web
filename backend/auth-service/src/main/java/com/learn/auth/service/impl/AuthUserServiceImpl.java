package com.learn.auth.service.impl;

import com.learn.auth.config.S3StorageProperties;
import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.dto.UpdateCurrentUserDTO;
import com.learn.auth.entity.UserEntity;
import com.learn.auth.exception.CurrentUserUnavailableException;
import com.learn.auth.exception.EmailAlreadyRegisteredException;
import com.learn.auth.exception.InvalidProfileUpdateException;
import com.learn.auth.exception.InvalidAvatarException;
import com.learn.auth.exception.PasswordConfirmationMismatchException;
import com.learn.auth.mapper.UserMapper;
import com.learn.auth.model.AvatarMetadata;
import com.learn.auth.service.AuthUserService;
import com.learn.auth.service.AvatarStorageService;
import com.learn.auth.service.JwtTokenService;
import com.learn.auth.vo.AuthVO;
import com.learn.auth.vo.UserVO;
import com.learn.security.currentuser.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 登录与注册业务实现。
 *
 * <p>登录时先按邮箱读取 BCrypt 哈希，再在 Java 内存中使用 PasswordEncoder 校验；
 * 注册时只保存密码哈希。两种流程成功后都会签发包含用户 UUID 的 JWT。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserServiceImpl implements AuthUserService {

    private static final int INITIAL_CREDITS = 3;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CurrentUserProvider currentUserProvider;
    private final AvatarStorageService avatarStorageService;
    private final S3StorageProperties storageProperties;

    @Override
    public AuthVO authUserLogin(AuthDTO authDTO) {
        // 邮箱不是秘密，可统一大小写；密码必须保持用户提交的原始内容，不能 trim。
        String email = normalizeEmail(authDTO.email());
        UserEntity user = userMapper.selectByEmail(email)
                .orElseThrow(AuthUserServiceImpl::invalidCredentials);

        // BCrypt 哈希自带盐，matches 会使用哈希内的盐验证本次明文密码。
        if (!passwordEncoder.matches(authDTO.password(), user.getPasswordHash())
                || !ACTIVE_STATUS.equals(user.getStatus())) {
            throw invalidCredentials();
        }

        return createAuthVO(user);
    }

    @Override
    @Transactional
    public AuthVO authUserRegister(RegisterRequestDTO registerRequest) {
        if (!registerRequest.password().equals(registerRequest.passwordConfirmation())) {
            throw new PasswordConfirmationMismatchException();
        }

        String email = normalizeEmail(registerRequest.email());
        if (userMapper.selectByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setName(registerRequest.name().trim());
        user.setEmail(email);
        // 数据库只接收 BCrypt 哈希，绝不保存注册请求中的明文密码。
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.setCredits(INITIAL_CREDITS);
        user.setStatus(ACTIVE_STATUS);
        user.setTokenVersion(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 唯一索引处理并发注册同一邮箱时的竞争条件。
            throw new EmailAlreadyRegisteredException();
        }

        return createAuthVO(user);
    }

    @Override
    public UserVO authUserMe() {
        return toUserVO(getCurrentActiveUser());
    }

    @Override
    @Transactional
    public UserVO authUserUpdateMe(UpdateCurrentUserDTO userDTO) {
        // 邮箱或者用户名必须有个不是空的
        if (userDTO.email() == null && userDTO.name() == null) {
            throw new InvalidProfileUpdateException("至少提供 name 或 email");
        }

        // 获取当前用户的情况及处理用户输入
        UserEntity user = getCurrentActiveUser();
        String name = normalizeName(userDTO.name());
        String email = userDTO.email() == null ? null : normalizeProfileEmail(userDTO.email());

        // 检查邮箱是否被使用了
        if (email != null) {
            userMapper.selectByEmail(email)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new EmailAlreadyRegisteredException();
                    });
        }

        // 要求name、email必须不同
        if (!(Objects.equals(user.getEmail(), email) && Objects.equals(user.getName(), name))) {
            // 规范化DTO重新处理
            UpdateCurrentUserDTO normalizedRequest = new UpdateCurrentUserDTO(name, email);
            try {
                int updatedRows = userMapper.updateUserInfoById(normalizedRequest, user.getId());
                if (updatedRows != 1) {
                    throw new CurrentUserUnavailableException();
                }
            } catch (DuplicateKeyException exception) {
                // 唯一索引兜底处理两个用户并发修改为同一邮箱的竞争条件。
                throw new EmailAlreadyRegisteredException();
            }
        }

        if (name != null) {
            user.setName(name);
        }
        if (email != null) {
            user.setEmail(email);
        }
        return toUserVO(user);
    }

    @Override
    @Transactional
    public UserVO authUserUpdateAvatar(MultipartFile avatar) {
        UserEntity user = getCurrentActiveUser();
        ValidatedAvatar validated = validateAvatar(avatar);
        String objectKey = "users/%s/avatars/%s.%s".formatted(
                user.getId(),
                UUID.randomUUID(),
                validated.extension()
        );
        AvatarMetadata metadata = new AvatarMetadata(
                storageProperties.bucket(),
                objectKey,
                normalizeOriginalName(avatar.getOriginalFilename(), validated.extension()),
                validated.contentType(),
                validated.content().length,
                sha256(validated.content()),
                Instant.now()
        );

        avatarStorageService.put(
                metadata.bucket(),
                metadata.objectKey(),
                metadata.contentType(),
                validated.content()
        );

        try {
            int updatedRows = userMapper.updateAvatarById(metadata, user.getId());
            if (updatedRows != 1) {
                throw new CurrentUserUnavailableException();
            }
        } catch (RuntimeException exception) {
            deleteAvatarQuietly(metadata.bucket(), metadata.objectKey());
            throw exception;
        }

        scheduleAvatarCleanup(
                user.getAvatarBucket(),
                user.getAvatarObjectKey(),
                metadata.bucket(),
                metadata.objectKey()
        );
        applyAvatarMetadata(user, metadata);
        return toUserVO(user);
    }

    private UserEntity getCurrentActiveUser() {
        UUID userId = currentUserProvider.getUserId();
        return userMapper.selectById(userId)
                .filter(candidate -> ACTIVE_STATUS.equals(candidate.getStatus()))
                .orElseThrow(CurrentUserUnavailableException::new);
    }

    private AuthVO createAuthVO(UserEntity user) {
        // UserVO 是允许返回给前端的字段白名单，不暴露 passwordHash 等内部字段。
        String token = jwtTokenService.createAccessToken(user);
        return new AuthVO(token, toUserVO(user));
    }

    private UserVO toUserVO(UserEntity user) {
        String avatarUrl = null;
        if (user.getAvatarBucket() != null && user.getAvatarObjectKey() != null) {
            avatarUrl = avatarStorageService.createReadUrl(
                    user.getAvatarBucket(),
                    user.getAvatarObjectKey()
            );
        }
        return new UserVO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCredits(),
                avatarUrl
        );
    }

    private void scheduleAvatarCleanup(
            String oldBucket,
            String oldObjectKey,
            String newBucket,
            String newObjectKey
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAvatarQuietly(oldBucket, oldObjectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteAvatarQuietly(oldBucket, oldObjectKey);
                } else {
                    deleteAvatarQuietly(newBucket, newObjectKey);
                }
            }
        });
    }

    private void deleteAvatarQuietly(String bucket, String objectKey) {
        if (bucket == null || objectKey == null) {
            return;
        }
        try {
            avatarStorageService.delete(bucket, objectKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete avatar object bucket={}, key={}",
                    bucket, objectKey, exception);
        }
    }

    private static void applyAvatarMetadata(UserEntity user, AvatarMetadata metadata) {
        user.setAvatarBucket(metadata.bucket());
        user.setAvatarObjectKey(metadata.objectKey());
        user.setAvatarOriginalName(metadata.originalName());
        user.setAvatarContentType(metadata.contentType());
        user.setAvatarSize(metadata.size());
        user.setAvatarSha256(metadata.sha256());
        user.setAvatarUpdatedAt(metadata.updatedAt());
    }

    private static ValidatedAvatar validateAvatar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new InvalidAvatarException("请选择头像文件");
        }
        if (avatar.getSize() > MAX_AVATAR_SIZE) {
            throw new InvalidAvatarException("头像不能超过 5MB");
        }

        final byte[] content;
        try {
            content = avatar.getBytes();
        } catch (IOException exception) {
            throw new InvalidAvatarException("头像文件读取失败");
        }

        if (hasPrefix(content, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new ValidatedAvatar(content, "image/png", "png");
        }
        if (hasPrefix(content, new int[]{0xFF, 0xD8, 0xFF})) {
            return new ValidatedAvatar(content, "image/jpeg", "jpg");
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I'
                && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E'
                && content[10] == 'B' && content[11] == 'P') {
            return new ValidatedAvatar(content, "image/webp", "webp");
        }
        throw new InvalidAvatarException("仅支持 PNG、JPG、WEBP 图片");
    }

    private static boolean hasPrefix(byte[] content, int[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeOriginalName(String originalName, String extension) {
        String normalized = originalName == null || originalName.isBlank()
                ? "avatar." + extension
                : originalName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("\0", "");
        if (normalized.isBlank()) {
            normalized = "avatar." + extension;
        }
        return normalized.length() <= MAX_ORIGINAL_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ORIGINAL_NAME_LENGTH);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ValidatedAvatar(byte[] content, String contentType, String extension) {
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeProfileEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            throw new InvalidProfileUpdateException("邮箱不能为空");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new InvalidProfileUpdateException("显示名称不能为空");
        }
        return normalized;
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("邮箱或密码错误");
    }
}
