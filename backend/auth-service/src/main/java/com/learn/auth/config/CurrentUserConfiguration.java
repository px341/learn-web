package com.learn.auth.config;

import com.learn.security.currentuser.CurrentUserProvider;
import com.learn.security.currentuser.SecurityContextCurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为基于 Spring MVC 的 Auth Service 提供当前用户读取器。
 */
@Configuration
public class CurrentUserConfiguration {

    @Bean
    public CurrentUserProvider currentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }
}
