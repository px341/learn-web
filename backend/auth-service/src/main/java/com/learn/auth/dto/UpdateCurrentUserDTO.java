package com.learn.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 当前用户资料修改请求；两个字段均为可选，但至少需要提供一个。
 */
public record UpdateCurrentUserDTO(
        @Size(max = 30, message = "名称不能超过 30 个字符")
        String name,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        String email
) {
}
