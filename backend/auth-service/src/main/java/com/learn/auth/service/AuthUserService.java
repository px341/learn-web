package com.learn.auth.service;

import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.dto.UpdateCurrentUserDTO;
import com.learn.auth.vo.AuthVO;
import com.learn.auth.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 根据已认证 JWT 中的用户 ID 查询最新公开信息。
     */
    UserVO authUserMe();

    /**
     * 修改当前用户提交的资料字段并返回最新公开信息。
     */
    UserVO authUserUpdateMe(UpdateCurrentUserDTO userDTO);

    /**
     * 校验并替换当前用户头像，返回包含短期访问地址的最新用户信息。
     */
    UserVO authUserUpdateAvatar(MultipartFile avatar);
}
