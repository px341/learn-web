package com.learn.auth.service;

import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.vo.AuthVO;

/**
 * 用户认证用例，包括登录和注册。
 */
public interface AuthUserService {

    /**
     * 校验邮箱和密码，成功后签发 JWT。
     */
    AuthVO authUserLogin(AuthDTO authDTO);

    /**
     * 创建用户并将初始额度、状态和密码哈希写入数据库。
     */
    AuthVO authUserRegister(RegisterRequestDTO registerRequest);
}
