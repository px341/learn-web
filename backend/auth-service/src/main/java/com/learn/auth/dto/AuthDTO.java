package com.learn.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>password 是本次认证使用的明文密码，只允许在请求处理内存中短暂存在，
 * 不得写入日志、缓存或数据库。</p>
 */
public record AuthDTO(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须为 6～64 个字符")
        String password
) {
}
