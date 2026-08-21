package com.learn.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求。
 *
 * <p>Service 会再次检查两次密码是否一致，并只将 BCrypt 哈希写入数据库。</p>
 */
public record RegisterRequestDTO(
        @NotBlank(message = "名称不能为空")
        @Size(max = 30, message = "名称不能超过 30 个字符")
        String name,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须为 6～64 个字符")
        String password,

        @NotBlank(message = "请再次输入密码")
        String passwordConfirmation
) {
}
