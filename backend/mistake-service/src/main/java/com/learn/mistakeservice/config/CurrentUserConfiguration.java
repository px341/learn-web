package com.learn.mistakeservice.config;

import com.learn.security.currentuser.CurrentUserProvider;
import com.learn.security.currentuser.SecurityContextCurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为 Mistake Service 注册当前用户读取器。
 *
 * <p>JWT 由本服务的 Resource Server 配置完成验证；该读取器只从当前请求的
 * SecurityContext 中读取已经验证的 JWT subject，不会调用 Auth Service。</p>
 */
@Configuration
public class CurrentUserConfiguration {

    @Bean
    public CurrentUserProvider currentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }
}
