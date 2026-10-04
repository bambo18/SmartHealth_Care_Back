package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.utils.S3Uploader;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class FileUploadServiceUT {
    private static final byte[] PNG_MAGIC =
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Mock
    private S3Uploader s3Uploader;

    @InjectMocks
    private FileUploadService fileUploadService;

    /**
     * @Value 필드는 Mockito가 주입하지 않아 기본값 0이 되고, 그러면 모든 파일이
     * 크기 검사에서 거부된다. yml 기본값(7MB)과 같은 값을 심어 운영과 같은 조건으로 둔다.
     */
    @BeforeEach
    void 상한을_주입한다() {
        ReflectionTestUtils.setField(fileUploadService, "maxImageSizeBytes", 7L * 1024 * 1024);
    }

    @Test
    void validateImageFile_ShouldThrowException_WhenFileIsNull() {
        // Test implementation here
        MockMultipartFile file = null;
        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
    }

    @Test
    void validateImageFile_ShouldThrowException_WhenFileIsEmpty() {
        // Test implementation here
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
    }

    @Test
    void validateImageFile_ShouldThrowException_WhenFileSizeExceedsLimit() {
        // 상한이 5MB 하드코딩에서 주입값 7MB로 올라갔으므로, 상한을 넘는 크기도 8MB로 올린다.
        // (6MB로 두면 크기 검사를 통과한 뒤 MIME 검사에서 거부돼 이 테스트의 의도가 사라진다.)
        byte[] largeFile = new byte[8 * 1024 * 1024]; // 8MB
        new Random().nextBytes(largeFile);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", largeFile);
        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
    }

    @Test
    void validateImageFile_ShouldThrowException_WhenFileTypeIsInvalid() {
        // Test implementation here
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "invalid content".getBytes());
        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
    }

    @Test
    void validateImageFile_ShouldThrowException_WhenFileContentIsInvalid() {
        // Test implementation here
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "invalid image content".getBytes());
        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
    }

    @Test
    void validateImageFile_ShouldPass_WhenFileIsValid() throws Exception {
        // Test implementation here
        ClassPathResource imgFile = new ClassPathResource("test-image.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", imgFile.getInputStream());
        fileUploadService.validateImageFile(file);
    }

    @Test
    void validateImageFile_주입된_상한을_사용한다() {
        ReflectionTestUtils.setField(fileUploadService, "maxImageSizeBytes", 7L * 1024 * 1024);

        // 6MB — 기존 하드코딩 5MB 상한이라면 거부되고, 주입된 7MB 상한이라면 통과한다.
        byte[] sixMegabytes = new byte[6 * 1024 * 1024];
        // PNG 매직 바이트를 앞에 둬 Tika가 image/png 로 판정하게 한다.
        System.arraycopy(PNG_MAGIC, 0, sixMegabytes, 0, PNG_MAGIC.length);

        MockMultipartFile file = new MockMultipartFile(
            "image", "cert.png", "image/png", sixMegabytes
        );

        assertDoesNotThrow(() -> fileUploadService.validateImageFile(file));
    }

    @Test
    void validateImageFile_상한을_넘기면_INVALID_IMAGE를_던진다() {
        ReflectionTestUtils.setField(fileUploadService, "maxImageSizeBytes", 1024L);

        byte[] tooBig = new byte[2048];
        System.arraycopy(PNG_MAGIC, 0, tooBig, 0, PNG_MAGIC.length);

        MockMultipartFile file = new MockMultipartFile("image", "cert.png", "image/png", tooBig);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.validateImageFile(file)
        );
        assertEquals(ErrorCode.INVALID_IMAGE, e.getErrorCode());
    }
}
