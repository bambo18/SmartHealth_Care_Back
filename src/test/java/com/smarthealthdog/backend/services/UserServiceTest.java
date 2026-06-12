package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.UpdateUserProfileRequest;
import com.smarthealthdog.backend.dto.UserProfile;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.ImgUtils;


@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class UserServiceTest {
    @MockitoBean
    private FileUploadService fileUploadService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ImgUtils imgUtils;

    @BeforeAll
    void setup() {
        ReflectionTestUtils.setField(
            imgUtils,
            "localStorageUrlPrefix",
            "http://localhost:8080/images/"
        );

        ReflectionTestUtils.setField(
            imgUtils,
            "aiModelServiceUrlPrefix",
            "http://localhost:9090/ai/images/"
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
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @AfterAll
    void tearDown() {
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    public void createUser_ShouldThrowInvalidRequestDataException_WhenNicknameIsInvalid() {
        String invalidNickname = "ab"; // 너무 짧은 닉네임
        String email = "testuser@example.com";
        String password = "ValidPass1!";

        // ACT & ASSERT
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(invalidNickname, email, password);
        });

        String longNickname = "a".repeat(129); // 너무 긴 닉네임
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(longNickname, email, password);
        });
    }

    @Test
    public void createUser_ShouldThrowInvalidRequestDataException_WhenPasswordIsInvalid() {
        String nickname = "validNickname";
        String email = "testuser@example.com";
        String invalidPassword = "short"; // 너무 짧은 비밀번호

        // ACT & ASSERT
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(nickname, email, invalidPassword);
        });

        String noUpperCasePassword = "lowercase1!"; // 대문자 없음
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(nickname, email, noUpperCasePassword);
        });

        String noSpecialCharPassword = "NoSpecialChar1"; // 특수문자 없음
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(nickname, email, noSpecialCharPassword);
        });

        String noNumberPassword = "NoNumber!"; // 숫자 없음
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(nickname, email, noNumberPassword);
        });
    }

    @Test
    public void createUser_ShouldThrowInvalidRequestDataException_WhenEmailAlreadyExists() {
        String nickname1 = "userOne";
        String email = "testuser@example.com";
        String password = "ValidPass1!";

        userService.createUser(nickname1, email, password);

        boolean userExists = userRepository.existsByEmail(email);
        assertTrue(userExists);

        String nickname2 = "userTwo";
        // ACT & ASSERT
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.createUser(nickname2, email, password);
        });
    }

    @Test
    public void createUser_ShouldCreateUserSuccessfully_WhenInputIsValid() {
        String nickname = "validNickname";
        String email = "testuser@example.com";
        String password = "ValidPass1!";

        userService.createUser(nickname, email, password);

        User user = userRepository.findByEmail(email).orElse(null);
        assertNotNull(user);

        assertTrue(user.getNickname().equals(nickname));
        assertTrue(user.getEmail().equals(email));
        assertTrue(user.getPassword() != null);
        assertTrue(user.getPassword() != password); // 비밀번호는 해시화되어 저장되므로 원본과 다름
    }

    @Test
    public void getUserProfileById_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        Long nonExistingUserId = 999L;

        assertThrows(ResourceNotFoundException.class, () -> {
            userService.getUserProfileById(nonExistingUserId);
        });
    }
    
    @Test
    public void getUserProfileById_ShouldReturnUserProfile_WhenUserExists() {
        User user = userService.createUser("asdfasdf", "testuser@example.com", "ValidPass1!");
        
        boolean userExists = userRepository.findById(user.getId()).isPresent();
        assertTrue(userExists);

        UserProfile userProfile = userService.getUserProfileById(user.getId());
        assertNotNull(userProfile);
        assertEquals(user.getId(), userProfile.id());
        assertEquals(user.getEmail(), userProfile.email());
        assertEquals(user.getNickname(), userProfile.nickname());
        assertEquals(user.getProfilePic(), userProfile.profilePicture());
    }

    @Test
    public void updateUserProfile_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        Long nonExistingUserId = 999L;

        UpdateUserProfileRequest request = new UpdateUserProfileRequest("newNickname");

        MockMultipartFile newProfilePic = new MockMultipartFile(
            "file",
            "profile.jpg",
            "image/jpeg",
            "dummy image content".getBytes()
        );

        assertThrows(ResourceNotFoundException.class, () -> {
            userService.updateUserProfile(nonExistingUserId, request, newProfilePic);
        });
    }

    @Test
    public void updateUserProfile_ShouldThrowInvalidRequestDataException_WhenNicknameIsInvalid() {
        User user = userService.createUser("validNickname", "testuser@example.com", "ValidPass1!");
        boolean userExists = userRepository.findById(user.getId()).isPresent();
        assertTrue(userExists);

        String invalidNickname = "ab"; // 너무 짧은 닉네임
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(invalidNickname);
        MockMultipartFile newProfilePic = new MockMultipartFile(
            "file",
            "profile.jpg",
            "image/jpeg",
            "dummy image content".getBytes()
        );
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.updateUserProfile(user.getId(), request, newProfilePic);
        });

        String longNickname = "a".repeat(129); // 너무 긴 닉네임
        UpdateUserProfileRequest longNicknameRequest = new UpdateUserProfileRequest(longNickname);
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.updateUserProfile(user.getId(), longNicknameRequest, newProfilePic);
        });
    }

    @Test
    public void updateUserProfile_ShouldUpdateProfileSuccessfully_WhenInputIsValid() {
        User user = userService.createUser("validNickname", "testuser@example.com", "ValidPass1!");
        boolean userExists = userRepository.findById(user.getId()).isPresent();
        assertTrue(userExists);

        String newNickname = "newValidNickname";
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(newNickname);
        MockMultipartFile newProfilePic = new MockMultipartFile(
            "file",
            "profile.jpg",
            "image/jpeg",
            "dummy image content".getBytes()
        );

        UserProfile updatedProfile = userService.updateUserProfile(user.getId(), request, newProfilePic);
        assertNotNull(updatedProfile);
        assertEquals(newNickname, updatedProfile.nickname());
        assertEquals(user.getEmail(), updatedProfile.email());
        assertEquals(user.getId(), updatedProfile.id());
    }
}