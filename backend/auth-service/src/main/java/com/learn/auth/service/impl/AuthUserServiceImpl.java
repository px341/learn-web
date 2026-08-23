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
 * 当前用户认证与账户资料业务实现。
 *
 * <p>主要职责包括登录、注册、查询及修改当前用户资料，以及头像对象的上传和替换。
 * 用户身份始终从已验证 JWT 的 subject 获取，不接受客户端提交的用户 ID。</p>
 *
 * <p>头像二进制保存在 Garage 私有 Bucket，users 表只保存对象键、真实 MIME、摘要等元数据；
 * 返回用户信息时再为头像生成短期预签名 URL，数据库不会保存临时 URL 或存储密钥。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserServiceImpl implements AuthUserService {

    /** 新用户注册成功后赠送的初始分析次数。 */
    private static final int INITIAL_CREDITS = 3;

    /** 只有 ACTIVE 用户可以登录或通过 JWT 访问个人接口。 */
    private static final String ACTIVE_STATUS = "ACTIVE";

    /** 业务层允许的头像最大字节数，与接口文档中的 5MB 保持一致。 */
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    /** users.avatar_original_name 的数据库列长度上限。 */
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    /** 用户认证信息和头像元数据的 MyBatis 数据访问层。 */
    private final UserMapper userMapper;

    /** 用于 BCrypt 密码哈希生成和明文密码验证。 */
    private final PasswordEncoder passwordEncoder;

    /** 登录、注册成功后为用户签发 Access Token。 */
    private final JwtTokenService jwtTokenService;

    /** 从 Spring Security 上下文读取已验证 JWT 中的用户 UUID。 */
    private final CurrentUserProvider currentUserProvider;

    /** 封装 Garage 的上传、删除和预签名 URL 操作。 */
    private final AvatarStorageService avatarStorageService;

    /** 提供头像 Bucket 名称等对象存储配置。 */
    private final S3StorageProperties storageProperties;

    /**
     * 使用邮箱和密码登录。
     *
     * <p>处理步骤：</p>
     * <ol>
     *     <li>规范化邮箱，消除首尾空格及大小写差异；</li>
     *     <li>按邮箱读取用户及其 BCrypt 哈希；</li>
     *     <li>验证密码，同时确认账号状态为 ACTIVE；</li>
     *     <li>签发 JWT，并返回公开用户信息。</li>
     * </ol>
     *
     * <p>邮箱不存在、密码错误、账号禁用使用同一种异常，避免攻击者根据错误信息
     * 判断某个邮箱是否已经注册。</p>
     *
     * @param authDTO 客户端提交的邮箱和原始密码
     * @return JWT 与当前用户公开信息
     * @throws BadCredentialsException 邮箱、密码或账号状态不允许登录
     */
    @Override
    public AuthVO authUserLogin(AuthDTO authDTO) {
        // 邮箱不区分大小写，可以统一格式；密码必须保留原始内容，不能 trim。
        String email = normalizeEmail(authDTO.email());
        UserEntity user = userMapper.selectByEmail(email)
                .orElseThrow(AuthUserServiceImpl::invalidCredentials);

        // 无论密码错误还是账号不可用，都返回相同错误，避免泄露账号状态。
        // BCrypt 哈希自带盐，matches 会根据哈希中的参数校验明文密码。
        if (!passwordEncoder.matches(authDTO.password(), user.getPasswordHash())
                || !ACTIVE_STATUS.equals(user.getStatus())) {
            throw invalidCredentials();
        }

        return createAuthVO(user);
    }

    /**
     * 注册用户并直接建立登录态。
     *
     * <p>{@link Transactional} 保证用户插入失败时数据库事务整体回滚。Service 中的
     * 邮箱重复查询用于尽早给出业务错误，users 邮箱唯一索引用于处理两个请求并发注册
     * 同一邮箱的竞争情况。</p>
     *
     * @param registerRequest 姓名、邮箱、密码及确认密码
     * @return 新用户的 JWT 与公开信息
     * @throws PasswordConfirmationMismatchException 两次密码不一致
     * @throws EmailAlreadyRegisteredException 邮箱已经被注册
     */
    @Override
    @Transactional
    public AuthVO authUserRegister(RegisterRequestDTO registerRequest) {
        // 两次密码比较必须发生在任何哈希或持久化操作之前。
        if (!registerRequest.password().equals(registerRequest.passwordConfirmation())) {
            throw new PasswordConfirmationMismatchException();
        }

        // 先查询可以尽早返回友好错误；数据库唯一索引仍是最终并发保护。
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

    /**
     * 查询 JWT 所代表的当前用户。
     *
     * <p>不使用登录时缓存的用户对象，而是重新查询数据库，因此可以返回最新的资料、
     * 剩余额度和头像。若有头像，{@link #toUserVO(UserEntity)} 会为本次响应生成新的
     * 短期预签名地址。</p>
     *
     * @return 当前用户最新公开信息
     * @throws CurrentUserUnavailableException JWT 对应用户不存在或已不可用
     */
    @Override
    public UserVO authUserMe() {
        // 每次从数据库读取最新额度和资料，不长期依赖登录时返回的用户快照。
        return toUserVO(getCurrentActiveUser());
    }

    /**
     * 修改当前用户的显示名称和/或邮箱。
     *
     * <p>DTO 中的 {@code null} 表示客户端没有提交该字段，MyBatis 动态 SQL 不会修改它。
     * 邮箱会去除首尾空格并转换为小写，显示名称只去除首尾空格。更新成功后同步修改当前
     * 内存中的 {@link UserEntity}，避免为了构造响应再查询一次数据库。</p>
     *
     * <p>邮箱唯一性仍采用“业务查询 + 数据库唯一索引”双重保护：前者提供友好错误，
     * 后者兜住并发修改为同一邮箱的情况。</p>
     *
     * @param userDTO 客户端实际提交的资料字段
     * @return 修改后的公开用户信息
     * @throws InvalidProfileUpdateException 没有提交字段，或提交值规范化后为空
     * @throws EmailAlreadyRegisteredException 新邮箱属于其他用户
     * @throws CurrentUserUnavailableException 当前用户不存在、不可用或更新失败
     */
    @Override
    @Transactional
    public UserVO authUserUpdateMe(UpdateCurrentUserDTO userDTO) {
        // null 表示未提交该字段；两个字段都未提交时没有可执行的更新。
        if (userDTO.email() == null && userDTO.name() == null) {
            throw new InvalidProfileUpdateException("至少提供 name 或 email");
        }

        // 先加载 JWT 对应的有效用户，再规范化客户端实际提交的字段。
        UserEntity user = getCurrentActiveUser();
        String name = normalizeName(userDTO.name());
        String email = userDTO.email() == null ? null : normalizeProfileEmail(userDTO.email());

        // 允许继续使用自己的邮箱，但不能占用其他用户的邮箱。
        if (email != null) {
            userMapper.selectByEmail(email)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new EmailAlreadyRegisteredException();
                    });
        }

        // 同时提交的 name、email 都未变化时跳过无意义的 UPDATE；
        // DTO 中为 null 的字段会由 MyBatis 动态 SQL 保持原值。
        if (!(Objects.equals(user.getEmail(), email) && Objects.equals(user.getName(), name))) {
            // 只把规范化后的值交给 Mapper，避免数据库保存首尾空格或大小写不统一的邮箱。
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

    /**
     * 替换当前用户头像。
     *
     * <p>完整流程如下：</p>
     * <ol>
     *     <li>根据 JWT 获取当前有效用户；</li>
     *     <li>读取文件字节，检查 5MB 上限，并通过文件头魔数识别真实格式；</li>
     *     <li>生成不会覆盖旧头像的唯一 Object Key，并计算 SHA-256；</li>
     *     <li>先将新对象上传至 Garage；</li>
     *     <li>通过一条 SQL 原子替换 users 表中的全部头像元数据；</li>
     *     <li>事务提交后删除旧对象；事务回滚时删除新对象；</li>
     *     <li>为新对象生成短期预签名地址并返回。</li>
     * </ol>
     *
     * <p><strong>为什么需要补偿清理：</strong>Spring 的数据库事务只能回滚 PostgreSQL，
     * 不能回滚已经发给 Garage 的 S3 请求。因此这里采用“新对象先上传、数据库后更新”并
     * 配合 {@link TransactionSynchronization}：数据库失败时删除新对象，数据库提交成功
     * 后再删除旧对象，从而尽量避免数据库悬空引用或对象泄漏。</p>
     *
     * @param avatar multipart/form-data 中名为 avatar 的文件
     * @return 包含新头像预签名 URL 的用户公开信息
     * @throws InvalidAvatarException 文件为空、超过限制或真实格式不受支持
     * @throws CurrentUserUnavailableException 当前用户不存在、不可用或数据库更新失败
     * @throws com.learn.auth.exception.AvatarStorageException Garage 上传或签名操作失败
     */
    @Override
    @Transactional
    public UserVO authUserUpdateAvatar(MultipartFile avatar) {
        // 用户 ID 来自 JWT；随后按文件字节识别真实格式，不能信任客户端声明的 Content-Type。
        UserEntity user = getCurrentActiveUser();
        ValidatedAvatar validated = validateAvatar(avatar);

        // 每次替换都创建新对象，避免覆盖旧头像后因数据库失败而无法恢复。
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

        // 必须先确认新对象上传成功，再让数据库指向它，避免数据库引用不存在的对象。
        avatarStorageService.put(
                metadata.bucket(),
                metadata.objectKey(),
                metadata.contentType(),
                validated.content()
        );

        try {
            // 七个头像字段通过一条 SQL 原子替换，不能留下半套元数据。
            int updatedRows = userMapper.updateAvatarById(metadata, user.getId());
            if (updatedRows != 1) {
                throw new CurrentUserUnavailableException();
            }
        } catch (RuntimeException exception) {
            // 数据库更新尚未成功，新对象没有任何有效引用，应立即尽力清理。
            deleteAvatarQuietly(metadata.bucket(), metadata.objectKey());
            throw exception;
        }

        // 真正提交后删除旧对象；若事务最终回滚，则反过来删除本次上传的新对象。
        scheduleAvatarCleanup(
                user.getAvatarBucket(),
                user.getAvatarObjectKey(),
                metadata.bucket(),
                metadata.objectKey()
        );

        // Mapper 不会自动刷新当前内存对象，手动同步后即可生成本次响应。
        applyAvatarMetadata(user, metadata);
        return toUserVO(user);
    }

    /**
     * 从安全上下文取得当前用户 ID，并查询可执行业务操作的用户。
     *
     * <p>这里再次检查数据库中的账号状态，因为 JWT 在有效期内不会随数据库状态变化而
     * 自动失效。例如管理员刚禁用账号时，旧 JWT 可能仍通过签名和有效期校验，但不能继续
     * 操作个人数据。</p>
     *
     * @return 当前 ACTIVE 用户的内部实体
     * @throws CurrentUserUnavailableException 用户不存在或状态不是 ACTIVE
     */
    private UserEntity getCurrentActiveUser() {
        // CurrentUserProvider 只读取已通过 Spring Security 验证的 JWT subject。
        UUID userId = currentUserProvider.getUserId();
        return userMapper.selectById(userId)
                .filter(candidate -> ACTIVE_STATUS.equals(candidate.getStatus()))
                .orElseThrow(CurrentUserUnavailableException::new);
    }

    /**
     * 为已完成身份校验的用户创建认证响应。
     *
     * @param user 内部用户实体
     * @return 新 JWT 和经过字段白名单转换的用户信息
     */
    private AuthVO createAuthVO(UserEntity user) {
        // UserVO 是允许返回给前端的字段白名单，不暴露 passwordHash 等内部字段。
        String token = jwtTokenService.createAccessToken(user);
        return new AuthVO(token, toUserVO(user));
    }

    /**
     * 将内部实体转换为允许返回客户端的 UserVO。
     *
     * <p>转换过程不会暴露 passwordHash、status、tokenVersion 或 Garage Object Key。
     * 数据库中有头像对象定位信息时，才临时生成可供浏览器读取的预签名 URL。该 URL 到期
     * 后应通过重新请求当前用户接口获得，而不是作为永久地址缓存。</p>
     *
     * @param user 数据库用户实体或注册流程中新建的实体
     * @return 对外公开的用户视图
     */
    private UserVO toUserVO(UserEntity user) {
        String avatarUrl = null;
        if (user.getAvatarBucket() != null && user.getAvatarObjectKey() != null) {
            // 预签名 URL 有有效期，只用于本次响应，绝不能回写 users 表。
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

    /**
     * 注册头像对象的事务完成后清理动作。
     *
     * <p>提交成功意味着 users 表已经指向新对象，此时删除旧对象；回滚意味着数据库仍然
     * 指向旧对象，此时删除刚上传但失去引用的新对象。删除采用尽力而为策略，不从事务完成
     * 回调中再次抛出异常。</p>
     *
     * <p>直接调用 Service 实例的单元测试没有 Spring 事务代理，也就没有事务同步上下文；
     * 该场景下数据库 Mapper 已同步返回成功，因此直接尝试删除旧对象。</p>
     *
     * @param oldBucket 原头像所在 Bucket，可以为 null
     * @param oldObjectKey 原头像对象键，可以为 null
     * @param newBucket 新头像所在 Bucket
     * @param newObjectKey 新头像对象键
     */
    private void scheduleAvatarCleanup(
            String oldBucket,
            String oldObjectKey,
            String newBucket,
            String newObjectKey
    ) {
        // 单元测试等未开启事务同步的调用没有 afterCompletion，只能直接清理旧对象。
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAvatarQuietly(oldBucket, oldObjectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    // 数据库已经指向新对象，旧对象可以安全删除。
                    deleteAvatarQuietly(oldBucket, oldObjectKey);
                } else {
                    // 数据库仍指向旧对象，删除失去引用的新对象作为补偿。
                    deleteAvatarQuietly(newBucket, newObjectKey);
                }
            }
        });
    }

    /**
     * 尽力删除一个头像对象。
     *
     * <p>这是补偿和清理逻辑，删除失败不应覆盖原始业务结果。例如数据库已经成功提交时，
     * 旧对象删除失败不能再向客户端报告头像更新失败，否则客户端会误以为数据库也未更新。
     * 当前实现记录 Bucket、Object Key 和异常，后续可由清理任务根据日志重试。</p>
     *
     * @param bucket 对象所在 Bucket；null 时无需删除
     * @param objectKey 对象键；null 时无需删除
     */
    private void deleteAvatarQuietly(String bucket, String objectKey) {
        if (bucket == null || objectKey == null) {
            return;
        }
        try {
            avatarStorageService.delete(bucket, objectKey);
        } catch (RuntimeException exception) {
            // 清理失败不能反向破坏已经完成的数据库事务，记录对象位置供后续重试。
            log.warn("Failed to delete avatar object bucket={}, key={}",
                    bucket, objectKey, exception);
        }
    }

    /**
     * 把刚写入数据库的头像元数据同步到当前内存实体。
     *
     * <p>MyBatis 的 UPDATE 只返回受影响行数，不会自动把参数中的字段刷新进先前查询的
     * {@link UserEntity}。这里同步七个字段后，{@link #toUserVO(UserEntity)} 才能立即为
     * 新头像生成预签名地址。</p>
     *
     * @param user 待同步的当前用户实体
     * @param metadata 已成功写入数据库的头像元数据
     */
    private static void applyAvatarMetadata(UserEntity user, AvatarMetadata metadata) {
        user.setAvatarBucket(metadata.bucket());
        user.setAvatarObjectKey(metadata.objectKey());
        user.setAvatarOriginalName(metadata.originalName());
        user.setAvatarContentType(metadata.contentType());
        user.setAvatarSize(metadata.size());
        user.setAvatarSha256(metadata.sha256());
        user.setAvatarUpdatedAt(metadata.updatedAt());
    }

    /**
     * 读取并验证头像文件，同时确定服务端可信的 MIME 和扩展名。
     *
     * <p>不能使用 {@link MultipartFile#getContentType()} 或原始文件名判断格式，因为它们
     * 都由客户端提供，可以把任意内容伪装成 {@code image/png}。这里检查常见文件签名：</p>
     * <ul>
     *     <li>PNG：固定的 8 字节签名；</li>
     *     <li>JPEG：以 FF D8 FF 开始；</li>
     *     <li>WEBP：RIFF 容器，并在第 9～12 字节标识 WEBP。</li>
     * </ul>
     *
     * <p>文件上限只有 5MB，因此在内存中读取一次可以同时用于上传和 SHA-256 计算，避免
     * 重复读取临时文件或输入流。</p>
     *
     * @param avatar 客户端上传的 multipart 文件
     * @return 文件字节、服务端识别的 MIME 和安全扩展名
     * @throws InvalidAvatarException 文件为空、过大、读取失败或格式不受支持
     */
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

        // 使用文件头魔数识别真实格式，不根据文件名后缀或请求 Content-Type 判断。
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

    /**
     * 判断文件字节是否以指定魔数开头。
     *
     * <p>byte 在 Java 中是有符号数，比较前通过 {@code & 0xFF} 转换到 0～255，才能正确
     * 匹配 PNG 的 0x89、JPEG 的 0xFF 等大于 127 的字节。</p>
     *
     * @param content 完整文件字节
     * @param signature 使用无符号整数表示的文件签名
     * @return 内容长度足够且每个签名字节都相同则返回 true
     */
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

    /**
     * 清理用于审计的原始文件名。
     *
     * <p>浏览器通常只提交文件名，但其他客户端可能提交 Windows 或 Unix 完整路径；这里只
     * 保留最后一个路径段，同时移除 NUL 字符并限制长度。该名称不会参与 Object Key 生成，
     * 因此用户文件名无法控制 Garage 中的存储路径。</p>
     *
     * @param originalName MultipartFile 提供的原始名称，可以为空
     * @param extension 服务端根据文件内容确定的扩展名
     * @return 可安全写入 avatar_original_name 的审计名称
     */
    private static String normalizeOriginalName(String originalName, String extension) {
        // 原始文件名只用于审计：移除客户端路径和 NUL，并限制为数据库列允许的长度。
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

    /**
     * 计算文件内容的 SHA-256 小写十六进制摘要。
     *
     * <p>摘要保存在 users.avatar_sha256，可用于完整性检查和未来的内容去重。SHA-256 是
     * Java 运行时必须支持的标准算法；若运行环境异常缺失该算法，转换为不可恢复的
     * {@link IllegalStateException}。</p>
     *
     * @param content 文件完整字节
     * @return 64 个小写十六进制字符
     */
    private static String sha256(byte[] content) {
        try {
            // 摘要用于完整性校验和后续去重，不参与对象访问授权。
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * 头像验证后的内部结果。
     *
     * @param content 已读取且大小合法的文件字节
     * @param contentType 根据魔数识别的可信 MIME，用于 S3 Content-Type 和数据库元数据
     * @param extension 根据真实格式确定的扩展名，用于生成 Object Key
     */
    private record ValidatedAvatar(byte[] content, String contentType, String extension) {
    }

    /**
     * 规范化登录或注册邮箱：去除首尾空格并按固定 Locale 转为小写。
     * 使用 {@link Locale#ROOT} 可避免土耳其语等系统 Locale 导致大小写转换结果变化。
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化资料修改中的邮箱，并拒绝只包含空白字符的值。
     * DTO 的 Bean Validation 负责格式和长度，这里负责 trim 后的业务语义校验。
     */
    private static String normalizeProfileEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            throw new InvalidProfileUpdateException("邮箱不能为空");
        }
        return normalized;
    }

    /**
     * 规范化可选显示名称。
     *
     * @param name null 表示未提交该字段；非 null 时去除首尾空格
     * @return null 或规范化后的非空显示名称
     * @throws InvalidProfileUpdateException 去除首尾空格后为空
     */
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

    /**
     * 构造统一登录失败异常，确保所有凭证失败路径使用相同对外信息。
     */
    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("邮箱或密码错误");
    }
}
