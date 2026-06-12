package com.smarthealthdog.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.EmailVerificationCodeRequest;
import com.smarthealthdog.backend.dto.LoginRequest;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.RefreshTokenRequest;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.dto.auth.EmailVerificationCodeSentEvent;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.EmailService;
import com.smarthealthdog.backend.services.EmailVerificationService;
import com.smarthealthdog.backend.services.UserService;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.utils.JWTUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc // Provides MockMvc instance
public class AuthControllerTest {
    // Test methods would go here
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder tokenEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private ImgUtils imgUtils;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ImageUploader imageUploader;

    SecretKey key;

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    void setupAll() {
        doNothing().when(emailService).sendEmailVerification(any(EmailVerificationCodeSentEvent.class));

        // Runs once before all tests
        Role bannedRole = new Role();
        bannedRole.setName(RoleEnum.BANNED_USER);
        bannedRole.setDescription("Banned user role");
        roleRepository.save(bannedRole);

        Role socialUserRole = new Role();
        socialUserRole.setName(RoleEnum.SOCIAL_ACCOUNT_USER);
        socialUserRole.setDescription("Social account user role");
        roleRepository.save(socialUserRole);

        Role deletedUserRole = new Role();
        deletedUserRole.setName(RoleEnum.DELETED_USER);
        deletedUserRole.setDescription("Deleted user role");
        roleRepository.save(deletedUserRole);

        Role userRole = new Role();
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Role for regular users");
        roleRepository.save(userRole);

        roleRepository.flush();

        // Initialize JWTUtils
        // generate a 64 hex character secret key for HS256
        key = Jwts.SIG.HS256.key().build();
        ReflectionTestUtils.setField(
            jwtUtils, 
            "key",
            Keys.hmacShaKeyFor(key.getEncoded())
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationExpiryMinutes",
            15
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationTriesCount",
            5
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationTriesDurationDays",
            1
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationFailureAttempts",
            5
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationLockDurationMinutes",
            30
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationSecret",
            "test-email-verification-secret"
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "allowedEmails",
            "test@example.com," + 
            "testuser@example.com," + 
            "refreshtestuser@example.com," + 
            "logoutuser@example.com," + 
            "validuser@example.com," + 
            "loginuser@example.com," + 
            "user@example.com"
        );

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
    }

