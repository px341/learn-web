package com.learn.security.currentuser;

import java.util.UUID;

/**
 * 为基于 Servlet 的 Spring MVC 请求提供当前登录用户身份。
 *
 * <p>实现类从当前线程的 SecurityContext 读取身份，因此应在 Spring Security
 * 认证过滤器完成之后、处理当前请求的线程内调用。</p>
 */
public interface CurrentUserProvider {

    /**
     * 获取当前已认证用户的 UUID。
     *
     * @return JWT subject 对应的用户 UUID
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         当前请求未认证，或认证 subject 不是合法 UUID 时抛出
     */
    UUID getUserId();
}
