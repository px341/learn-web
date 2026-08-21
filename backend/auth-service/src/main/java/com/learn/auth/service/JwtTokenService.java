package com.learn.auth.service;

import com.learn.auth.entity.UserEntity;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT Access Token 签发服务。
 *
 * <p>Token 只保存身份识别所需的最小声明，不写入密码、邮箱或额度等敏感及易变数据。</p>
 */
@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            SecretKey secretKey,
            @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl
    ) {
        this.secretKey = secretKey;
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * 为已完成认证的用户签发 Access Token。
     *
     * <p>用户 UUID 写入标准 sub；tokenVersion 可用于密码修改或全部登出后的旧令牌失效。</p>
     */
    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
}
