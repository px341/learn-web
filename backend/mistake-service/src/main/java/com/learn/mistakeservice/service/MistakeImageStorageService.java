package com.learn.mistakeservice.service;

import com.learn.mistakeservice.model.PresignedImage;

/** Garage 私有错题图片的统一存储接口。 */
public interface MistakeImageStorageService {
    void put(String objectKey, String contentType, byte[] content);

    void delete(String objectKey);

    PresignedImage createReadUrl(String objectKey);
}
