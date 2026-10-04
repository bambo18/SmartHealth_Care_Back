package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.SubmissionService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
public class S3UploaderUT {

    @InjectMocks
    private S3Uploader s3Uploader;

    @Mock private S3Client s3Client;
    @Mock private UserRepository userRepository;
    @Mock private PetRepository petRepository;
    @Mock private SubmissionService submissionService;
    @Mock private Submission submission;

    @Captor private ArgumentCaptor<PutObjectRequest> putCaptor;
    @Captor private ArgumentCaptor<DeleteObjectRequest> deleteCaptor;

    private static final byte[] JPEG_BYTES =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};

    private void givenBucket() {
        ReflectionTestUtils.setField(s3Uploader, "bucket", "test-bucket");
    }

    @Test
    void storeSubmissionImage_클라이언트가_보낸_contentType을_무시하고_Tika_판정값을_저장한다() {
        givenBucket();

        // 실제 바이트는 JPEG 인데 클라이언트는 text/html 이라고 주장한다.
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, JPEG_BYTES, "evil.jpg", "text/html"
        );

        String key = s3Uploader.storeSubmissionImage(event, "health-certificates/");

        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        PutObjectRequest sent = putCaptor.getValue();

        assertEquals("image/jpeg", sent.contentType());
        assertEquals("attachment", sent.contentDisposition());
        assertTrue(key.startsWith("health-certificates/"), "접두사가 적용되어야 한다");
        assertTrue(key.endsWith(".jpg"), "확장자는 Tika 판정값에서 나와야 한다: " + key);
        assertEquals(key, sent.key());
    }

    @Test
    void storeSubmissionImage_파일명_확장자가_달라도_Tika_판정값을_따른다() {
        givenBucket();

        // 파일명은 .png 지만 바이트는 JPEG 다.
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, JPEG_BYTES, "mislabeled.png", "image/png"
        );

        String key = s3Uploader.storeSubmissionImage(event, "health-certificates/");

        assertTrue(key.endsWith(".jpg"), "파일명이 아니라 바이트 판정이 이겨야 한다: " + key);
    }

    @Test
    void storeSubmissionImage_이미지가_아니면_INVALID_IMAGE를_던지고_putObject를_호출하지_않는다() {
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, "<html>x</html>".getBytes(), "x.jpg", "image/jpeg"
        );

        assertThrows(
            InvalidRequestDataException.class,
            () -> s3Uploader.storeSubmissionImage(event, "health-certificates/")
        );

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void delete_버킷과_키로_deleteObject를_호출한다() {
        givenBucket();

        s3Uploader.delete("health-certificates/abc.jpg");

        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertEquals("test-bucket", deleteCaptor.getValue().bucket());
        assertEquals("health-certificates/abc.jpg", deleteCaptor.getValue().key());
    }

    @Test
    void delete_키가_비어있으면_아무것도_하지_않는다() {
        s3Uploader.delete(null);
        s3Uploader.delete("");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
