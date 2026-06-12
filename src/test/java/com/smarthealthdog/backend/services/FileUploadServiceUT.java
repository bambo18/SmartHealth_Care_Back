package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.utils.S3Uploader;

@ExtendWith(MockitoExtension.class)
public class FileUploadServiceUT {
    @Mock
    private S3Uploader s3Uploader; 

    @InjectMocks
    private FileUploadService fileUploadService;

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
        byte[] largeFile = new byte[6 * 1024 * 1024]; // 6MB
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
