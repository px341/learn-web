package com.learn.auth.service;

/** 当前用户头像所使用的私有对象存储。 */
public interface AvatarStorageService {

    void put(String bucket, String objectKey, String contentType, byte[] content);

    void delete(String bucket, String objectKey);

    String createReadUrl(String bucket, String objectKey);
}
