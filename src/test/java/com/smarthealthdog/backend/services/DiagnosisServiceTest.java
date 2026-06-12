package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.Diagnosis;
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
import com.smarthealthdog.backend.dto.diagnosis.update.DiagnosisResultDto;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.ConditionRepository;
import com.smarthealthdog.backend.repositories.DiagnosisRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.repositories.UserRepository;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class DiagnosisServiceTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiagnosisRepository diagnosisRepository;

    @Autowired
    private ConditionRepository conditionRepository;

    @Autowired
    private PetService petService;

    @Autowired
    private UserService userService;

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

        // 제출 생성
        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        submissionService.saveSubmission(submission);

        Submission savedSubmission = submissionRepository.findFirst100ByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING).stream().findFirst().orElse(null);
        assertTrue(savedSubmission != null);

        Condition condition = new Condition();
        condition.setName("Test Disease");
        condition.setSpecies(PetSpecies.DOG);
        conditionRepository.save(condition);
    }

    @AfterAll
    void teardown() {
        diagnosisRepository.deleteAll();
        conditionRepository.deleteAll();
        submissionRepository.deleteAll();
        petRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    @Transactional
    void processInferenceResult_ShouldThrowInvalidRequestDataException_WhenNoValidDiagnoses() {
        Submission submission = submissionRepository.findFirst100ByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING).stream().findFirst().orElse(null);
        assertTrue(submission != null);

        DiagnosisResultDto resultDto = new DiagnosisResultDto();
        resultDto.setDisease("SomeDisease");
        resultDto.setProbability(new BigDecimal("0.85"));
        resultDto.setModelMd5Hash("dummyhash");

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(List.of(resultDto));

        assertThrows(InvalidRequestDataException.class, () -> {
            diagnosisService.processInferenceResult(submission, request);
        });
    }

    @Test
    @Transactional
    void processInferenceResult_ShouldProcessSuccessfully_WhenValidDiagnosesExist() {
        Submission submission = submissionRepository.findFirst100ByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING).stream().findFirst().orElse(null);
        assertTrue(submission != null);

        DiagnosisResultDto resultDto = new DiagnosisResultDto();
        resultDto.setDisease("Test Disease");
        resultDto.setProbability(new BigDecimal("0.85"));
        resultDto.setModelMd5Hash("dummyhash");

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(List.of(resultDto));

        diagnosisService.processInferenceResult(submission, request);

        List<Diagnosis> savedDiagnoses = diagnosisRepository.findAll();
        assertTrue(savedDiagnoses.size() > 0);
    }
}
