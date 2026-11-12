package com.smarthealthdog.backend.services;

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

@ExtendWith(MockitoExtension.class)
public class FileUploadServiceUT {
    @Mock
    private S3Uploader s3Uploader; 

    @InjectMocks
    private FileUploadService fileUploadService;

    /** 상한의 단일 출처는 yml 이지만 순수 단위 테스트에는 @Value 가 주입되지 않는다. */
    private static final long MAX_FILE_SIZE_BYTES = 7L * 1024 * 1024; // 7MB

    @BeforeEach
    void setUpMaxFileSize() {
        ReflectionTestUtils.setField(fileUploadService, "maxFileSize", MAX_FILE_SIZE_BYTES);
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
        // Test implementation here
        byte[] largeFile = new byte[8 * 1024 * 1024]; // 8MB — yml 상한(7MB) 초과
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
}
