package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
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
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
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
public class RefreshTokenServiceTest {
    @Autowired
    private RefreshTokenService refreshTokenService; 

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JWTUtils jwtUtils;

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
    void getExpirationFromToken_ShouldReturnExpirationInDate() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);

        Date expiration = refreshTokenService.getExpirationFromToken(token);
        assertTrue(expiration != null);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void getExpirationFromTokenInISOString_ShouldReturnExpirationInISOString() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);

        String expiration = refreshTokenService.getExpirationFromTokenInISOString(token);
        assertTrue(expiration != null);
        assertTrue(!expiration.isEmpty());
        assertTrue(expiration.contains("T") && expiration.contains("Z"));
    }

    @Test
    void getUserFromToken_ShouldReturnUser() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);
        User tokenUser = refreshTokenService.getUserFromToken(token);

        assertTrue(tokenUser != null);
        assertTrue(tokenUser.getId().equals(user.getId()));
    }

    @Test
    void generateRefreshToken_ShouldCreateAndStoreRefreshToken() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        assertTrue(token != null);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);
    }

    @Test
    void generateRefreshToken_ShouldThrowException_WhenUserIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshToken(null);
        });

        assertTrue(exception.getMessage().contains("User or User ID cannot be null"));
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowException_WhenUserIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(null, "oldToken");
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowException_WhenOldTokenIsNullOrEmpty() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();

        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(user, null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(user, "");
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowException_WhenOldTokenIsInvalid() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(user, "invalidOldToken");
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldReturnNewToken_WhenOldTokenIsValid() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);

        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        String newToken = refreshTokenService.generateRefreshTokenWithOldToken(user, token);
        assertTrue(newToken != null);
        assertTrue(!newToken.equals(token));
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);
    }

    @Test
    void getTokenById_ShouldReturnNull_WhenTokenIdDoesNotExist() {
        UUID randomUuid = UUID.randomUUID();
        RefreshToken token = refreshTokenService.getTokenById(randomUuid);
        assertTrue(token == null);
    }

    @Test
    void getTokenById_ShouldReturnToken_WhenTokenIdExists() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);

        UUID tokenId = refreshTokenService.getTokenIdFromToken(token);
        assertNotNull(tokenId);

        RefreshToken foundToken = refreshTokenService.getTokenById(tokenId);
        assertNotNull(foundToken);
        assertEquals(tokenId, foundToken.getId());
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken("");
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken("invalid.token.here");
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenTokenHasEmptyClaims() {
        String emptyClaimsToken = Jwts.builder()
                                      .signWith(key)
                                      .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(emptyClaimsToken);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenTokenIdIsMissing() {
        String tokenWithoutId = Jwts.builder()
                                    .subject("1234")
                                    .signWith(key)
                                    .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(tokenWithoutId);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenTokenIdIsNotUUID() {
        String tokenWithInvalidId = Jwts.builder()
                                        .id("not-a-uuid")
                                        .subject("1234")
                                        .signWith(key)
                                        .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(tokenWithInvalidId);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldReturnTokenId_WhenTokenIsValid() {
        String validToken = Jwts.builder()
                                   .id(UUID.randomUUID().toString())
                                   .subject("1234")
                                   .signWith(key)
                                   .compact();

        UUID tokenId = refreshTokenService.getTokenIdFromToken(validToken);
        assertNotNull(tokenId);
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken("");
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken("invalid.token.here");
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenHasEmptyClaims() {
        String emptyClaimsToken = Jwts.builder()
                                      .signWith(key)
                                      .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(emptyClaimsToken);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenSubjectIsMissing() {
        String tokenWithoutSubject = Jwts.builder()
                                         .id("asdf")
                                         .signWith(key)
                                         .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithoutSubject);
        });

        String tokenWithEmptySubject = Jwts.builder()
                                          .id("asdf")
                                          .subject("")
                                          .signWith(key)
                                          .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithEmptySubject);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenUserIdIsNotNumber() {
        String tokenWithNonNumericSubject = Jwts.builder()
                                               .id(UUID.randomUUID().toString())
                                               .subject("not-a-number")
                                               .signWith(key)
                                               .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithNonNumericSubject);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIdIsMissing() {
        String tokenWithoutId = Jwts.builder()
                                    .subject("1234")
                                    .signWith(key)
                                    .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithoutId);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIdIsNotUUID() {
        String tokenWithInvalidId = Jwts.builder()
                                        .id("not-a-uuid")
                                        .subject("1234")
                                        .signWith(key)
                                        .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithInvalidId);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIdDoesNotExistInDatabase() {
        UUID randomUuid = UUID.randomUUID();
        String tokenWithNonexistentId = Jwts.builder()
                                            .id(randomUuid.toString())
                                            .subject("1234")
                                            .signWith(key)
                                            .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(tokenWithNonexistentId);
        });
    }

    @Test
    void validateRefreshToken_ShouldThrowException_WhenTokenIsExpired() {
        String expiredToken = Jwts.builder()
                                  .id(UUID.randomUUID().toString())
                                  .subject("1234")
                                  .expiration(new java.util.Date(System.currentTimeMillis() - 10000)) // Set to 10 second in the past
                                  .signWith(key)
                                  .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(expiredToken);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken("");
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken("invalid.token.here");
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenHasEmptyClaims() {
        String emptyClaimsToken = Jwts.builder()
                                      .signWith(key)
                                      .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(emptyClaimsToken);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenSubjectIsMissing() {
        String tokenWithoutSubject = Jwts.builder()
                                          .id("asdf")
                                          .signWith(key)
                                          .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(tokenWithoutSubject);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenSubjectIsNotNumber() {
        String tokenWithNonNumericSubject = Jwts.builder()
                                               .id(UUID.randomUUID().toString())
                                               .subject("not-a-number")
                                               .signWith(key)
                                               .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(tokenWithNonNumericSubject);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenIdIsMissing() {
        String tokenWithoutId = Jwts.builder()
                                    .subject("1234")
                                    .signWith(key)
                                    .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(tokenWithoutId);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenIdIsNotUUID() {
        String tokenWithInvalidId = Jwts.builder()
                                        .id("not-a-uuid")
                                        .subject("1234")
                                        .signWith(key)
                                        .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(tokenWithInvalidId);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenTokenIdDoesNotExistInDatabase() {
        UUID randomUuid = UUID.randomUUID();
        String tokenWithNonexistentId = Jwts.builder()
                                            .id(randomUuid.toString())
                                            .subject("1234")
                                            .signWith(key)
                                            .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(tokenWithNonexistentId);
        });
    }

    @Test
    void rotateRefreshToken_ShouldReturnNewToken_WhenOldTokenIsValid() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);
        refreshTokenService.validateRefreshToken(token);
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        String newToken = refreshTokenService.rotateRefreshToken(token);
        assertTrue(newToken != null);
        assertTrue(!newToken.equals(token));
        assertTrue(refreshTokenRepository.findByUser(user).size() == 1);

        Jws<Claims> oldClaims = refreshTokenService.getClaimsFromToken(token);
        Jws<Claims> newClaims = refreshTokenService.getClaimsFromToken(newToken);

        assertTrue(oldClaims.getPayload().getSubject().equals(newClaims.getPayload().getSubject()));
        assertTrue(!oldClaims.getPayload().getId().equals(newClaims.getPayload().getId()));
        assertTrue(oldClaims.getPayload().getIssuedAt() != newClaims.getPayload().getIssuedAt());

        // 새로운 토큰 생성 시, 만료 시간에 엄청 미세한 차이가 발생함
        // 시간 차이가 1초 미만인지 확인
        long timeDiff = Math.abs(newClaims.getPayload().getExpiration().getTime() -
                                  oldClaims.getPayload().getExpiration().getTime());

        assertTrue(timeDiff < 1000, "Expiration time difference is " + timeDiff + "ms");
    }

    @Test
    void revokeRefreshToken_ShouldDeleteTokenFromDatabase() {
        Optional<User> userOpt = userService.getUserByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());

        // 토큰 생성
        User user = userOpt.get();
        String token = refreshTokenService.generateRefreshToken(user);

        // 토큰이 데이터베이스에 저장되었는지 확인
        List<RefreshToken> userTokens = refreshTokenRepository.findByUser(user);
        assertTrue(userTokens.size() == 1);

        // 토큰 폐기
        refreshTokenService.revokeRefreshToken(token);
        assertTrue(refreshTokenRepository.findByUser(user).isEmpty());

        // 토큰 폐기 후, 유효성 검사 시도 시 예외 발생
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(token);
        });
    }

    @Test
    void revokeRefreshToken_ShouldThrowException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken("");
        });
    }

    @Test
    void revokeRefreshToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken("invalid.token.here");
        });
    }

    @Test
    void revokeRefreshToken_ShouldThrowException_WhenTokenHasEmptyClaims() {
        String emptyClaimsToken = Jwts.builder()
                                      .signWith(key)
                                      .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(emptyClaimsToken);
        });
    }

    @Test
    void revokeRefreshToken_ShouldThrowException_WhenTokenIdIsMissing() {
        String tokenWithoutId = Jwts.builder()
                                    .subject("some-user-id")
                                    .signWith(key)
                                    .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(tokenWithoutId);
        });
    }

    @Test
    void revokeRefreshToken_ShouldThrowException_WhenTokenIdIsNotUUID() {
        String tokenWithInvalidId = Jwts.builder()
                                        .id("not-a-uuid")
                                        .subject("some-user-id")
                                        .signWith(key)
                                        .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(tokenWithInvalidId);
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenTokenIsNullOrEmpty() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(null);
        });

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken("");
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken("invalid.token.here");
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenTokenHasEmptyClaims() {
        String emptyClaimsToken = Jwts.builder()
                                      .signWith(key)
                                      .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(emptyClaimsToken);
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenSubjectIsMissing() {
        String tokenWithoutSubject = Jwts.builder()
                                         .id("asdf")
                                         .signWith(key)
                                         .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(tokenWithoutSubject);
        });

        String tokenWithEmptySubject = Jwts.builder()
                                          .id("asdf")
                                          .subject("")
                                          .signWith(key)
                                          .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(tokenWithEmptySubject);
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenUserIdIsNotNumber() {
        String tokenWithNonNumericSubject = Jwts.builder()
                                               .subject("not-a-number")
                                               .signWith(key)
                                               .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(tokenWithNonNumericSubject);
        });
    }

    @Test
    void validateAccessToken_ShouldThrowException_WhenTokenIsExpired() {
        String expiredToken = Jwts.builder()
                                  .subject("1234")
                                  .expiration(new java.util.Date(System.currentTimeMillis() - 10000)) // Set to 10 second in the past
                                  .signWith(key)
                                  .compact();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(expiredToken);
        });
    }
}