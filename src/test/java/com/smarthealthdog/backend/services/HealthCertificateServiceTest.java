package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.CreatePetRequest;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionDetail;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class HealthCertificateServiceTest {

    /** 실제 Vision 호출은 과금되므로 통합 테스트에서도 외부 클라이언트는 대체한다. */
    @MockitoBean
    private HealthCertificateOcrClient healthCertificateOcrClient;

    @Autowired
    private HealthCertificateService healthCertificateService;

    @Autowired
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private PetService petService;

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PetHealthCertificateRepository petHealthCertificateRepository;

    @Autowired
    private DiagnosisAttemptRepository diagnosisAttemptRepository;

    private static final String CERTIFICATE_TEXT = String.join("\n",
        "진 단 서",
        "동물명: 초코",
        "종류: Canine",
        "품종: Pug",
        "성별: Castrated Male",
        "모색: 흰색",
        "연령: 16년 3개월",
        "병명: 심장비대, 폐침윤",
        "발병 연월일: 2020.10.28",
        "진단 연월일: 2020.10.28",
        "예후 소견: 폐수종 의심됩니다.",
        "그외의 사항: 진단서 발급 원합니다."
    );

    private Long userId;
    private Long petId;

    @BeforeAll
    void setup() {
        Permission viewOwnProfilePermission = new Permission();
        viewOwnProfilePermission.setName(PermissionEnum.CAN_VIEW_OWN_PROFILE);
        viewOwnProfilePermission.setDescription("자신의 프로필 보기");
        permissionRepository.save(viewOwnProfilePermission);

        Role role = new Role();
        role.setName(RoleEnum.USER);
        role.setDescription("Standard user role");
        role.setPermissions(new java.util.HashSet<>());
        roleRepository.save(role);

        role.getPermissions().add(viewOwnProfilePermission);
        roleRepository.save(role);

        userService.createUser("certuser", "cert@email.com", "Password1!");
        User user = userService.getUserByEmail("cert@email.com").orElseThrow();
        userId = user.getId();

        CreatePetRequest petRequest = new CreatePetRequest(
            "초코",
            PetSpecies.DOG,
            "Pug",
            PetGender.MALE,
            LocalDate.of(2020, 1, 1),
            true,
            BigDecimal.valueOf(5.5)
        );

        try {
            petService.create(userId, petRequest, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Pet pet = petService.listByOwner(userId).stream().findFirst().orElseThrow();
        petId = pet.getId();
    }

    @BeforeEach
    void resetStateBetweenTests() {
        // 빈도 제한은 사용자 단위라 테스트끼리 간섭한다. 각 테스트는 깨끗한 상태에서 시작한다.
        diagnosisAttemptRepository.deleteAll();
        petHealthCertificateRepository.deleteAll();
        submissionRepository.deleteAll();

        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "userIntervalSeconds", 30);
    }

    @AfterAll
    void cleanup() {
        diagnosisAttemptRepository.deleteAll();
        petHealthCertificateRepository.deleteAll();
        submissionRepository.deleteAll();
        petRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    // ───────────────────────── 등록 ─────────────────────────

    @Test
    void register_ShouldPersistSubmissionAndCertificate_WhenRecognitionSucceeds() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult result = healthCertificateService.register(validImage(), petId, userId);

        assertNotNull(result.submissionId());
        assertEquals(petId, result.petId());
        assertEquals("초코", result.animalName());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals(LocalDate.of(2020, 10, 28), result.diagnosedDate());
        assertEquals(false, result.manuallyEdited());

        // 동기 경로는 처음부터 COMPLETED 라 Quartz 배치가 집어가지 않는다.
        Submission submission = submissionRepository.findById(result.submissionId()).orElseThrow();
        assertEquals(SubmissionStatus.COMPLETED, submission.getStatus());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, submission.getType());
        assertTrue(submission.getPhotoUrl().startsWith("health-certificates/"));
        assertNotNull(submission.getCompletedAt());

        assertEquals(1, petHealthCertificateRepository.count());
    }

    @Test
    void register_ShouldPersistNothing_WhenConfidenceIsBelowThreshold() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.40));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
        assertEquals(0, submissionRepository.count());
        assertEquals(0, petHealthCertificateRepository.count());
    }

    @Test
    void register_ShouldPersistNothing_WhenImageIsNotAHealthCertificate() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult("영수증\n합계 12,000원", 0.99));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        assertEquals(ErrorCode.NOT_A_HEALTH_CERTIFICATE, e.getErrorCode());
        assertEquals(0, submissionRepository.count());
    }

    @Test
    void register_ShouldPersistNothing_WhenOcrServiceIsUnavailable() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenThrow(new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE));

        assertThrows(
            ServiceUnavailableException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        assertEquals(0, submissionRepository.count());
        assertEquals(0, petHealthCertificateRepository.count());
    }

    @Test
    void register_ShouldReturn404_WhenPetBelongsToSomeoneElse() {
        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.register(validImage(), petId, 999999L)
        );

        assertEquals(0, submissionRepository.count());
    }

    @Test
    void register_ShouldSucceedWithNullDate_WhenDateCannotBeParsed() {
        String textWithBadDate = CERTIFICATE_TEXT.replace("진단 연월일: 2020.10.28", "진단 연월일: 202O.1O.28");
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(textWithBadDate, 0.9124));

        HealthCertificateResult result = healthCertificateService.register(validImage(), petId, userId);

        // 파싱 실패가 등록을 막지는 않는다. 원문은 남기고 DATE 컬럼만 비운다.
        assertNull(result.diagnosedDate());
        assertEquals("202O.1O.28", result.diagnosedDateText());
    }

    // ────────────── 빈도 제한: 실패 요청도 제한에 걸려야 한다 (§5.5c) ──────────────

    @Test
    void register_ShouldBlockSecondRequest_EvenWhenFirstOneFailedRecognition() {
        // 인식에 실패하는 응답 — 제출 행이 남지 않는다.
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.10));

        assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        InvalidRequestDataException second = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        // 제출 행 기준으로 셌다면 두 번째도 OCR 을 호출해 과금됐을 것이다.
        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, second.getErrorCode());
        verify(healthCertificateOcrClient, times(1)).extract(any(), anyString());
        assertEquals(0, submissionRepository.count());
    }

    @Test
    void register_ShouldNotBlockEyeDiagnosis_WhenCertificateWasJustSubmitted() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        healthCertificateService.register(validImage(), petId, userId);

        // 진단서 제출이 눈 진단의 빈도 제한을 건드리면 안 된다 (유형별 분리).
        Pet pet = petRepository.findById(petId).orElseThrow();
        assertTrue(submissionService.getMostRecentSubmissionByPetAndType(pet, SubmissionTypeEnum.EYE).isEmpty());
        assertTrue(submissionService.getMostRecentSubmissionByPetAndType(
            pet, SubmissionTypeEnum.HEALTH_CERTIFICATE).isPresent());
    }

    // ───────────────────────── 조회 · 수정 ─────────────────────────

    @Test
    void getSubmissionAndCertificateById_ShouldNeverExposePhotoUrl() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        SubmissionDetail<HealthCertificateResult> detail =
            submissionService.getSubmissionAndCertificateById(created.submissionId(), userId);

        // 이미지는 전용 엔드포인트로만 받게 해서 무서명 URL 이 새어나갈 경로를 막는다.
        assertNull(detail.getPhotoUrl());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, detail.getType());
        assertEquals(1, detail.getResults().size());
    }

    @Test
    void getSubmissionAndCertificateById_ShouldReturn404_ForAnotherUser() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        assertThrows(
            ResourceNotFoundException.class,
            () -> submissionService.getSubmissionAndCertificateById(created.submissionId(), 999999L)
        );
    }

    @Test
    void getImageUrl_ShouldReturnExpiringUrl() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        var response = healthCertificateService.getImageUrl(created.submissionId(), userId);

        assertNotNull(response.imageUrl());
        assertEquals(300L, response.expiresIn());
    }

    @Test
    void update_ShouldApplyOnlyProvidedFieldsAndMarkManuallyEdited() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("심장비대");
        request.setDiagnosedDateText("2021.01.02");

        HealthCertificateResult updated =
            healthCertificateService.update(created.submissionId(), userId, request);

        assertEquals("심장비대", updated.diseaseName());
        // 날짜는 원문을 받아 서버가 재파싱한다.
        assertEquals(LocalDate.of(2021, 1, 2), updated.diagnosedDate());
        assertTrue(updated.manuallyEdited());

        // 전달하지 않은 필드는 그대로 남는다.
        assertEquals("초코", updated.animalName());
        assertEquals("Pug", updated.breed());
    }

    @Test
    void update_ShouldReject_WhenRequiredFieldIsBlanked() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.update(created.submissionId(), userId, request)
        );

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void update_ShouldReturn404_ForAnotherUser() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("심장비대");

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.update(created.submissionId(), 999999L, request)
        );
    }

    // ───────────────────────── 목록 type 필터 ─────────────────────────

    @Test
    void getSubmissionsByUserId_ShouldFilterByType() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        healthCertificateService.register(validImage(), petId, userId);

        Pet pet = petRepository.findById(petId).orElseThrow();
        Submission eyeSubmission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        eyeSubmission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(eyeSubmission);

        var pageable = org.springframework.data.domain.PageRequest.of(0, 15);

        // 필터 없이 호출하면 기존과 동일한 결과
        assertEquals(2, submissionService
            .getSubmissionsByUserId(userId, null, null, null, null, null, pageable)
            .getTotalElements());

        assertEquals(1, submissionService
            .getSubmissionsByUserId(userId, null, null, null, null,
                SubmissionTypeEnum.HEALTH_CERTIFICATE, pageable)
            .getTotalElements());

        assertEquals(1, submissionService
            .getSubmissionsByUserId(userId, null, null, null, null,
                SubmissionTypeEnum.EYE, pageable)
            .getTotalElements());
    }

    @Test
    void getSubmissionsByPetId_ShouldExcludeDeletedSubmissions() {
        when(healthCertificateOcrClient.extract(any(), anyString()))
            .thenReturn(new HealthCertificateOcrResult(CERTIFICATE_TEXT, 0.9124));

        HealthCertificateResult created = healthCertificateService.register(validImage(), petId, userId);
        submissionService.deleteSubmissionById(created.submissionId(), userId);

        var pageable = org.springframework.data.domain.PageRequest.of(0, 15);

        // 사용자 기준 목록과 동작이 같아야 한다 (기존 비대칭 정리).
        assertEquals(0, submissionService
            .getSubmissionsByPetId(petId, userId, null, null, null, null, null, pageable)
            .getTotalElements());
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile("image", "certificate.jpg", "image/jpeg", jpegBytes());
    }

    /** validateImageFile 이 Tika 로 바이트를 검사하므로 실제 JPEG 이어야 한다. */
    private byte[] jpegBytes() {
        try {
            java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpg", out);

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
