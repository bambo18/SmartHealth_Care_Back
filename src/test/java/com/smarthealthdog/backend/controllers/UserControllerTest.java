package com.smarthealthdog.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.dto.LoginRequest;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.UpdateUserProfileRequest;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.dto.UserProfile;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.EmailVerificationService;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.utils.JWTUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc // Provides MockMvc instance
public class UserControllerTest {
    // Test methods would go here
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder tokenEncoder;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private ImageUploader imageUploader;

    @Autowired
    private ImgUtils imgUtils;

    SecretKey key;

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    void setup() {
        // iterate over Enum values and create permissions
        // // --- General User Permissions (User & Profile) ---
        // CAN_VIEW_OWN_PROFILE("can_view_own_profile", "자신의 프로필 보기"),
        Permission viewOwnProfilePermission = new Permission();
        viewOwnProfilePermission.setName(PermissionEnum.CAN_VIEW_OWN_PROFILE);
        viewOwnProfilePermission.setDescription("자신의 프로필 보기");
        permissionRepository.save(viewOwnProfilePermission);

        // 정규 사용자 역할 생성
        Role userRole = new Role();
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Standard user role");
        userRole.setPermissions(new java.util.HashSet<>());
        roleRepository.save(userRole);

        userRole.getPermissions().add(viewOwnProfilePermission);
        roleRepository.save(userRole);

        // 밴된 사용자 역할 생성
        Role bannedRole = new Role();
        bannedRole.setName(RoleEnum.BANNED_USER);
        bannedRole.setDescription("Banned user role");
        roleRepository.save(bannedRole);

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
            "validuser@example.com," +
            "invaliduser@example.com" 
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
    void cleanUp() {
        emailVerificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterAll
    void tearDownAll() {
        // Runs once after all tests
        emailVerificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void getMyProfile_ShouldReturn401_WhenNotAuthorized() throws Exception {
        mockMvc.perform(get("/api/users/me/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfile_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        String invalidToken = "this.is.an.invalid.token";
        String response = mockMvc.perform(get("/api/users/me/profile")
            .header("Authorization", "Bearer " + invalidToken))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertNotNull(response);

        // Find the message in the response string
        assertTrue(response.contains("잘못된 JWT 토큰이거나 만료되었습니다."));
    }

    @Test
    void getMyProfile_ShouldReturn200_WhenUserIsValid() throws Exception {
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

        String accessToken = loginResponse.accessToken();

        // Finally, access the protected endpoint with the obtained token
        String profileResponse = mockMvc.perform(get("/api/users/me/profile")
            .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Assert the profile response
        assertNotNull(profileResponse);

        UserProfile userProfile = objectMapper.readValue(profileResponse, UserProfile.class);
        assertNotNull(userProfile);
        assertTrue(userProfile.email().equals("validuser@example.com"));
        assertTrue(userProfile.nickname().equals("validuser"));
        assertTrue(userProfile.profilePicture() == null, "Profile picture should be null initially, but was: " + userProfile.profilePicture());
    }

    @Test
    void updateMyProfile_ShouldReturn401_WhenNotAuthorized() throws Exception {
        mockMvc.perform(multipart("/api/users/me")
            .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMyProfile_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        String invalidToken = "this.is.an.invalid.token";
        String response = mockMvc.perform(patch("/api/users/me")
            .header("Authorization", "Bearer " + invalidToken))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertNotNull(response);
    }

    @Test
    void updateMyProfile_ShouldReturn400_WhenRequestIsInvalid() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("invaliduser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "invaliduser",
            "invaliduser@example.com",
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
        String email = "invaliduser@example.com";
        String password = "Password123!";
        String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        String accessToken = loginResponse.accessToken();

        MockMultipartFile invalidRequestFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes()
        );

        // Finally, access the protected endpoint with the obtained token
        mockMvc.perform(multipart("/api/users/me")
            .file(invalidRequestFile)
            .header("Authorization", "Bearer " + accessToken)
            .with(request -> {
                request.setMethod("PATCH");
                return request;
            }))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfile_ShouldReturn400_WhenNicknameIsTooLong() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("invaliduser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "invaliduser",
            "invaliduser@example.com",
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
        String email = "invaliduser@example.com";
        String password = "Password123!";
        String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        String accessToken = loginResponse.accessToken();

        MockMultipartFile invalidRequestFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE,
            toJson(new UpdateUserProfileRequest("a".repeat(129))).getBytes()
        );

        // Finally, access the protected endpoint with the obtained token
        mockMvc.perform(multipart("/api/users/me")
            .file(invalidRequestFile)
            .header("Authorization", "Bearer " + accessToken)
            .with(request -> {
                request.setMethod("PATCH");
                return request;
            }))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfile_ShouldReturn400_WhenNicknameIsTooShort() throws Exception {
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("invaliduser@example.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationExpiry(Instant.now().plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        // First, create a user
        UserCreateRequest createRequest = new UserCreateRequest(
            "invaliduser",
            "invaliduser@example.com",
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
        String email = "invaliduser@example.com";
        String password = "Password123!";
        String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        String accessToken = loginResponse.accessToken();

        MockMultipartFile invalidRequestFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE,
            toJson(new UpdateUserProfileRequest("ab")).getBytes()
        );

        // Finally, access the protected endpoint with the obtained token
        mockMvc.perform(multipart("/api/users/me")
            .file(invalidRequestFile)
            .header("Authorization", "Bearer " + accessToken)
            .with(request -> {
                request.setMethod("PATCH");
                return request;
            }))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyProfile_ShouldReturn200_WhenRequestIsValid() throws Exception {
        // Similar to previous test, but with valid update request
        // Implementation would go here
    }
}
