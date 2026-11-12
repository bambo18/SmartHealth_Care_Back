package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateFieldExtractor;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.CertificateSubmissionAccepted;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * 이 기능의 핵심 테스트는 거부 경로다.
 *
 * "저장 없음"을 응답 코드가 아니라 mock 호출 횟수(verify never)로 검증한다 —
 * 인식 실패 시 DB 행도 S3 객체도 만들지 않는다는 정책이 실제로 지켜지는지는
 * 그렇게만 확인할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
public class HealthCertificateServiceUT {

    @Mock
    private PetService petService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Mock
    private HealthCertificateFieldExtractor fieldExtractor;

    @Mock
    private PetHealthCertificateRepository petHealthCertificateRepository;

    @Mock
    private ImageUploader imageUploader;

    @Mock
    private ImgUtils imgUtils;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private HealthCertificateService healthCertificateService;

    private static final Long OWNER_ID = 1L;
    private static final Long PET_ID = 10L;
    private static final String STORED_KEY = "health-certificates/abc.jpg";

    private MockMultipartFile imageFile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthCertificateService, "minConfidence", 0.75);
        ReflectionTestUtils.setField(healthCertificateService, "presignedUrlExpirationSeconds", 300L);

        imageFile = new MockMultipartFile("image", "certificate.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());
    }

    // ───────────────────────── 접수 경로 ─────────────────────────

    @Test
    void register_ShouldCreatePendingSubmissionAndPublishUpload() {
        Pet pet = givenOwnedPet();
        Submission pending = pendingSubmissionOf(pet);

        when(submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(pending);
        when(submissionService.saveSubmission(pending)).thenReturn(pending);

        CertificateSubmissionAccepted accepted =
            healthCertificateService.register(imageFile, PET_ID, OWNER_ID);

        assertEquals(pending.getId(), accepted.submissionId());
        assertEquals(PET_ID, accepted.petId());
        assertEquals(SubmissionStatus.PENDING, accepted.status());

        // 업로드는 커밋 이후 비동기로 일어난다 — 이 메서드는 이벤트 발행까지만 한다.
        verify(fileUploadService).updateDiagnosisImage(pending, imageFile);

        // 백엔드가 Vision 을 직접 부르지 않는다. 추출은 워커 몫이다 — 벤더 클라이언트가
        // 아예 없으므로 호출 가능성 자체가 구조로 막혀 있다. 여기서는 등록 시점에
        // 진단서 행이 생기지 않는다는 것만 확인한다.
        verify(petHealthCertificateRepository, never()).save(any());
    }

    // ───────────────────────── 거부 경로 ─────────────────────────

    @Test
    void register_ShouldReturn404AndNotCreateSubmission_WhenPetBelongsToSomeoneElse() {
        Pet otherOwnersPet = petOwnedBy(999L);
        when(petService.get(PET_ID)).thenReturn(otherOwnersPet);

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        verifyNothingWasStored();
    }

    @Test
    void register_ShouldNotTouchImage_WhenRequestIsTooFrequent() {
        givenOwnedPetWithoutOcrStubs();
        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(OWNER_ID, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());

        // 빈도 제한이 제출 생성·업로드보다 앞에 있어야 의미가 있다.
        verify(fileUploadService, never()).updateDiagnosisImage(any(), any());
        verifyNothingWasStored();
    }

    // ───────────────────────── 이미지 URL ─────────────────────────

    @Test
    void getImageUrl_ShouldUseSecureUrl_NeverThePlainOne() {
        Submission submission = submissionOf(petOwnedBy(OWNER_ID));
        submission.setPhotoUrl(STORED_KEY);

        UUID submissionId = submission.getId();
        when(submissionService.getSubmissionByIdAndOwnerId(submissionId, OWNER_ID)).thenReturn(submission);
        when(imgUtils.getSecureImgUrl(eq(STORED_KEY), any())).thenReturn("https://signed-url");

        var response = healthCertificateService.getImageUrl(submissionId, OWNER_ID);

        assertEquals("https://signed-url", response.imageUrl());
        assertEquals(300L, response.expiresIn());

        // getImgUrl 은 prod 에서 무서명 CloudFront URL 을 돌려줄 수 있어 절대 쓰면 안 된다.
        verify(imgUtils, never()).getImgUrl(anyString());
    }

    @Test
    void getImageUrl_ShouldReturn404_WhenSubmissionIsDeleted() {
        Submission submission = submissionOf(petOwnedBy(OWNER_ID));
        submission.setPhotoUrl(STORED_KEY);
        submission.setStatus(SubmissionStatus.DELETED);

        UUID submissionId = submission.getId();
        when(submissionService.getSubmissionByIdAndOwnerId(submissionId, OWNER_ID)).thenReturn(submission);

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.getImageUrl(submissionId, OWNER_ID)
        );
    }

    // ───────────────────────── 헬퍼 ─────────────────────────

    private Pet givenOwnedPet() {
        Pet pet = petOwnedBy(OWNER_ID);
        when(petService.get(PET_ID)).thenReturn(pet);
        return pet;
    }

    private void givenOwnedPetWithoutOcrStubs() {
        Pet pet = petOwnedBy(OWNER_ID);
        when(petService.get(PET_ID)).thenReturn(pet);
    }

    private Pet petOwnedBy(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);

        // Pet 의 기본 생성자는 protected 라 테스트에서 직접 만들 수 없다.
        Pet pet = mock(Pet.class);
        lenient().when(pet.getId()).thenReturn(PET_ID);
        lenient().when(pet.getOwner()).thenReturn(owner);

        return pet;
    }

    /** 업로드·추출까지 끝난 상태. 조회 경로 테스트에서 쓴다. */
    private Submission submissionOf(Pet pet) {
        return Submission.builder()
            .id(UUID.randomUUID())
            .pet(pet)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .photoUrl(STORED_KEY)
            .status(SubmissionStatus.COMPLETED)
            .submittedAt(Instant.parse("2026-10-03T12:00:00Z"))
            .completedAt(Instant.parse("2026-10-03T12:00:04Z"))
            .build();
    }

    /** 접수 직후 상태 — photoUrl 은 비동기 업로드가 끝나야 채워진다. */
    private Submission pendingSubmissionOf(Pet pet) {
        return Submission.builder()
            .id(UUID.randomUUID())
            .pet(pet)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .photoUrl("")
            .status(SubmissionStatus.PENDING)
            .submittedAt(Instant.parse("2026-10-03T12:00:00Z"))
            .build();
    }

    /** 거부된 요청이 아무 흔적도 남기지 않았는지 확인한다. */
    private void verifyNothingWasStored() {
        try {
            verify(imageUploader, never()).storeSubmissionImage(any(), anyString());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        verify(submissionService, never()).createSubmission(any(), any());
        verify(submissionService, never()).saveSubmission(any());
        verify(petHealthCertificateRepository, never()).save(any());
    }
}
