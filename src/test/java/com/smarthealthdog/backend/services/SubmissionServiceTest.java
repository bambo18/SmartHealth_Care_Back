package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.CreatePetRequest;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionPage;
import com.smarthealthdog.backend.dto.diagnosis.update.DiagnosisResultDto;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.ConditionRepository;
import com.smarthealthdog.backend.repositories.ConditionTranslationRepository;
import com.smarthealthdog.backend.repositories.DiagnosisRepository;
import com.smarthealthdog.backend.repositories.LanguageRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.repositories.UserRepository;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class SubmissionServiceTest {
    @Autowired
    private PetService petService;

    @Autowired
    private SubmissionService submissionService; 

    @Autowired
    private UserService userService;

    @Autowired
    private ConditionRepository conditionRepository;

    @Autowired
    private ConditionTranslationRepository conditionTranslationRepository;

    @Autowired
    private DiagnosisRepository diagnosisRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeAll
    void setup() {
        // iterate over Enum values and create permissions
        // // --- General User Permissions (User & Profile) ---
        // CAN_VIEW_OWN_PROFILE("can_view_own_profile", "자신의 프로필 보기"),
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
        // 유저 생성
        userService.createUser("testuser", "email@email.com", "Password1!");
        User user = userService.getUserByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        // 반려동물 생성
        CreatePetRequest petRequest = new CreatePetRequest(
            "Buddy",
            PetSpecies.DOG,
            "Golden Retriever",
            PetGender.MALE,
            LocalDate.of(2020, 1, 1),
            true,
            BigDecimal.valueOf(30.5)
        );

        try {
            petService.create(user.getId(), petRequest, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);

        // 반려동물 확인
        assertTrue(pet != null);

        Condition condition = new Condition();
        condition.setName("Test Disease");
        condition.setSpecies(PetSpecies.DOG);
        conditionRepository.save(condition);

        condition = conditionRepository.findByName("Test Disease").orElse(null);
        assertTrue(condition != null);
    }

    @BeforeEach
    void beforeEach() {
        diagnosisRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    @AfterAll
    void cleanup() {
        diagnosisRepository.deleteAll();
        conditionTranslationRepository.deleteAll();
        languageRepository.deleteAll();
        conditionRepository.deleteAll();
        submissionRepository.deleteAll();
        petRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenSubmissionNotProcessing() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(submission);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(submission.getId(), null);
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenRequestIsNull() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(submission.getId(), null);
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenResultsAreEmpty() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(submission.getId(), new com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest());
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenResultsAreNull() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(null);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(submission.getId(), request);
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenResultsAreEmptyList() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(Collections.emptyList());

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(submission.getId(), request);
        });
    }

    @Test
    void completeDiagnosis_ShouldProcessSuccessfully_WhenValidRequest() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);

        SubmissionResultRequest request = new SubmissionResultRequest();
        DiagnosisResultDto resultDto = new DiagnosisResultDto();
        resultDto.setDisease("Test Disease");
        resultDto.setProbability(new BigDecimal("0.85"));
        resultDto.setModelMd5Hash("dummyhash");
        request.setResults(List.of(resultDto));

        Submission updatedSubmission = submissionService.completeEyeTest(submission.getId(), request);
        assertTrue(updatedSubmission.getStatus() == SubmissionStatus.COMPLETED);

        assertTrue(diagnosisRepository.count() == 1);
    }

    @Test
    void deleteSubmissionById_ShouldThrowResourceNotFoundException_WhenSubmissionAlreadyDeleted() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.DELETED);
        submissionService.saveSubmission(submission);

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.deleteSubmissionById(submission.getId(), user.getId());
        });
    }

    @Test
    void deleteSubmissionById_ShouldUpdateStatusToDeleted() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(submission);

        submissionService.deleteSubmissionById(submission.getId(), user.getId());
        Submission deletedSubmission = submissionService.getSubmissionById(submission.getId());
        assertTrue(deletedSubmission.getStatus() == SubmissionStatus.DELETED);
    }

    @Test
    void failSubmission_ShouldThrowInvalidRequestDataException_WhenSubmissionStatusIsNotProcessing() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(submission);
        SubmissionFailureReasonEnum failureReason = SubmissionFailureReasonEnum.SERVICE_ERROR;
        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.failSubmission(submission, failureReason);
        });
    }

    @Test
    void failSubmission_ShouldUpdateStatusToFailed() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(submission);
        SubmissionFailureReasonEnum failureReason = SubmissionFailureReasonEnum.SERVICE_ERROR;
        Submission failedSubmission = submissionService.failSubmission(submission, failureReason);
        assertTrue(failedSubmission.getStatus() == SubmissionStatus.FAILED);
        assertTrue(failedSubmission.getFailureReason().equals(failureReason));
        assertTrue(failedSubmission.getCompletedAt() != null);
    }

    @Test
    void getSubmissionsByPetId_ShouldThrowResourceNotFoundException_WhenNoSubmissionsExist() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Pageable pageable = Pageable.ofSize(10);

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.getSubmissionsByPetId(
                pet.getId(), user.getId(), null, null, null, null, null, pageable);
        });
    }

    @Test
    void getSubmissionsByPetId_ShouldReturnSubmissions_WhenTheyExist() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(submission);

        Pageable pageable = Pageable.ofSize(10);
        SubmissionPage page = submissionService.getSubmissionsByPetId(
            pet.getId(), user.getId(), null, null, null, null, null, pageable);
        assertTrue(page.getTotalElements() == 1);

        // Create another submission for the same pet
        Submission anotherSubmission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        anotherSubmission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(anotherSubmission);

        page = submissionService.getSubmissionsByPetId(
            pet.getId(), user.getId(), null, null, null, null, null, pageable);
        assertTrue(page.getTotalElements() == 2);
    }

    @Test
    void getSubmissionsByUserId_ShouldReturnSubmissions_WhenTheyExist() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionService.saveSubmission(submission);

        Pageable pageable = Pageable.ofSize(10);
        SubmissionPage page = submissionService.getSubmissionsByUserId(
            user.getId(), null, null, null, null, null, pageable);
        assertTrue(page.getTotalElements() == 1);

        // Create another submission for the same pet
        Submission anotherSubmission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        anotherSubmission.setStatus(SubmissionStatus.PROCESSING);
        submissionService.saveSubmission(anotherSubmission);

        page = submissionService.getSubmissionsByUserId(
            user.getId(), null, null, null, null, null, pageable);
        assertTrue(page.getTotalElements() == 2);
    }

    @Test
    void getSubmissionsByPetId_type_필터가_해당_유형만_반환한다() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        // given: 같은 반려동물에 눈 1건, 진단서 1건
        submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.EYE)
        );
        Submission cert = submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        SubmissionPage certOnly = submissionService.getSubmissionsByPetId(
            pet.getId(), user.getId(),
            SubmissionTypeEnum.HEALTH_CERTIFICATE,
            null, null, null, null,
            PageRequest.of(0, 15)
        );

        assertEquals(1L, certOnly.getTotalElements());
        assertEquals(cert.getId().toString(), certOnly.getSubmissions().get(0).getSubmissionId());
    }

    @Test
    void getSubmissionsByPetId_type이_null이면_모든_유형을_반환한다() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.EYE)
        );
        submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        SubmissionPage all = submissionService.getSubmissionsByPetId(
            pet.getId(), user.getId(),
            null,
            null, null, null, null,
            PageRequest.of(0, 15)
        );

        assertEquals(2L, all.getTotalElements());
    }

    @Test
    void getSubmissionsByUserId_type_필터가_해당_유형만_반환한다() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.EYE)
        );
        Submission cert = submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        SubmissionPage certOnly = submissionService.getSubmissionsByUserId(
            user.getId(),
            SubmissionTypeEnum.HEALTH_CERTIFICATE,
            null, null, null, null,
            PageRequest.of(0, 15)
        );

        assertEquals(1L, certOnly.getTotalElements());
        assertEquals(cert.getId().toString(), certOnly.getSubmissions().get(0).getSubmissionId());
    }

    @Test
    void getSubmissionsByPetId_삭제된_제출을_제외한다() {
        // 기존 버그: filterPetSubmissions 는 filterUserSubmissions 와 달리
        // DELETED 를 제외하지 않아 반려동물별 목록에만 삭제된 제출이 보였다.
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        Submission kept = submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.EYE)
        );
        Submission removed = submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.EYE)
        );
        submissionService.deleteSubmissionById(removed.getId(), user.getId());

        SubmissionPage page = submissionService.getSubmissionsByPetId(
            pet.getId(), user.getId(),
            null, null, null, null, null,
            PageRequest.of(0, 15)
        );

        assertEquals(1L, page.getTotalElements());
        assertEquals(kept.getId().toString(), page.getSubmissions().get(0).getSubmissionId());
    }

    @Test
    void createCompletedSubmission_처음부터_COMPLETED_상태로_만든다() {
        Pet pet = 소유_반려동물();

        Submission submission = submissionService.createCompletedSubmission(
            pet,
            SubmissionTypeEnum.HEALTH_CERTIFICATE,
            "health-certificates/abc.jpg"
        );

        assertTrue(submission.getId() != null);
        assertEquals(SubmissionStatus.COMPLETED, submission.getStatus());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, submission.getType());
        assertEquals("health-certificates/abc.jpg", submission.getPhotoUrl());
        assertTrue(submission.getSubmittedAt() != null);
        assertTrue(submission.getCompletedAt() != null);
        assertTrue(submission.getFailureReason() == null);

        // 저장까지 끝났는지 확인한다 (createSubmission 과 달리 저장 전 상태를 돌려주지 않는다).
        assertTrue(submissionRepository.findById(submission.getId()).isPresent());
    }

    @Test
    void createCompletedSubmission_배치가_집어가지_않는_상태여야_한다() {
        // PushAIInferenceTasks 는 status=PENDING AND photoUrl<>'' 인 행만 집는다.
        // 동기 경로의 행이 Celery 큐로 흘러가면 안 된다.
        Pet pet = 소유_반려동물();

        submissionService.createCompletedSubmission(
            pet, SubmissionTypeEnum.HEALTH_CERTIFICATE, "health-certificates/abc.jpg"
        );

        List<Submission> waiting = submissionRepository.findSubmissionsWaitingToBeProcessedByAmount(
            SubmissionStatus.PENDING,
            PageRequest.of(0, 100)
        );

        assertTrue(waiting.isEmpty(), "동기 경로 행이 Celery 대기 목록에 들어가면 안 된다");
    }

    @Test
    void createCompletedSubmission_photoKey가_비면_예외를_던진다() {
        // 동기 경로는 S3 업로드가 끝난 뒤에만 호출되므로 빈 키는 호출 순서 버그다.
        Pet pet = 소유_반려동물();

        assertThrows(IllegalArgumentException.class, () ->
            submissionService.createCompletedSubmission(
                pet, SubmissionTypeEnum.HEALTH_CERTIFICATE, ""
            )
        );

        assertThrows(IllegalArgumentException.class, () ->
            submissionService.createCompletedSubmission(
                pet, SubmissionTypeEnum.HEALTH_CERTIFICATE, null
            )
        );
    }

    private Pet 소유_반려동물() {
        User user = userRepository.findByEmail("email@email.com").orElse(null);
        assertTrue(user != null);

        Pet pet = petService.listByOwner(user.getId()).stream().findFirst().orElse(null);
        assertTrue(pet != null);

        return pet;
    }
}