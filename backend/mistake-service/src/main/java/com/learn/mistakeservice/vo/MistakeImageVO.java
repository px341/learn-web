package com.learn.mistakeservice.vo;

import java.time.Instant;

/** 私有错题图片的临时读取地址；该对象不会写回数据库。 */
public record MistakeImageVO(
        String url,
        Instant expiresAt
) {
}
