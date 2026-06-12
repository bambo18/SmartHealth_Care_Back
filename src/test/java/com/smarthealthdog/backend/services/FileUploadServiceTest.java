package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.utils.ImageUploader;

@SpringBootTest
@ActiveProfiles("test")
public class FileUploadServiceTest {
    @MockitoBean
    private ImageUploader imageUploader;

    @Autowired
    private FileUploadService fileUploadService;

    @Test
    void uploadProfilePicture_ShouldThrowException_WhenFileIsNull() {
        MockMultipartFile file = new MockMultipartFile("file", (byte[]) null);

        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.uploadProfilePicture(null, file)
        );
    }

    @Test
    void uploadProfilePicture_ShouldThrowException_WhenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.uploadProfilePicture(null, file)
        );
    }

    @Test
    void uploadProfilePicture_ShouldThrowException_WhenFileSizeExceedsLimit() {
        byte[] largeFile = new byte[6 * 1024 * 1024]; // 6MB file
        new Random().nextBytes(largeFile);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", largeFile);

        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.uploadProfilePicture(null, file)
        );
    }

    @Test
    void uploadProfilePicture_ShouldThrowException_WhenFileTypeIsInvalid() {
        byte[] invalidFileContent = "This is not an image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", invalidFileContent);

        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.uploadProfilePicture(null, file)
        );
    }

    @Test
    void uploadProfilePicture_ShouldThrowException_WhenFileContentIsInvalid() {
        byte[] invalidImageContent = "invalid image content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", invalidImageContent);

        assertThrows(
            InvalidRequestDataException.class,
            () -> fileUploadService.uploadProfilePicture(null, file)
        );
    }

    @Test
    void uploadProfilePicture_ShouldPass_WhenFileIsValid() throws Exception {
        ClassPathResource imgFile = new ClassPathResource("test-image.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", imgFile.getInputStream());

        fileUploadService.uploadProfilePicture(null, file);
    }
}
