package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

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
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.utils.JWTUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class RefreshTokenCleanupServiceTest {
    @Autowired
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder tokenEncoder;

    @Autowired
    private ImgUtils imgUtils;

    SecretKey key;

    @BeforeAll
    void setUp() {
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
            emailVerificationService,
            "emailVerificationSecret",
            "test-email-verification-secret"
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "allowedEmails",
            "testuser@example.com" 
        );

        Permission resetPasswordPermission = new Permission();
        resetPasswordPermission.setName(PermissionEnum.CAN_RESET_PASSWORD);
        resetPasswordPermission.setDescription("Can reset password");
        permissionRepository.save(resetPasswordPermission);

        Role userRole = new Role();
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Verified User");
        userRole.setPermissions(Set.of(resetPasswordPermission));
        roleRepository.save(userRole);

        // Create an email verification entry
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("testuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(Instant.now())
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // Create a new user before each test
        UserCreateRequest request = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            token
        );
        authService.registerUser(request, null);

        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();

        // Ensure the user is created successfully
        assertTrue(user != null);
        assertTrue(user.getId() != null);

        refreshTokenRepository.deleteAll();
        ReflectionTestUtils.setField(
            refreshTokenService, 
            "refreshTokenExpirationInDays", 
            7L // Use 'L' for a Long value
        );

        // Initialize JWTUtils
        // generate a 64 hex character secret key for HS256
        key = Jwts.SIG.HS256.key().build();
        ReflectionTestUtils.setField(
            jwtUtils, 
            "key",
            Keys.hmacShaKeyFor(key.getEncoded())
        );
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
    }

    @AfterAll
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void deleteRefreshTokensById_ShouldDeleteTokens() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);

        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        Jws<Claims> claims = refreshTokenService.getClaimsFromToken(token);
        UUID tokenId = UUID.fromString(claims.getPayload().getId());
        refreshTokenCleanupService.deleteRefreshTokensById(tokenId);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }

    @Test
    void deleteRefreshTokensByIdInNewTransaction_ShouldDeleteTokens() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();

        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        Jws<Claims> claims = refreshTokenService.getClaimsFromToken(token);
        UUID tokenId = UUID.fromString(claims.getPayload().getId());

        refreshTokenCleanupService.deleteRefreshTokensByIdInNewTransaction(tokenId);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }

    @Test
    void deleteUserRefreshTokensIfExpired_ShouldDeleteExpiredTokens() throws InterruptedException {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        Instant now = Instant.now().minus(8, ChronoUnit.DAYS);
        Date issuedAt = Date.from(now);

        User user = userOpt.get();
        UUID jti = UUID.randomUUID();
        jwtUtils.generateRefreshToken(user.getId().toString(), jti, issuedAt);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(jti);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }

    // Test enforcing max refresh token count
    @Test
    void enforceMaxRefreshTokenCount_ShouldRemoveExcessTokens_WhenUserHasMoreThanMaxTokens() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        ReflectionTestUtils.setField(
            refreshTokenCleanupService, 
            "maxRefreshTokenCount", 
            3 // Set max count to 3 for testing
        );

        // Generate 5 tokens
        for (int i = 0; i < 5; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }

        assertTrue(refreshTokenRepository.findByUser(user).size() == 5);

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldNotRemoveTokens_WhenUserHasLessThanOrEqualToMaxTokens() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();

        ReflectionTestUtils.setField(
            refreshTokenCleanupService, 
            "maxRefreshTokenCount", 
            5 // Set max count to 5 for testing
        );

        // Generate 3 tokens
        for (int i = 0; i < 3; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }

        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);

        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldDoNothing_WhenMaxCountIsNullOrNonPositive() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        ReflectionTestUtils.setField(
            refreshTokenCleanupService, 
            "maxRefreshTokenCount", 
            null // Set max count to null
        );

        // Generate 3 tokens
        for (int i = 0; i < 3; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }

        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldHandleNoTokensGracefully() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        ReflectionTestUtils.setField(
            refreshTokenCleanupService, 
            "maxRefreshTokenCount", 
            null // Set max count to null
        );

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldHandleExactlyMaxTokensGracefully() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        ReflectionTestUtils.setField(
            refreshTokenCleanupService, 
            "maxRefreshTokenCount", 
            3 // Set max count to 3 for testing
        );

        // Generate exactly 3 tokens
        for (int i = 0; i < 3; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
    }

    @Test
    void removeExpiredTokens_ShouldRemoveOnlyExpiredTokens() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        Instant now = Instant.now().minus(8, ChronoUnit.DAYS);
        Date issuedAt = Date.from(now);

        // Create three expired tokens and two valid tokens
        User user = userOpt.get();
        for (int i = 0; i < 3; i++) {
            UUID jti = UUID.randomUUID();
            jwtUtils.generateRefreshToken(user.getId().toString(), jti, issuedAt);

            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setId(jti);
            refreshToken.setUser(user);
            refreshToken.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
            refreshTokenRepository.save(refreshToken);
        }

        for (int i = 0; i < 2; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }

        assertTrue(refreshTokenRepository.findByUser(user).size() == 5);

        refreshTokenCleanupService.removeExpiredTokens();
        assertTrue(refreshTokenRepository.findByUser(user).size() == 2);
    }

    @Test
    void removeExpiredTokens_ShouldHandleNoTokensGracefully() {
        refreshTokenCleanupService.removeExpiredTokens();
        assertTrue(refreshTokenRepository.findAll().isEmpty());
    }

    @Test
    void removeExpiredTokens_ShouldHandleAllTokensValidGracefully() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        // Create three valid tokens
        for (int i = 0; i < 3; i++) {
            String token = refreshTokenService.generateRefreshToken(user);
            refreshTokenService.validateRefreshToken(token);
        }
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
        refreshTokenCleanupService.removeExpiredTokens();
        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
    }

    @Test
    void removeExpiredTokens_ShouldHandleAllTokensExpiredGracefully() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        Instant now = Instant.now().minus(8, ChronoUnit.DAYS);
        Date issuedAt = Date.from(now);
        User user = userOpt.get();
        // Create three expired tokens
        for (int i = 0; i < 3; i++) {
            UUID jti = UUID.randomUUID();
            jwtUtils.generateRefreshToken(user.getId().toString(), jti, issuedAt);

            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setId(jti);
            refreshToken.setUser(user);
            refreshToken.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
            refreshTokenRepository.save(refreshToken);
        }

        assertTrue(refreshTokenRepository.findByUser(user).size() == 3);
        refreshTokenCleanupService.removeExpiredTokens();
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());
    }
}
