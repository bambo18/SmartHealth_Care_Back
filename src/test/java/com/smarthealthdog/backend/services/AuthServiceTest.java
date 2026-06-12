package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.ImgUtils;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class AuthServiceTest {
    @Autowired 
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordEncoder tokenEncoder;

    @Autowired
    private ImgUtils imgUtils;

    @BeforeAll
    void cleanUp() {
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

        ReflectionTestUtils.setField(
            refreshTokenService,
            "maxRefreshTokenCount",
            10
        );

        Role userRole = new Role();
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Regular user role");
        roleRepository.save(userRole);

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationSecret",
            "test-email-verification-secret"
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "allowedEmails",
            "test@example.com" 
        );
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationRepository.deleteAll();
    }

    @AfterAll
    void finalCleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        emailVerificationRepository.deleteAll();
    }

    @Test
    void registerUser_shouldReturnCreatedUser() {
        // Arrange
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");

        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            "000000"
        );

        // Act
        authService.registerUser(createRequest, null);

        // Assert
        User createdUser = userRepository.findByEmail("test@example.com").orElse(null);
        assertTrue(createdUser != null);
        assertTrue(createdUser.getRole().getName() == RoleEnum.USER);
        assertTrue(createdUser.getEmail().equals("test@example.com"));
        assertTrue(createdUser.getNickname().equals("testuser"));

        // Compare the hashed password with the raw password
        assertTrue(createdUser.getPassword() != null);
        assertTrue(createdUser.getPassword().length() > 0);
        assertTrue(userService.checkUserPassword(createdUser, "TestPassword123!"));
    }

    @Test
    void registerUser_ShouldThrowException_WhenNicknameIsInvalid() {
        UserCreateRequest createRequest = new UserCreateRequest(
            "ab", // 너무 짧은 닉네임
            "test@example.com",
            "TestPassword123!",
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest, null);
        });

        UserCreateRequest createRequest2 = new UserCreateRequest(
            "a".repeat(129), // 너무 긴 닉네임
            "test@example.com",
            "TestPassword123!",
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest2, null);
        });
    }

    @Test
    void registerUser_ShouldThrowException_WhenTokenIsUsedTwice() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        UserCreateRequest createRequest1 = new UserCreateRequest(
            "testuser1",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act & Assert
        authService.registerUser(createRequest1, null);
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest1, null);
        });
    }

    @Test
    void registerUser_ShouldThrowException_WhenPasswordIsInvalid() {
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "short123!", // 대문자 없음
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest, null);
        });

        UserCreateRequest createRequest2 = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "SHORT123!", // 소문자 없음
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest2, null);
        });

        UserCreateRequest createRequest3 = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "NoDigits!", // 숫자 없음
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest3, null);
        });
    
        UserCreateRequest createRequest4 = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "NoSpecialChar123", // 특수문자 없음
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest4, null);
        });

        UserCreateRequest createRequest5 = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "A1!", // 너무 짧은 비밀번호
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest5, null);
        });

        UserCreateRequest createRequest6 = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "A".repeat(257) + "1!", // 너무 긴 비밀번호
            "000000"
        );

        // Act & Assert
        assertThrows(InvalidRequestDataException.class, () -> {
            authService.registerUser(createRequest6, null);
        });
    }

    @Test
    void registerUser_ShouldThrowException_WhenEmailVerificationTokenIsInvalid() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            "000001"
        );

        // Act & Assert
        for (int i = 0 ; i < 5 ; i++) {
            assertThrows(InvalidRequestDataException.class, () -> {
                authService.registerUser(createRequest, null);
            });

            // Check to make sure the fail count is incremented
            EmailVerification updatedRecord = emailVerificationRepository.findByEmail("test@example.com").orElse(null);
            assertNotNull(updatedRecord);
            assertEquals(i + 1, updatedRecord.getEmailVerificationFailCount());
        }
    }

    @Test
    void generateTokens_shouldThrowException_whenUserNotFound() {
        // Arrange
        Long nonExistentUserId = 999L;
        assertThrows(ResourceNotFoundException.class, () -> {
            authService.generateTokens(nonExistentUserId);
        });
    }

    @Test
    void generateTokens_ShouldReturnTokens_whenUserExists() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        // Arrange
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act
        authService.registerUser(createRequest, null);
        User user = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(user);

        // Assert
        LoginResponse loginResponse = authService.generateTokens(user.getId());
        assertTrue(loginResponse.accessToken() != null && loginResponse.accessToken().length() > 0);
        assertTrue(loginResponse.refreshToken() != null && loginResponse.refreshToken().length() > 0);
        assertTrue(loginResponse.expiration() != null && loginResponse.expiration().length() > 0);
    }

    @Test
    void generateTokens_ShouldDeleteExpiredRefreshTokens() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        // Arrange
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act
        authService.registerUser(createRequest, null);
        User user = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(user);

        // Create an expired refresh token
        RefreshToken expiredToken = RefreshToken.builder()
            .user(user)
            .expiresAt(Instant.now().minusSeconds(3600)) // 이미 만료된 토큰
            .id(UUID.randomUUID())
            .build();

        refreshTokenRepository.save(expiredToken);

        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        // Generate new tokens, which should trigger deletion of expired tokens
        authService.generateTokens(user.getId());
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);
        assertTrue(refreshTokenRepository.findByUser(user).get(0).getId() != expiredToken.getId());
    }

    @Test
    void generateTokens_ShouldEnforceMaxRefreshTokenCount() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act
        authService.registerUser(createRequest, null);
        User user = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(user);

        // Generate multiple tokens to exceed the max count
        for (int i = 0; i < 10; i++) {
            refreshTokenService.generateRefreshToken(user);
        }

        // Assert
        List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);
        assertEquals(10, tokens.size());
        
        // Generate one more token to trigger enforcement
        authService.generateTokens(user.getId());

        List<RefreshToken> updatedTokens = refreshTokenRepository.findByUser(user);
        assertEquals(10, updatedTokens.size(), "Total refresh tokens should still be 10 after enforcement");

        // Compare both lists by creating two sets of token IDs
        Set<UUID> originalTokenIds = tokens.stream()
            .map(RefreshToken::getId)
            .collect(Collectors.toSet());

        Set<UUID> updatedTokenIds = updatedTokens.stream()
            .map(RefreshToken::getId)
            .collect(Collectors.toSet());
        
        // Ensure that at least one of the original tokens has been removed
        originalTokenIds.retainAll(updatedTokenIds);
        assertTrue(originalTokenIds.size() == 9);
    }

    @Test
    void invalidateRefreshToken_ShouldThrowException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken("");
        });
    }

    @Test
    void invalidateRefreshToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken("invalid.token.here");
        });
    }

    @Test
    void invalidateRefreshToken_ShouldDeleteToken_WhenTokenIsValid() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        // Arrange
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act
        authService.registerUser(createRequest, null);
        User user = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(user);

        String refreshToken = refreshTokenService.generateRefreshToken(user);
        assertTrue(refreshToken != null && refreshToken.length() > 0);

        // Assert
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);
        authService.invalidateRefreshToken(refreshToken);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }

    @Test
    void refreshAccessToken_ShouldThrowException_WhenRefreshTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            authService.refreshAccessToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            authService.refreshAccessToken("");
        });
    }

    @Test
    void refreshAccessToken_ShouldThrowException_WhenRefreshTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            authService.refreshAccessToken("invalid.token.here");
        });
    }

    @Test
    void refreshAccessToken_ShouldReturnNewTokens_WhenRefreshTokenIsValid() {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // Arrange
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "test@example.com",
            "TestPassword123!",
            token
        );

        // Act
        authService.registerUser(createRequest, null);
        User user = userRepository.findByEmail("test@example.com").orElse(null);
        assertNotNull(user);

        String refreshToken = refreshTokenService.generateRefreshToken(user);
        assertTrue(refreshToken != null && refreshToken.length() > 0);

        // Assert
        LoginResponse loginResponse = authService.refreshAccessToken(refreshToken);
        assertTrue(loginResponse.accessToken() != null && loginResponse.accessToken().length() > 0);
        assertTrue(loginResponse.refreshToken() != null && loginResponse.refreshToken().length() > 0);
        assertTrue(loginResponse.expiration() != null && loginResponse.expiration().length() > 0);
        assertTrue(!loginResponse.refreshToken().equals(refreshToken));
    }
}