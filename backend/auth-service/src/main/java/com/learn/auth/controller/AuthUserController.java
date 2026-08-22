package com.learn.auth.controller;

import com.learn.auth.dto.AuthDTO;
import com.learn.auth.dto.RegisterRequestDTO;
import com.learn.auth.service.AuthUserService;
import com.learn.auth.vo.AuthVO;
import com.learn.auth.vo.UserVO;
import com.learn.common.vo.ApiResponse;
import com.learn.security.currentuser.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口，负责接收并校验登录、注册请求。
 *
 * <p>业务规则、密码处理和 JWT 签发均交给 Service，Controller 只负责 HTTP 层转换。</p>
 */
@Tag(name = "认证", description = "用户注册、登录和 JWT 签发")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthUserController {

    private final AuthUserService authUserService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 校验用户凭证并返回 Access Token 和公开用户信息。
     */
    @Operation(summary = "用户登录", description = "校验邮箱和密码，成功后返回 JWT Access Token")
    @PostMapping("/login")
    public ApiResponse<AuthVO> authUserLogin(@Valid @RequestBody AuthDTO authDTO) {
        AuthVO authVO = authUserService.authUserLogin(authDTO);
        return ApiResponse.success(authVO);
    }

    /**
     * 创建新用户；注册成功后直接签发 Access Token。
     */
    @Operation(summary = "用户注册", description = "创建用户并赠送初始分析额度，成功后返回 JWT")
    @PostMapping("/register")
    public ApiResponse<AuthVO> authUserRegister(
            @Valid @RequestBody RegisterRequestDTO registerRequest
    ) {
        AuthVO authVO = authUserService.authUserRegister(registerRequest);
        return ApiResponse.success(authVO);
    }

    /**
     * 根据已验证 JWT 的 subject 查询当前用户，不接收客户端提交的用户 ID 或密码。
     */
    @Operation(summary = "查询当前用户", description = "从 Bearer JWT 读取用户 UUID 并返回最新用户信息")
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserVO> authUserMe() {
        UserVO userVO = authUserService.authUserMe(currentUserProvider.getUserId());
        return ApiResponse.success(userVO);
    }
}
