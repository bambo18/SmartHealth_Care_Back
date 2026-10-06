package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.SubmissionService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * 저장형 XSS 회귀 테스트.
 *
 * 서명 URL 은 "누가 접근하는지"만 제한할 뿐 "브라우저가 그 바이트를 무엇으로 해석하는지"는
 * 제한하지 않는다. 클라이언트가 보낸 파트 헤더를 그대로 저장하면, 유효한 JPEG 안에 스크립트를
 * 심은 폴리글롯 파일이 text/html 로 저장돼 서명 URL 로도 막히지 않는 저장형 XSS 가 성립한다.
 *
 * 기존 거부 경로 테스트로는 잡히지 않으므로 여기서 따로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
public class S3UploaderUT {

    @Mock
    private S3Client s3Client;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private SubmissionService submissionService;

    @InjectMocks
    private S3Uploader s3Uploader;

    private byte[] jpegBytes;
    private byte[] pngBytes;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(s3Uploader, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Uploader, "region", "ap-northeast-2");

        jpegBytes = renderImage("jpg");
        pngBytes = renderImage("png");
    }

    @Test
    void storeSubmissionImage_ShouldUseTikaContentType_WhenPartHeaderLies() throws IOException {
        // 파트 헤더는 text/html 이라고 주장하지만 실제 바이트는 JPEG 이다.
        SubmissionImageUploadEvent event =
            new SubmissionImageUploadEvent(null, jpegBytes, "x.jpg", "text/html");

        s3Uploader.storeSubmissionImage(event, "health-certificates/");

        PutObjectRequest request = capturePutRequest();

        assertEquals("image/jpeg", request.contentType());
        assertEquals("attachment", request.contentDisposition());
        assertEquals(ServerSideEncryption.AES256, request.serverSideEncryption());
    }

    @Test
    void storeSubmissionImage_ShouldDeriveExtensionFromTika_WhenFilenameLies() throws IOException {
        // 파일명은 .jpg 이지만 실제 바이트는 PNG 다.
        SubmissionImageUploadEvent event =
            new SubmissionImageUploadEvent(null, pngBytes, "x.jpg", "image/jpeg");

        String key = s3Uploader.storeSubmissionImage(event, "health-certificates/");

        assertTrue(key.endsWith(".png"), "Tika 판정 기준으로 확장자가 정해져야 한다: " + key);
        assertTrue(key.startsWith("health-certificates/"), "접두사가 유지되어야 한다: " + key);
        assertEquals("image/png", capturePutRequest().contentType());
    }

    @Test
    void uploadProfilePicture_ShouldApplyTheSameProtections() throws IOException {
        User user = new User();
        when(userRepository.save(any(User.class))).thenReturn(user);

        // 프로필 사진 경로에도 동일한 결함이 있었다. 세 경로를 모두 고쳤는지 확인한다.
        s3Uploader.uploadProfilePicture(
            new UserProfilePictureUploadEvent(user, pngBytes, "avatar.jpg", "text/html")
        );

        PutObjectRequest request = capturePutRequest();

        assertEquals("image/png", request.contentType());
        assertEquals("attachment", request.contentDisposition());
        assertTrue(request.key().startsWith("profiles/"));
        assertTrue(request.key().endsWith(".png"));
    }

    @Test
    void uploadPetImage_ShouldApplyTheSameProtections() throws IOException {
        // Pet 의 기본 생성자는 protected 라 테스트에서 직접 만들 수 없다.
        Pet pet = mock(Pet.class);
        when(petRepository.save(any(Pet.class))).thenReturn(pet);

        s3Uploader.uploadPetImage(
            new PetPictureUploadEvent(pet, jpegBytes, "pet.png", "text/html")
        );

        PutObjectRequest request = capturePutRequest();

        assertEquals("image/jpeg", request.contentType());
        assertEquals("attachment", request.contentDisposition());
        assertTrue(request.key().startsWith("pets/"));
        assertTrue(request.key().endsWith(".jpg"));
    }

    @Test
    void storeSubmissionImage_ShouldReject_WhenBytesAreNotAnAllowedImage() {
        SubmissionImageUploadEvent event =
            new SubmissionImageUploadEvent(null, "<html>not an image</html>".getBytes(), "x.jpg", "image/jpeg");

        assertThrows(
            InvalidRequestDataException.class,
            () -> s3Uploader.storeSubmissionImage(event, "health-certificates/")
        );
    }

    @Test
    void delete_ShouldCallS3_WhenKeyIsPresent() {
        s3Uploader.delete("health-certificates/abc.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("health-certificates/abc.jpg", captor.getValue().key());
    }

    @Test
    void uploadSubmissionImage_ShouldStoreUnderDiagnosesPrefix_SoExistingFlowIsUnchanged() throws IOException {
        Submission submission = Submission.builder()
            .id(UUID.randomUUID())
            .photoUrl("")
            .build();

        s3Uploader.uploadSubmissionImage(
            new SubmissionImageUploadEvent(submission, jpegBytes, "eye.jpg", "image/jpeg")
        );

        // 눈·소변 비동기 업로드 동작은 리팩터링 이후에도 그대로여야 한다.
        assertTrue(capturePutRequest().key().startsWith("diagnoses/"));
        verify(submissionService).saveSubmission(submission);
    }

    private PutObjectRequest capturePutRequest() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        return captor.getValue();
    }

    /** Tika 가 실제로 판정할 수 있는 최소 이미지 바이트를 만든다. */
    private byte[] renderImage(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);

        return out.toByteArray();
    }
}
