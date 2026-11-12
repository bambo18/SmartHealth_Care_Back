package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
import com.smarthealthdog.backend.dto.diagnosis.update.CertificateOcrResultRequest;
import com.smarthealthdog.backend.dto.health.CertificateSubmissionAccepted;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
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

    // ───────────────────────── 등록 (접수까지만) ─────────────────────────

    @Test
    void register_ShouldCreatePendingSubmission_WithoutCallingOcr() {
        CertificateSubmissionAccepted accepted =
            healthCertificateService.register(validImage(), petId, userId);

        assertNotNull(accepted.submissionId());
        assertEquals(petId, accepted.petId());
        assertEquals(SubmissionStatus.PENDING, accepted.status());

        // 비동기 경로다 — 등록 시점에는 추출 결과가 없고 백엔드가 OCR 을 부르지도 않는다.
        Submission submission = submissionRepository.findById(accepted.submissionId()).orElseThrow();
        assertEquals(SubmissionStatus.PENDING, submission.getStatus());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, submission.getType());
        assertNull(submission.getCompletedAt());
        assertEquals(0, petHealthCertificateRepository.count());
        verifyNoInteractions(healthCertificateOcrClient);
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
    void applyOcrResult_ShouldSucceedWithNullDate_WhenDateCannotBeParsed() {
        String textWithBadDate =
            CERTIFICATE_TEXT.replace("진단 연월일: 2020.10.28", "진단 연월일: 202O.1O.28");

        HealthCertificateResult result = resultOf(registerCompleted(textWithBadDate, 0.9124));

        // 파싱 실패가 등록을 막지는 않는다. 원문은 남기고 DATE 컬럼만 비운다.
        assertNull(result.diagnosedDate());
        assertEquals("202O.1O.28", result.diagnosedDateText());
    }

    @Test
    void applyOcrResult_ShouldCompleteSubmissionAndPersistCertificate() {
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        Submission submission = submissionRepository.findById(submissionId).orElseThrow();
        assertEquals(SubmissionStatus.COMPLETED, submission.getStatus());
        assertNotNull(submission.getCompletedAt());
        assertEquals(1, petHealthCertificateRepository.count());

        HealthCertificateResult result = resultOf(submissionId);
        assertEquals("초코", result.animalName());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals(false, result.manuallyEdited());

        // 원문을 검증한다. 파싱된 LocalDate 는 DB 왕복에서 하루 밀린다 —
        // H2 세션이 UTC(TIME ZONE=UTC)인데 JVM 기본 시간대가 KST 라서 생기는 현상으로,
        // 기존 테스트는 저장 직후 메모리 객체만 봐서 드러나지 않았다.
        // 운영 PostgreSQL 에도 같은 문제가 있는지 확인이 필요하다 (PLAN.md 미해결 항목).
        assertEquals("2020.10.28", result.diagnosedDateText());
    }

    // ────────────── 빈도 제한: 유형별로 분리되어야 한다 ──────────────

    @Test
    void register_ShouldBlockSecondRequestWithinInterval() {
        healthCertificateService.register(validImage(), petId, userId);

        InvalidRequestDataException second = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(validImage(), petId, userId)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, second.getErrorCode());
        assertEquals(1, submissionRepository.count());
    }

    @Test
    void register_ShouldNotBlockEyeDiagnosis_WhenCertificateWasJustSubmitted() {
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
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        SubmissionDetail<HealthCertificateResult> detail =
            submissionService.getSubmissionAndCertificateById(submissionId, userId);

        // 이미지는 전용 엔드포인트로만 받게 해서 무서명 URL 이 새어나갈 경로를 막는다.
        assertNull(detail.getPhotoUrl());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, detail.getType());
        assertEquals(1, detail.getResults().size());
    }

    @Test
    void getSubmissionAndCertificateById_ShouldReturn404_ForAnotherUser() {
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        assertThrows(
            ResourceNotFoundException.class,
            () -> submissionService.getSubmissionAndCertificateById(submissionId, 999999L)
        );
    }

    @Test
    void getImageUrl_ShouldReturnExpiringUrl() {
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        var response = healthCertificateService.getImageUrl(submissionId, userId);

        assertNotNull(response.imageUrl());
        assertEquals(300L, response.expiresIn());
    }

    @Test
    void update_ShouldApplyOnlyProvidedFieldsAndMarkManuallyEdited() {
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("심장비대");
        request.setDiagnosedDateText("2021.01.02");

        HealthCertificateResult updated =
            healthCertificateService.update(submissionId, userId, request);

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
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.update(submissionId, userId, request)
        );

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
    }

    @Test
    void update_ShouldReturn404_ForAnotherUser() {
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);

        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest();
        request.setDiseaseName("심장비대");

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.update(submissionId, 999999L, request)
        );
    }

    // ───────────────────────── 목록 type 필터 ─────────────────────────

    @Test
    void getSubmissionsByUserId_ShouldFilterByType() {
        registerCompleted(CERTIFICATE_TEXT, 0.9124);

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
        UUID submissionId = registerCompleted(CERTIFICATE_TEXT, 0.9124);
        submissionService.deleteSubmissionById(submissionId, userId);

        var pageable = org.springframework.data.domain.PageRequest.of(0, 15);

        // 사용자 기준 목록과 동작이 같아야 한다 (기존 비대칭 정리).
        assertEquals(0, submissionService
            .getSubmissionsByPetId(petId, userId, null, null, null, null, null, pageable)
            .getTotalElements());
    }

    /**
     * 비동기 파이프라인 한 바퀴를 테스트 안에서 돌린다.
     *
     * 테스트 프로파일의 업로더는 아무것도 저장하지 않고 Quartz 도 꺼져 있으므로,
     * 업로드 완료(photoUrl)와 큐 적재(PROCESSING)를 손으로 대신한 뒤 워커 콜백을 흉내낸다.
     *
     * @return 완료된 제출 ID
     */
    private UUID registerCompleted(String rawText, double confidence) {
        CertificateSubmissionAccepted accepted =
            healthCertificateService.register(validImage(), petId, userId);

        Submission submission = submissionRepository.findById(accepted.submissionId()).orElseThrow();
        submission.setPhotoUrl("health-certificates/" + UUID.randomUUID() + ".jpg");
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionRepository.save(submission);

        CertificateOcrResultRequest request = new CertificateOcrResultRequest();
        request.setSuccess(true);
        request.setRawText(rawText);
        request.setConfidence(BigDecimal.valueOf(confidence));

        healthCertificateService.applyOcrResult(accepted.submissionId(), request);

        return accepted.submissionId();
    }

    /** 완료된 제출의 진단서 결과를 꺼낸다. */
    private HealthCertificateResult resultOf(UUID submissionId) {
        return submissionService.getSubmissionAndCertificateById(submissionId, userId)
            .getResults()
            .iterator()
            .next();
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
