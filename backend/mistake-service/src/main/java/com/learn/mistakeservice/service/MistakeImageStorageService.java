package com.learn.mistakeservice.service;

import com.learn.mistakeservice.model.PresignedImage;

/** 为 Garage 私有错题图片生成临时读取地址。 */
public interface MistakeImageStorageService {
    PresignedImage createReadUrl(String objectKey);
}
