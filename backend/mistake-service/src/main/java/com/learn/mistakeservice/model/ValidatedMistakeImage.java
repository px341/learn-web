package com.learn.mistakeservice.model;

/** 已通过文件签名、大小和元数据清洗的错题图片。 */
public record ValidatedMistakeImage(
        byte[] content,
        String originalName,
        String contentType,
        String extension,
        long size,
        String sha256
) {
    public ValidatedMistakeImage {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
