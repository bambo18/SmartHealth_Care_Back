package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HealthCertificateServiceUT {

    private static final Long PET_ID = 3L;
    private static final Long OWNER_ID = 7L;

    @Mock
    private PetService petService;

    @Mock
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private HealthCertificateOcrClient ocrClient;

    @Mock
    private ImageUploader imageUploader;

    @Mock
    private HealthCertificateWriter healthCertificateWriter;

    @InjectMocks
    private HealthCertificateService service;

    private Pet pet;
    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minConfidence", 0.75);

        // Pet 의 기본 생성자가 PROTECTED 라 기존 UT 들과 같이 목을 쓴다.
        User owner = User.builder().build();
        owner.setId(OWNER_ID);

        pet = mock(Pet.class);
        when(pet.getId()).thenReturn(PET_ID);
        when(pet.getOwner()).thenReturn(owner);

        image = new MockMultipartFile("image", "cert.jpg", "image/jpeg", jpegBytes());

        when(petService.get(PET_ID)).thenReturn(pet);
    }

    /** Tika 가 image/jpeg 로 판정하는 최소 바이트 (JFIF 헤더). */
    private static byte[] jpegBytes() {
        byte[] bytes = new byte[64];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        bytes[4] = 0x00;
        bytes[5] = 0x10;
        byte[] jfif = "JFIF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(jfif, 0, bytes, 6, jfif.length);
        return bytes;
    }

    private static HealthCertificateOcrResult ocrResult(
            String diseaseName, String diagnosedDateText, double confidence, boolean keywordFound) {
        return new HealthCertificateOcrResult(
            "초코", "Canine", "Pug", "Castrated Male", "흰색", "16년 3개월", null,
            diseaseName,
            LocalDate.of(2020, 10, 28), "2020.10.28",
            LocalDate.of(2020, 10, 28), diagnosedDateText,
            "예후 소견", "비고",
            confidence, keywordFound
        );
    }

    private static HealthCertificateOcrResult 정상결과() {
        return ocrResult("심장비대", "2020.10.28", 0.91, true);
    }

    /** 실패 시 미저장 보증: S3 업로드도 DB 저장도 일어나지 않았다. */
    private void 아무것도_저장되지_않았다() {
        verify(imageUploader, never()).storeSubmissionImage(any(), anyString());
        verifyNoInteractions(healthCertificateWriter);
    }

    @Test
    void 정상_흐름은_S3_업로드_뒤_저장하고_기록을_돌려준다() {
        PetHealthCertificate saved = new PetHealthCertificate();
        when(ocrClient.extract(any(), eq("image/jpeg"))).thenReturn(정상결과());
        when(imageUploader.storeSubmissionImage(any(), eq("health-certificates/")))
            .thenReturn("health-certificates/abc.jpg");
        when(healthCertificateWriter.persist(eq(pet), eq("health-certificates/abc.jpg"), any()))
            .thenReturn(saved);

        PetHealthCertificate result = service.register(image, PET_ID, OWNER_ID);

        assertSame(saved, result);
    }

    @Test
    void 빈도_제한은_OCR_호출보다_먼저_실행된다() {
        // 순서가 뒤바뀌면 제한이 유료 호출 비용을 막지 못한다.
        when(ocrClient.extract(any(), anyString())).thenReturn(정상결과());
        when(imageUploader.storeSubmissionImage(any(), anyString())).thenReturn("key");

        service.register(image, PET_ID, OWNER_ID);

        InOrder order = inOrder(diagnosisAttemptLimiter, ocrClient);
        order.verify(diagnosisAttemptLimiter)
             .checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);
        order.verify(ocrClient).extract(any(), anyString());
    }

    @Test
    void 빈도_제한에_걸리면_OCR을_호출하지_않는다() {
        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verifyNoInteractions(ocrClient);
        아무것도_저장되지_않았다();
    }

    @Test
    void 타인의_반려동물이면_404이고_빈도_제한도_기록하지_않는다() {
        ResourceNotFoundException e = assertThrows(
            ResourceNotFoundException.class,
            () -> service.register(image, PET_ID, OWNER_ID + 1)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
        verifyNoInteractions(diagnosisAttemptLimiter, ocrClient);
        아무것도_저장되지_않았다();
    }

    @Test
    void 이미지_검증에_실패하면_OCR을_호출하지_않는다() {
        doThrow(new InvalidRequestDataException(ErrorCode.INVALID_IMAGE))
            .when(fileUploadService).validateImageFile(image);

        assertThrows(InvalidRequestDataException.class,
                     () -> service.register(image, PET_ID, OWNER_ID));

        verifyNoInteractions(ocrClient);
        아무것도_저장되지_않았다();
    }

    @Test
    void 진단서_키워드가_없으면_NOT_A_HEALTH_CERTIFICATE로_거부하고_아무것도_저장하지_않는다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(ocrResult("심장비대", "2020.10.28", 0.91, false));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.NOT_A_HEALTH_CERTIFICATE, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 병명이_비면_OCR_REQUIRED_FIELD_MISSING로_거부한다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(ocrResult(null, "2020.10.28", 0.91, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 진단_연원일_원문이_비면_OCR_REQUIRED_FIELD_MISSING로_거부한다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(ocrResult("심장비대", "  ", 0.91, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 신뢰도가_임계값_미달이면_OCR_RECOGNITION_FAILED로_거부한다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(ocrResult("심장비대", "2020.10.28", 0.74, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 신뢰도가_임계값과_같으면_통과시킨다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(ocrResult("심장비대", "2020.10.28", 0.75, true));
        when(imageUploader.storeSubmissionImage(any(), anyString())).thenReturn("key");

        service.register(image, PET_ID, OWNER_ID);

        verify(healthCertificateWriter).persist(eq(pet), eq("key"), any());
    }

    @Test
    void 날짜_파싱_실패는_등록을_막지_않는다() {
        // 원문이 있으면 등록하고 DATE 컬럼만 null 로 둔다.
        HealthCertificateOcrResult 파싱실패 = new HealthCertificateOcrResult(
            "초코", null, null, null, null, null, null,
            "심장비대",
            null, "20Z0.1O.28",
            null, "20Z0.1O.28",
            null, null,
            0.91, true
        );
        when(ocrClient.extract(any(), anyString())).thenReturn(파싱실패);
        when(imageUploader.storeSubmissionImage(any(), anyString())).thenReturn("key");

        service.register(image, PET_ID, OWNER_ID);

        verify(healthCertificateWriter).persist(eq(pet), eq("key"), eq(파싱실패));
    }

    @Test
    void S3_업로드는_health_certificates_접두사를_쓴다() {
        when(ocrClient.extract(any(), anyString())).thenReturn(정상결과());
        when(imageUploader.storeSubmissionImage(any(), anyString())).thenReturn("key");

        service.register(image, PET_ID, OWNER_ID);

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SubmissionImageUploadEvent> event =
            ArgumentCaptor.forClass(SubmissionImageUploadEvent.class);

        verify(imageUploader).storeSubmissionImage(event.capture(), prefix.capture());

        assertEquals("health-certificates/", prefix.getValue());
        // 동기 경로는 업로드가 제출 생성보다 앞서므로 submission 이 아직 없다.
        assertEquals(null, event.getValue().submission());
        // 저장 contentType 은 Tika 판정값이다 — 클라이언트 헤더가 아니다.
        assertEquals("image/jpeg", event.getValue().contentType());
    }

    @Test
    void DB_저장이_실패하면_S3_객체를_보상_삭제한다() {
        when(ocrClient.extract(any(), anyString())).thenReturn(정상결과());
        when(imageUploader.storeSubmissionImage(any(), anyString()))
            .thenReturn("health-certificates/orphan.jpg");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenThrow(new IllegalStateException("DB 실패"));

        assertThrows(IllegalStateException.class,
                     () -> service.register(image, PET_ID, OWNER_ID));

        verify(imageUploader).delete("health-certificates/orphan.jpg");
    }

    @Test
    void 보상_삭제가_실패해도_원래_예외를_그대로_올린다() {
        when(ocrClient.extract(any(), anyString())).thenReturn(정상결과());
        when(imageUploader.storeSubmissionImage(any(), anyString())).thenReturn("key");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenThrow(new IllegalStateException("DB 실패"));
        doThrow(new RuntimeException("S3 삭제 실패")).when(imageUploader).delete("key");

        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> service.register(image, PET_ID, OWNER_ID)
        );

        assertEquals("DB 실패", e.getMessage());
    }

    @Test
    void petId나_ownerId가_null이면_IllegalArgumentException이다() {
        assertThrows(IllegalArgumentException.class, () -> service.register(image, null, OWNER_ID));
        assertThrows(IllegalArgumentException.class, () -> service.register(image, PET_ID, null));
    }
}
