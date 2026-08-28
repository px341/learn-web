package com.learn.mistakeservice.support;

import com.learn.mistakeservice.exception.InvalidMistakeImageException;
import com.learn.mistakeservice.exception.MistakeImageTooLargeException;
import com.learn.mistakeservice.model.ValidatedMistakeImage;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 使用文件签名而不是客户端 MIME 或扩展名校验错题图片。 */
@Component
public class MistakeImageValidator {

    static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    public ValidatedMistakeImage validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new MistakeImageTooLargeException();
        }
        byte[] content;
        try {
            content = image.getBytes();
        } catch (IOException exception) {
            throw new InvalidMistakeImageException("错题图片无法读取", exception);
        }
        ImageFormat format = detectFormat(content);
        return new ValidatedMistakeImage(
                content,
                normalizeOriginalName(image.getOriginalFilename(), format.extension()),
                format.contentType(),
                format.extension(),
                content.length,
                sha256(content)
        );
    }

    private ImageFormat detectFormat(byte[] content) {
        if (hasPrefix(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new ImageFormat("image/png", "png");
        }
        if (hasPrefix(content, 0xFF, 0xD8, 0xFF)) {
            return new ImageFormat("image/jpeg", "jpg");
        }
        if (content.length >= 12
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP")) {
            return new ImageFormat("image/webp", "webp");
        }
        throw new InvalidMistakeImageException("仅支持 PNG、JPEG 或 WEBP 图片");
    }

    private boolean hasPrefix(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (Byte.toUnsignedInt(content[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] content, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeOriginalName(String originalName, String extension) {
        String normalized = originalName == null ? "" : originalName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.replace("\0", "").trim();
        if (normalized.isEmpty()) {
            normalized = "mistake." + extension;
        }
        return normalized.length() <= MAX_ORIGINAL_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ORIGINAL_NAME_LENGTH);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ImageFormat(String contentType, String extension) {
    }
}
