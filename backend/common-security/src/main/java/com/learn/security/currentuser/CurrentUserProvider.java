package com.learn.security.currentuser;

import java.util.UUID;

/**
 * 为普通 Spring MVC 请求提供当前登录用户。
 */
public interface CurrentUserProvider {

    UUID getUserId();
}
