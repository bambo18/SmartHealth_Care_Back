package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.CreatePetRequest;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.repositories.UserRepository;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class AIDiagnosisClientServiceTest {
    @MockitoBean
    private FileUploadService fileUploadService;

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
    private AIDiagnosisClientService aiDiagnosisClientService;

    @BeforeAll
    public void setup() {
        ReflectionTestUtils.setField(
            aiDiagnosisClientService,
            "inferenceIntervalSeconds",
            0
        );

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
    }

    @AfterAll
    public void cleanup() {
        submissionRepository.deleteAll();
        petRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void performEyeDiagnosis_ShouldThrowException_WhenPetIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, null, 1L);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowException_WhenOwnerIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, 1L, null);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowException_WhenPetNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, 999L, 1L);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowException_WhenOwnerIdDoesNotMatch() {
        assertThrows(ResourceNotFoundException.class, () -> {
            // Assuming pet with ID 1 exists but does not belong to owner with ID 999
            aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 999L);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldPass_WhenValidPetIdAndOwnerId() {
        Pet pet = petService.listByOwner(
            userService.getUserByEmail("email@email.com").orElse(null).getId()
        ).stream().findFirst().orElse(null);

        aiDiagnosisClientService.performEyeDiagnosis(null, pet.getId(), pet.getOwner().getId());

        // Check if a submission was created
        long submissionCount = submissionRepository.count();
        assertTrue(submissionCount > 0);
    }
}
