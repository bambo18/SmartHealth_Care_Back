package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.UpdateUserProfileRequest;
import com.smarthealthdog.backend.dto.UserProfile;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.validation.NicknameValidator;
import com.smarthealthdog.backend.validation.PasswordValidator;

@ExtendWith(MockitoExtension.class) 
public class UserServiceUT {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private NicknameValidator nicknameValidator;

    @Mock 
    private FileUploadService fileUploadService;

    @Mock
    private ImgUtils imgUtils;

    @InjectMocks
    private UserService userService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        // Initialization if needed
        // Create roles
        userRole = new Role();
        userRole.setId((short)2);
        userRole.setName(RoleEnum.USER);
    }

    @Test
    public void testCreateUser_Success() {
        // ARRANGE: Set up the mock behavior for the success path
        // 1. **FIX:** Tell the nicknameValidator to return TRUE
        when(nicknameValidator.isValid(anyString())).thenReturn(true); 

        // 2. Tell the userRepository that no user exists (so creation can proceed)
        when(userRepository.existsByEmail(anyString())).thenReturn(false); 
        
        // 3. Tell the passwordValidator to return TRUE
        when(passwordValidator.isValid(anyString())).thenReturn(true); 

        // 4. Mock the role service to return a non-null Role object
        when(roleService.getUserRole()).thenReturn(new Role()); 
        
        // 5. Mock the userRepository.save() call (to return the created user)
        // This is often good practice to ensure the service proceeds to the end.
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            userArg.setId(1L); // Simulate DB assigning an ID
            userArg.setCreatedAt(java.time.Instant.now());
            userArg.setUpdatedAt(java.time.Instant.now());
            return userArg;
        });

        // Implement test logic here
        User user = userService.createUser(
            "nickname", 
            "test@email.com",
            "ValidPassword123!"
        );

        assertTrue(user != null);
        assertTrue(user.getNickname().equals("nickname"));
        assertTrue(user.getEmail().equals("test@email.com"));
        assertTrue(user.getCreatedAt() != null);
        assertTrue(user.getUpdatedAt() != null);
        assertTrue(user.getRole() != null);
        assertTrue(user.getLoginAttempt() == 0);
        assertTrue(user.getId() != null); // ID should not be null after saving to DB
        assertTrue(user.getProfilePic() == null);
        assertTrue(user.getPassword() == null); // Password should not be exposed
        assertTrue(user.getLoginAttempt() == 0);
        assertTrue(user.getLoginAttemptStartedAt() == null);
        assertTrue(user.getPasswordResetToken() == null);
        assertTrue(user.getPasswordResetTokenExpiry() == null);
        assertTrue(user.getPasswordResetRequestedAt() == null);
        assertTrue(user.getPasswordResetTokenVerifyFailCount() == 0);
        assertTrue(user.getEmailVerificationToken() == null);
        assertTrue(user.getEmailVerificationRequestedAt() == null);
        assertTrue(user.getEmailVerificationExpiry() == null);
        assertTrue(user.getEmailVerificationFailCount() == 0);
    }

    @Test
    public void testExpireEmailVerificationToken_Success() {
        User user = new User();
        user.setId(1L);
        user.setEmailVerificationExpiry(null); // Initially null

        // ACT
        userService.expireEmailVerificationToken(user);

        // ASSERT: Verify the email verification expiry has been set to now
        assertTrue(user.getEmailVerificationExpiry() != null);
    }

    @Test
    public void getUserProfileById_ShouldThrowException_WhenUserNotFound() {
        Long nonExistentUserId = 999L;

        // Arrange
        when(userRepository.findById(nonExistentUserId)).thenReturn(java.util.Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            userService.getUserProfileById(nonExistentUserId);
        });
    }

    @Test
    public void getUserProfileById_ShouldReturnUserProfile_WhenUserExists() {
        Long existingUserId = 1L;
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(existingUserId);
        when(mockUser.getNickname()).thenReturn("testNickname");
        when(mockUser.getEmail()).thenReturn("testEmail@example.com");
        when(mockUser.getProfilePic()).thenReturn("profilePicUrl");

        // Arrange
        when(userRepository.findById(existingUserId)).thenReturn(java.util.Optional.of(mockUser));

        // Act
        UserProfile userProfile = userService.getUserProfileById(existingUserId);

        // Assert
        assertTrue(userProfile.id().equals(existingUserId));
        assertTrue(userProfile.nickname().equals("testNickname"));
        assertTrue(userProfile.email().equals("testEmail@example.com"));
    }

    @Test
    public void updateUserProfile_ShouldThrowException_WhenUserNotFound() {
        Long nonExistentUserId = 999L;

        // Arrange
        when(userRepository.findById(nonExistentUserId)).thenReturn(java.util.Optional.empty());

        UpdateUserProfileRequest updateUserProfileRequest = new UpdateUserProfileRequest("newNickname");

        MockMultipartFile mockProfilePic = new MockMultipartFile(
            "profilePic", 
            "profile.jpg", 
            "image/jpeg", 
            "dummy image content".getBytes()
        );

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            userService.updateUserProfile(nonExistentUserId, updateUserProfileRequest, mockProfilePic);
        });
    }

    @Test
    public void updateUserProfile_ShouldThrowException_WhenNicknameInvalid() {
        Long existingUserId = 1L;
        User mockUser = mock(User.class);
        when(userRepository.findById(existingUserId)).thenReturn(java.util.Optional.of(mockUser));

        // Arrange
        when(nicknameValidator.isValid("invalidNickname")).thenReturn(false);

        UpdateUserProfileRequest updateUserProfileRequest = new UpdateUserProfileRequest("invalidNickname");

        MockMultipartFile mockProfilePic = new MockMultipartFile(
            "profilePic", 
            "profile.jpg", 
            "image/jpeg", 
            "dummy image content".getBytes()
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            userService.updateUserProfile(existingUserId, updateUserProfileRequest, mockProfilePic);
        });
    }

    @Test
    public void updateUserProfile_ShouldUpdateProfileSuccessfully() {
        Long existingUserId = 1L;
        User mockUser = mock(User.class);
        when(userRepository.findById(existingUserId)).thenReturn(java.util.Optional.of(mockUser));

        // Arrange
        when(nicknameValidator.isValid("newValidNickname")).thenReturn(true);

        UpdateUserProfileRequest updateUserProfileRequest = new UpdateUserProfileRequest("newValidNickname");

        MockMultipartFile mockProfilePic = new MockMultipartFile(
            "profilePic", 
            "profile.jpg", 
            "image/jpeg", 
            "dummy image content".getBytes()
        );

        // Act
        userService.updateUserProfile(existingUserId, updateUserProfileRequest, mockProfilePic);

        // Assert
        verify(mockUser).setNickname("newValidNickname");
        verify(userRepository).save(mockUser);
    }

    @Test
    public void testResetEmailVerificationFailCount_Success() {
        User user = new User();
        user.setId(1L);

        // ACT
        userService.resetEmailVerificationFailCount(user);

        // ASSERT: Since this method does not return anything, we verify that the repository method was called
        // This is done via Mockito's verify in the actual test framework, but here we just ensure no exceptions occur.
        verify(userRepository).resetEmailVerificationFailCount(user.getId());
    }
}