    @AfterEach
    void tearDown() {
        // Runs after each test
        refreshTokenRepository.deleteAll();
        emailVerificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterAll
    void tearDownAll() {
        // Runs once after all tests
        emailVerificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void authenticateUser_ShouldReturn400BadRequest_WhenRequestEmailIsInvalid() throws Exception {
        // Invalid email format
        String invalidEmail = "invalid-email";
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(invalidEmail, password))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authenticateUser_ShouldReturn400BadRequest_WhenRequestEmailIsBlank() throws Exception {
        String blankEmail = "";
        String password = "Password123!";

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(blankEmail, password))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authenticateUser_ShouldReturn400BadRequest_WhenRequestPasswordIsBlank() throws Exception {
        String email = "user@example.com";
        String blankPassword = "";
        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, blankPassword))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authenticateUser_ShouldReturn401Unauthorized_WhenCredentialsAreInvalid() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("loginuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "loginuser",
            "loginuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // Now, attempt to login with incorrect password
        String email = "loginuser@example.com";
        String wrongPassword = "WrongPassword!";
        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, wrongPassword))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticateUser_ShouldReturn401Unauthorized_WhenBannedUser() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("validuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "validuser",
            "validuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // Ban the user
        User user = userRepository.findByEmail("validuser@example.com").orElseThrow();
        Role bannedRole = roleRepository.findByName(RoleEnum.BANNED_USER).orElseThrow();

        user.setRole(bannedRole);
        userRepository.save(user);

        userRepository.flush();

        User bannedUser = userRepository.findByEmail("validuser@example.com").orElseThrow();
        assertTrue(bannedUser.getRole().getName() == RoleEnum.BANNED_USER);

        // Now, attempt to login with correct credentials
        String email = "validuser@example.com";
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticateUser_ShouldReturn401Unauthorized_WhenSocialUser() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("validuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "validuser",
            "validuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // Change the user role to SOCIAL_ACCOUNT_USER
        User user = userRepository.findByEmail("validuser@example.com").orElseThrow();
        Role socialUserRole = roleRepository.findByName(RoleEnum.SOCIAL_ACCOUNT_USER).orElseThrow();
        user.setRole(socialUserRole);
        userRepository.save(user);

        userRepository.flush();

        User socialUser = userRepository.findByEmail("validuser@example.com").orElseThrow();
        assertTrue(socialUser.getRole().getName() == RoleEnum.SOCIAL_ACCOUNT_USER);

        // Now, attempt to login with correct credentials
        String email = "validuser@example.com";
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticateUser_ShouldReturn401Unauthorized_WhenDeletedUser() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("validuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "validuser",
            "validuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // Change the user role to DELETED_USER
        User user = userRepository.findByEmail("validuser@example.com").orElseThrow();
        Role deletedUserRole = roleRepository.findByName(RoleEnum.DELETED_USER).orElseThrow();
        user.setRole(deletedUserRole);
        userRepository.save(user);

        userRepository.flush();

        User deletedUser = userRepository.findByEmail("validuser@example.com").orElseThrow();
        assertTrue(deletedUser.getRole().getName() == RoleEnum.DELETED_USER);

        // Now, attempt to login with correct credentials
        String email = "validuser@example.com";
        String password = "Password123!";
        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticateUser_ShouldReturn200OKAndTokens_WhenCredentialsAreValid() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("validuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "validuser",
            "validuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // Now, attempt to login with correct credentials
        String email = "validuser@example.com";
        String password = "Password123!";
        String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        assertNotNull(loginResponse);
        assertNotNull(loginResponse.accessToken());
        assertNotNull(loginResponse.refreshToken());
        assertNotNull(loginResponse.expiration());
        assertTrue(!loginResponse.accessToken().isEmpty());
        assertTrue(!loginResponse.refreshToken().isEmpty());
        assertTrue(!loginResponse.expiration().isEmpty());
    }

    @Test
    void logoutUser_ShouldReturn400BadRequest_WhenRequestTokenIsBlank() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void logoutUser_ShouldReturn401Unauthorized_WhenRequestTokenIsInvalid() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutUser_ShouldReturn401Unauthorized_WhenRequestTokenDoesNotHaveClaims() throws Exception {
        // Create a token without claims
        String invalidToken = Jwts.builder()
                                  .signWith(key)
                                  .compact();

        RefreshTokenRequest request = new RefreshTokenRequest(invalidToken);
        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutUser_ShouldReturn401Unauthorized_WhenRequestTokenSubjectIsNotNumeric() throws Exception {
        String invalidToken = jwtUtils.generateRefreshToken(
            "non-numeric-subject", UUID.randomUUID(), Date.from(Instant.now().plusSeconds(60 * 60 * 24 * 7))); // 7 days expiry

        RefreshTokenRequest request = new RefreshTokenRequest(invalidToken);

        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutUser_ShouldReturn401Unauthorized_WhenRequestTokenJTIIsNotUUID() throws Exception {
        String invalidTokenWithNonUUIDJTI = Jwts.builder()
                                            .subject("1")
                                            .id("non-uuid-jti")
                                            .issuedAt(new Date())
                                            .expiration(Date.from(Instant.now().plusSeconds(60 * 60 * 24 * 7))) // 7 days expiry
                                            .signWith(key)
                                            .compact();

        RefreshTokenRequest request = new RefreshTokenRequest(invalidTokenWithNonUUIDJTI);
        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutUser_ShouldReturn401Unauthorized_WhenRequestTokenIsNotInDatabase() throws Exception {
        String validTokenNotInDB = jwtUtils.generateRefreshToken(
            "1", UUID.randomUUID(), Date.from(Instant.now().plusSeconds(60 * 60 * 24 * 7))); // 7 days expiry

        RefreshTokenRequest request = new RefreshTokenRequest(validTokenNotInDB);

        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutUser_ShouldReturn204NoContent_WhenRequestIsValid() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("logoutuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // Soon to be implemented: Create a user and a refresh token in the database
        UserCreateRequest createRequest = new UserCreateRequest(
            "logoutuser",
            "logoutuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        // 로그인하여 토큰 발급
        String email = "logoutuser@example.com";
        String password = "Password123!";
        String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        assertNotNull(loginResponse);
        assertNotNull(loginResponse.refreshToken());
        assertTrue(!loginResponse.refreshToken().isEmpty());

        String refreshToken = loginResponse.refreshToken();

        // 로그아웃 요청
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isNoContent());

        // 다시 한 번 로그아웃 요청 - 이미 삭제된 토큰이므로 401 Unauthorized
        mockMvc.perform(post("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sendEmailVerification_ShouldReturn400BadRequest_WhenRequestEmailIsInvalid() throws Exception {
        // Invalid email format
        EmailVerificationCodeRequest request = new EmailVerificationCodeRequest("invalid-email");

        mockMvc.perform(post("/api/auth/register/send-email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isBadRequest());

        // Blank email
        EmailVerificationCodeRequest blankEmailRequest = new EmailVerificationCodeRequest("");
        mockMvc.perform(post("/api/auth/register/send-email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(blankEmailRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sendEmailVerification_ShouldReturn401Unauthorized_WhenUserWithEmailAlreadyExists() throws Exception {
        userService.createUser("logoutuser", "logoutuser@example.com", "Password123!");

        // Now, attempt to send email verification for the same email
        EmailVerificationCodeRequest request = new EmailVerificationCodeRequest("logoutuser@example.com");
        mockMvc.perform(post("/api/auth/register/send-email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void sendEmailVerification_ShouldReturn201Created_WhenEmailIsValid() throws Exception {
        // With the valid email, it should return 201 Created up to 5 times
        EmailVerificationCodeRequest request = new EmailVerificationCodeRequest("logoutuser@example.com");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/register/send-email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isCreated());
        }

        // The 6th attempt within the lock duration should return 429 Too Many Requests
        mockMvc.perform(post("/api/auth/register/send-email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_ShouldReturn400BadRequest_WhenRequestTokenIsBlank() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/auth/token/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_ShouldReturn401Unauthorized_WhenRequestTokenIsInvalid() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        mockMvc.perform(post("/api/auth/token/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ShouldReturn401Unauthorized_WhenTokenSubjectIsNotNumeric() throws Exception {
        String refreshToken = jwtUtils.generateRefreshToken(
            "non-numeric-subject", UUID.randomUUID(), Date.from(Instant.now().minusSeconds(60 * 60 * 24 * 8))); // 8 days ago, assuming 7 days expiry

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/auth/token/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ShouldReturn401Unauthorized_WhenTokenJTIIsNotFoundInDatabase() throws Exception {
        String refreshToken = jwtUtils.generateRefreshToken(
            "1", UUID.randomUUID(), Date.from(Instant.now().minusSeconds(60 * 60 * 24 * 8))); // 8 days ago, assuming 7 days expiry

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/auth/token/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ShouldReturn401Unauthorized_WhenTokenIsExpired() throws Exception {
        Instant now = Instant.now();
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("refreshtestuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(now)
            .emailVerificationExpiry(now.plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "refreshtestuser",
            "refreshtestuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isCreated());

        Optional<User> userOpt = userRepository.findByEmail("refreshtestuser@example.com");
        assertTrue(userOpt.isPresent());

        User user = userOpt.get();
        user.setRole(roleRepository.findByName(RoleEnum.USER).get());
        userRepository.save(user);

        // Then, create a refresh token for the user
        String tokenId = UUID.randomUUID().toString();
        Instant issuedAtInInstant = Instant.now().minusSeconds(60 * 60 * 24 * 8); // 8 days ago, assuming 7 days expiry
        Instant expiredAtInInstant = issuedAtInInstant.plusSeconds(60 * 60 * 24 * 7); // 7 days expiry
        String refreshToken = jwtUtils.generateRefreshToken(
            String.valueOf(user.getId()), UUID.fromString(tokenId), Date.from(issuedAtInInstant));

        RefreshToken rf = new RefreshToken();
        rf.setId(UUID.fromString(tokenId));
        rf.setUser(user);
        rf.setExpiresAt(expiredAtInInstant);
        refreshTokenRepository.save(rf);

        RefreshToken foundToken = refreshTokenRepository.findById(UUID.fromString(tokenId)).orElse(null);
        assertNotNull(foundToken);
        assertTrue(foundToken.getUser().getId().equals(user.getId()));
        assertTrue(foundToken.getExpiresAt().isBefore(Instant.now()));

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/auth/token/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(request)))
            .andExpect(status().isUnauthorized());

        // Verify that the expired token has been deleted from the database
        Optional<RefreshToken> deletedTokenOpt = refreshTokenRepository.findById(UUID.fromString(tokenId));
        assertTrue(deletedTokenOpt.isEmpty());
    }

    @Test
    void registerUser_ShouldReturn400BadRequest_WhenRequestDataIsInvalid() throws Exception {
        // Invalid email format
        UserCreateRequest invalidEmailRequest = new UserCreateRequest(
            "testuser",
            "invalid-email",
            "Password123!",
            "000000"
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidEmailRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isBadRequest());

        // Blank nickname
        UserCreateRequest blankNicknameRequest = new UserCreateRequest(
            "",
            "testuser@example.com",
            "Password123!",
            "000000"
        );

        MockMultipartFile mockFile2 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(blankNicknameRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile2))
            .andExpect(status().isBadRequest());

        // Blank password
        UserCreateRequest blankPasswordRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "",
            "000000"
        );

        MockMultipartFile mockFile3 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(blankPasswordRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile3))
            .andExpect(status().isBadRequest());

        // Blank email verification token
        UserCreateRequest blankTokenRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            ""
        );

        MockMultipartFile mockFile4 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(blankTokenRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile4))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldLockUserOut_WhenEmailVerificationTriesExceeded() throws Exception {
        // First, send email verification 6 times to trigger lock
        EmailVerificationCodeRequest emailRequest = new EmailVerificationCodeRequest("testuser@example.com");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/register/send-email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(emailRequest)))
                .andExpect(status().isCreated());
        }

        // The 6th attempt within the lock duration should return 403 Forbidden
        mockMvc.perform(post("/api/auth/register/send-email-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(emailRequest)))
            .andExpect(status().isForbidden());

        // Now, attempt to register the user
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            "000000"
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile))
            .andExpect(status().isBadRequest());

        Optional<User> userOpt = userRepository.findByEmail("testuser@example.com");
        assertTrue(userOpt.isEmpty());

        EmailVerification evOpt = emailVerificationRepository.findByEmail("testuser@example.com").orElse(null);
        assertTrue(evOpt != null);

        assertTrue(evOpt.getEmailVerificationLockedAt() != null);
        assertTrue(evOpt.getEmailVerificationFailCount() == 0, "Fail count is " + evOpt.getEmailVerificationFailCount());
        assertTrue(evOpt.getEmailVerificationTries() == 5);
    }

    @Test
    void registerUser_ShouldReturn201Created_WhenRequestDataIsValid() throws Exception {
        Instant now = Instant.now();
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("testuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(now)
            .emailVerificationExpiry(now.plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile2 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile2))
            .andExpect(status().isCreated());

        Optional<User> userOpt = userRepository.findByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertTrue(user.getRole().getName() == RoleEnum.USER);
        assertNotNull(user.getPassword());
        assertTrue(!user.getPassword().isEmpty());
        assertTrue(!user.getPassword().equals("Password123!")); // Password should be hashed
        assertTrue(userService.checkUserPassword(user, "Password123!"));
        assertTrue(user.getEmailVerificationToken() == null || user.getEmailVerificationToken().isEmpty());

        EmailVerification evOpt = emailVerificationRepository.findByEmail("testuser@example.com").orElse(null);
        assertTrue(evOpt == null); // 이메일 인증 레코드가 삭제되었는지 확인
    }

    @Test
    void registerUser_ShouldReturn400BadRequest_WhenImageIsInvalid() throws Exception {
        Instant now = Instant.now();
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("testuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(now)
            .emailVerificationExpiry(now.plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);
        String BASE_URL = "/api/auth";

        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        MockMultipartFile invalidImageFile = new MockMultipartFile(
            "profilePicture", "profile.txt", MediaType.TEXT_PLAIN_VALUE, "This is not a valid image file".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile)
            .file(invalidImageFile))
            .andExpect(status().isBadRequest());
        
        // VERIFY: AuthService should not be called
        Optional<User> userOpt = userRepository.findByEmail("testuser@example.com");
        assertTrue(userOpt.isEmpty());
        EmailVerification evOpt = emailVerificationRepository.findByEmail("testuser@example.com").orElse(null);
        assertTrue(evOpt != null); // 이메일 인증 레코드가 삭제되지 않았는지 확인

        // Try with an image with the right extension but invalid content
        MockMultipartFile fakeImageFile = new MockMultipartFile(
            "profilePicture", "profile.jpg", MediaType.IMAGE_JPEG_VALUE, "This is not a valid image content".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile)
            .file(fakeImageFile))
            .andExpect(status().isBadRequest());

        // VERIFY: AuthService should not be called
        userOpt = userRepository.findByEmail("testuser@example.com");
        assertTrue(userOpt.isEmpty());
        evOpt = emailVerificationRepository.findByEmail("testuser@example.com").orElse(null);
        assertTrue(evOpt != null); // 이메일 인증 레코드가 삭제되지 않았는지 확인

        // Try with an image that exceeds the size limit (e.g., 6MB)
        byte[] largeImage = new byte[6 * 1024 * 1024]; // 6MB
        new Random().nextBytes(largeImage); // Fill with random bytes

        MockMultipartFile largeImageFile = new MockMultipartFile(
            "profilePicture", "large_image.jpg", MediaType.IMAGE_JPEG_VALUE, largeImage
        );
        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile)
            .file(largeImageFile))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn201Created_WhenImageIsValid() throws Exception {
        Instant now = Instant.now();
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("testuser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(now)
            .emailVerificationExpiry(now.plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // Mock S3Uploader to return a fixed URL
        ClassPathResource resource = new ClassPathResource("test-image.jpg");
        UserCreateRequest createRequest = new UserCreateRequest(
            "testuser",
            "testuser@example.com",
            "Password123!",
            token
        );

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(createRequest).getBytes()
        );

        MockMultipartFile validImageFile = new MockMultipartFile(
            "profilePicture", "test-image.jpg", MediaType.IMAGE_JPEG_VALUE, resource.getInputStream()
        );

        mockMvc.perform(multipart("/api/auth/register")
            .file(mockFile)
            .file(validImageFile))
            .andExpect(status().isCreated());

        Optional<User> userOpt = userRepository.findByEmail("testuser@example.com");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertTrue(user.getRole().getName() == RoleEnum.USER);
        assertNotNull(user.getPassword());
        assertTrue(!user.getPassword().isEmpty());
        assertTrue(!user.getPassword().equals("Password123!")); // Password should be hashed
        assertTrue(userService.checkUserPassword(user, "Password123!"));
    }
}
