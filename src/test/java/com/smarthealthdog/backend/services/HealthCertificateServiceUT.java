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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
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
    private HealthCertificateOcrClient healthCertificateOcrClient;

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

    // ───────────────────────── 거부 경로 ─────────────────────────

    @Test
    void register_ShouldRejectAndStoreNothing_WhenConfidenceIsBelowThreshold() {
        givenOwnedPet();
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("진단서\n병명: 심장비대", 0.60));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(true);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldRejectAndStoreNothing_WhenDiseaseNameIsMissing() {
        givenOwnedPet();
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("진단서", 0.95));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(true);
        when(fieldExtractor.extract(any())).thenReturn(
            PetHealthCertificate.builder()
                .diseaseName(null)
                .diagnosedDateText("2020.10.28")
                .ocrConfidence(BigDecimal.valueOf(0.9500))
                .build()
        );

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldRejectAndStoreNothing_WhenDiagnosedDateIsMissing() {
        givenOwnedPet();
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("진단서", 0.95));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(true);
        when(fieldExtractor.extract(any())).thenReturn(
            PetHealthCertificate.builder()
                .diseaseName("심장비대")
                .diagnosedDateText("   ")
                .ocrConfidence(BigDecimal.valueOf(0.9500))
                .build()
        );

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldRejectAndStoreNothing_WhenImageIsNotAHealthCertificate() {
        givenOwnedPet();
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("고양이 사진에 적힌 알 수 없는 글자", 0.98));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(false);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.NOT_A_HEALTH_CERTIFICATE, e.getErrorCode());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldRejectAndStoreNothing_WhenOcrServiceTimesOut() {
        givenOwnedPet();
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenThrow(new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE));

        ServiceUnavailableException e = assertThrows(
            ServiceUnavailableException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_SERVICE_UNAVAILABLE, e.getErrorCode());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldReturn404AndNotCallOcr_WhenPetBelongsToSomeoneElse() {
        Pet otherOwnersPet = petOwnedBy(999L);
        when(petService.get(PET_ID)).thenReturn(otherOwnersPet);

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        // 유료 외부 호출은 소유권 검증을 통과한 뒤에만 일어나야 한다.
        verify(healthCertificateOcrClient, never()).extract(any(), anyString());
        verifyNothingWasStored();
    }

    @Test
    void register_ShouldNotCallOcr_WhenRequestIsTooFrequent() {
        givenOwnedPetWithoutOcrStubs();
        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(OWNER_ID, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());

        // 빈도 제한이 비용보다 앞에 있어야 의미가 있다.
        verify(healthCertificateOcrClient, never()).extract(any(), anyString());
        verify(fileUploadService, never()).validateImageFile(any());
        verifyNothingWasStored();
    }

    // ───────────────────────── 보상 삭제 ─────────────────────────

    @Test
    void register_ShouldDeleteStoredImage_WhenDatabaseWriteFails() throws Exception {
        givenOwnedPet();
        givenValidOcrResult();

        when(imageUploader.storeSubmissionImage(any(SubmissionImageUploadEvent.class), eq("health-certificates/")))
            .thenReturn(STORED_KEY);
        when(transactionTemplate.execute(any())).thenThrow(new RuntimeException("DB down"));

        assertThrows(
            InternalServerErrorException.class,
            () -> healthCertificateService.register(imageFile, PET_ID, OWNER_ID)
        );

        verify(imageUploader).delete(STORED_KEY);
    }

    // ───────────────────────── 성공 경로 ─────────────────────────

    @Test
    void register_ShouldSucceedWithNullDate_WhenDateParsingFails() throws Exception {
        Pet pet = givenOwnedPet();

        // 날짜 원문은 있지만 파싱에 실패해 DATE 컬럼이 null 인 상태
        PetHealthCertificate extracted = PetHealthCertificate.builder()
            .diseaseName("심장비대, 폐침윤")
            .diagnosedDateText("202O.1O.28")
            .diagnosedDate(null)
            .ocrConfidence(BigDecimal.valueOf(0.9124))
            .build();

        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("진단서\n병명: 심장비대, 폐침윤", 0.9124));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(true);
        when(fieldExtractor.extract(any())).thenReturn(extracted);
        when(imageUploader.storeSubmissionImage(any(SubmissionImageUploadEvent.class), eq("health-certificates/")))
            .thenReturn(STORED_KEY);

        Submission submission = submissionOf(pet);
        when(submissionService.createCompletedSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE, STORED_KEY))
            .thenReturn(submission);
        when(petHealthCertificateRepository.save(any(PetHealthCertificate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        givenTransactionTemplateRunsCallback();

        HealthCertificateResult result = healthCertificateService.register(imageFile, PET_ID, OWNER_ID);

        assertNull(result.diagnosedDate());
        assertEquals("202O.1O.28", result.diagnosedDateText());
        assertEquals(submission.getId(), result.submissionId());
        assertEquals(PET_ID, result.petId());
        verify(imageUploader, never()).delete(anyString());
    }

    @Test
    void register_ShouldStoreUnderHealthCertificatePrefix_WhenRecognitionSucceeds() throws Exception {
        Pet pet = givenOwnedPet();
        givenValidOcrResult();

        when(imageUploader.storeSubmissionImage(any(SubmissionImageUploadEvent.class), anyString()))
            .thenReturn(STORED_KEY);
        when(submissionService.createCompletedSubmission(any(), any(), anyString()))
            .thenReturn(submissionOf(pet));
        when(petHealthCertificateRepository.save(any(PetHealthCertificate.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        givenTransactionTemplateRunsCallback();

        healthCertificateService.register(imageFile, PET_ID, OWNER_ID);

        ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);
        verify(imageUploader).storeSubmissionImage(any(SubmissionImageUploadEvent.class), prefixCaptor.capture());

        // 접두사 단위로 접근 정책을 따로 걸 수 있어야 하므로 기존 diagnoses/ 와 섞지 않는다.
        assertEquals("health-certificates/", prefixCaptor.getValue());
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

    private void givenValidOcrResult() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("진단서\n병명: 심장비대", 0.9124));
        when(fieldExtractor.looksLikeHealthCertificate(anyString())).thenReturn(true);
        when(fieldExtractor.extract(any())).thenReturn(
            PetHealthCertificate.builder()
                .diseaseName("심장비대, 폐침윤")
                .diagnosedDateText("2020.10.28")
                .diagnosedDate(LocalDate.of(2020, 10, 28))
                .ocrConfidence(BigDecimal.valueOf(0.9124))
                .build()
        );
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionTemplateRunsCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
            ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null)
        );
    }

    /** DB 행도 S3 객체도 만들어지지 않았는지 확인한다. 이 기능의 핵심 검증이다. */
    private void verifyNothingWasStored() {
        try {
            verify(imageUploader, never()).storeSubmissionImage(any(), anyString());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        verify(submissionService, never()).createCompletedSubmission(any(), any(), anyString());
        verify(submissionService, never()).saveSubmission(any());
        verify(petHealthCertificateRepository, never()).save(any());
    }
}
