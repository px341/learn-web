package com.learn.auth.service.impl;

import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.entity.UserEntity;
import com.learn.auth.exception.CurrentUserUnavailableException;
import com.learn.auth.exception.EmailAlreadyRegisteredException;
import com.learn.auth.exception.PasswordConfirmationMismatchException;
import com.learn.auth.mapper.UserMapper;
import com.learn.auth.service.AuthUserService;
import com.learn.auth.service.JwtTokenService;
import com.learn.auth.vo.AuthVO;
import com.learn.auth.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * 登录与注册业务实现。
 *
 * <p>登录时先按邮箱读取 BCrypt 哈希，再在 Java 内存中使用 PasswordEncoder 校验；
 * 注册时只保存密码哈希。两种流程成功后都会签发包含用户 UUID 的 JWT。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthUserServiceImpl implements AuthUserService {

    private static final int INITIAL_CREDITS = 3;
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

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
    public UserVO authUserMe(UUID userId) {
        UserEntity user = userMapper.selectById(userId)
                .filter(candidate -> ACTIVE_STATUS.equals(candidate.getStatus()))
                .orElseThrow(CurrentUserUnavailableException::new);
        return toUserVO(user);
    }

    private AuthVO createAuthVO(UserEntity user) {
        // UserVO 是允许返回给前端的字段白名单，不暴露 passwordHash 等内部字段。
        String token = jwtTokenService.createAccessToken(user);
        return new AuthVO(token, toUserVO(user));
    }

    private static UserVO toUserVO(UserEntity user) {
        return new UserVO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCredits()
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("邮箱或密码错误");
    }
}
