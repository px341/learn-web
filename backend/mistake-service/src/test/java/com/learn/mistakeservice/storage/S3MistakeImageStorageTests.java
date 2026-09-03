package com.learn.mistakeservice.storage;

import com.learn.mistakeservice.config.MistakeStorageProperties;
import com.learn.mistakeservice.exception.MistakeStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3MistakeImageStorageTests {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final String OBJECT_KEY =
            "users/11111111-1111-1111-1111-111111111111/mistakes/"
                    + "22222222-2222-2222-2222-222222222222/original.png";

    private S3Client s3Client;
    private S3MistakeImageStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        MistakeStorageProperties properties = new MistakeStorageProperties(
                URI.create("http://localhost:3900"),
                URI.create("http://localhost:3900"),
                "garage",
                "mistake-images",
                "access-key",
                "secret-key",
                true,
                Duration.ofMinutes(15)
        );
        storage = new S3MistakeImageStorage(s3Client, presigner, properties);
    }

    @Test
    void readsPrivateImageAfterCheckingItsSize() {
        byte[] content = new byte[] {1, 2, 3, 4};
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength((long) content.length)
                        .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        content
                ));

        byte[] result = storage.get(OBJECT_KEY);

        assertThat(result).containsExactly(content);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void rejectsBlankObjectKeyWithoutCallingS3() {
        assertThatThrownBy(() -> storage.get(" "))
                .isInstanceOf(MistakeStorageException.class)
                .hasMessage("错题图片读取失败")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsOversizedObjectBeforeDownloadingIt() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(MAX_FILE_SIZE + 1)
                        .build());

        assertThatThrownBy(() -> storage.get(OBJECT_KEY))
                .isInstanceOf(MistakeStorageException.class)
                .hasMessage("错题图片读取失败")
                .hasCauseInstanceOf(MistakeStorageException.class)
                .cause()
                .hasMessage("错题图片大小不能超过10MB");

        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void rejectsObjectThatGrowsBetweenHeadAndDownload() {
        byte[] oversizedContent = new byte[(int) MAX_FILE_SIZE + 1];
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1L).build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) oversizedContent.length)
                                .build(),
                        oversizedContent
                ));

        assertThatThrownBy(() -> storage.get(OBJECT_KEY))
                .isInstanceOf(MistakeStorageException.class)
                .hasMessage("错题图片读取失败")
                .hasCauseInstanceOf(MistakeStorageException.class);
    }

    @Test
    void wrapsS3ReadFailure() {
        RuntimeException failure = new RuntimeException("Garage unavailable");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> storage.get(OBJECT_KEY))
                .isInstanceOf(MistakeStorageException.class)
                .hasMessage("错题图片读取失败")
                .cause()
                .isSameAs(failure);
    }
}
