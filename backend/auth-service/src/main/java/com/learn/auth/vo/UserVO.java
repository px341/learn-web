package com.learn.auth.vo;

import java.util.UUID;

/**
 * 当前用户的公开视图，不包含密码哈希、账号状态和 tokenVersion。
 */
public record UserVO(
        UUID id,
        String name,
        String email,
        Integer credits
) {
}
