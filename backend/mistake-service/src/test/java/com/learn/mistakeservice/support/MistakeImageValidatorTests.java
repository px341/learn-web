package com.learn.mistakeservice.support;

import com.learn.mistakeservice.exception.InvalidMistakeImageException;
import com.learn.mistakeservice.model.ValidatedMistakeImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MistakeImageValidatorTests {

    private final MistakeImageValidator validator = new MistakeImageValidator();

    @Test
    void detectsPngFromFileSignatureInsteadOfClientMetadata() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "image", "fake.exe", "application/octet-stream", png
        );

        ValidatedMistakeImage result = validator.validate(file);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.sha256()).hasSize(64);
    }

    @Test
    void rejectsSpoofedImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "fake.png", "image/png", "not an image".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidMistakeImageException.class)
                .hasMessage("仅支持 PNG、JPEG 或 WEBP 图片");
    }
}
