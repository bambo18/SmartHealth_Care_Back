package com.smarthealthdog.backend.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.LoginRequest;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.RefreshTokenRequest;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.services.AuthService;
import com.smarthealthdog.backend.services.CustomUserDetailsService;
import com.smarthealthdog.backend.services.EmailService;
import com.smarthealthdog.backend.services.RefreshTokenService;
import com.smarthealthdog.backend.validation.ValidErrorCodeFinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AuthControllerUT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mocking all dependencies of AuthController
    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ValidErrorCodeFinder validErrorCodeFinder;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final String BASE_URL = "/api/auth";
    private final String MOCK_EMAIL = "test@example.com";
    private final String MOCK_PASSWORD = "Password123!";
    private final Long MOCK_USER_ID = 1L;

    // --- Helper Methods ---

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
    
    // --- /login Tests ---

    @Test
    void authenticateUser_ShouldReturn200AndTokens_OnSuccess() throws Exception {
        // ARRANGE
        LoginRequest loginRequest = new LoginRequest(MOCK_EMAIL, MOCK_PASSWORD);
        LoginResponse expectedResponse = new LoginResponse(
                "mock.access.token", "mock.refresh.token", "2024-12-31T23:59:59Z");

        Authentication mockAuthentication = mock(Authentication.class);
        UserDetails mockUserDetails = mock(UserDetails.class);
        when(mockUserDetails.getUsername()).thenReturn(String.valueOf(MOCK_USER_ID));
        when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuthentication);

        // 2. Mock AuthService to return the tokens
        when(authService.generateTokens(MOCK_USER_ID))
                .thenReturn(expectedResponse);

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(expectedResponse.accessToken()))
                .andExpect(jsonPath("$.refreshToken").value(expectedResponse.refreshToken()));

        // VERIFY
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(authService, times(1)).generateTokens(MOCK_USER_ID);
    }

    @Test
    void authenticateUser_ShouldReturn401_OnInvalidCredentials() throws Exception {
        // ARRANGE
        LoginRequest loginRequest = new LoginRequest(MOCK_EMAIL, "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(loginRequest)))
                // Spring Security's default exception handler often returns 401
                .andExpect(status().isUnauthorized());

        // VERIFY: AuthService should not be called
        verify(authService, never()).generateTokens(anyLong());
    }

    @Test
    void authenticateUser_ShouldReturn400_OnInvalidInput() throws Exception {
        // ARRANGE: Assuming LoginRequest has a validation that rejects a blank email.
        LoginRequest invalidRequest = new LoginRequest("", MOCK_PASSWORD);

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(invalidRequest)))
                // @Valid failure typically results in a 400 Bad Request
                .andExpect(status().isBadRequest());

        // VERIFY: No services should be called
        verify(authenticationManager, never()).authenticate(any());
        verify(authService, never()).generateTokens(anyLong());
    }

    // --- /logout Tests ---
    @Test
    void logoutUser_ShouldReturn400_OnInvalidInput() throws Exception {
        // ARRANGE: Assuming validation rejects a blank token
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest("");

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(invalidRequest)))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).invalidateRefreshToken(any());
    }

    @Test
    void logoutUser_ShouldReturn204_OnSuccess() throws Exception {
        // ARRANGE
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");
        doNothing().when(authService).invalidateRefreshToken("valid.refresh.token");

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isNoContent()) // Expect 204 No Content
                .andExpect(content().string("")); // Response body must be empty for 204

        // VERIFY
        verify(authService, times(1)).invalidateRefreshToken("valid.refresh.token");
    }

    // --- /token/refresh Tests ---
    @Test
    void refreshToken_ShouldReturn200AndNewTokens_OnSuccess() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");
        when(authService.refreshAccessToken("valid.refresh.token"))
                .thenReturn(new LoginResponse("new.access.token", "new.refresh.token", "2024-12-31T23:59:59Z"));

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"))
                .andExpect(jsonPath("$.expiration").value("2024-12-31T23:59:59Z"));
        
        // VERIFY
        verify(authService, times(1)).refreshAccessToken("valid.refresh.token");
    }

    @Test
    void refreshToken_ShouldReturn400_OnInvalidInput() throws Exception {
        // ARRANGE: Assuming validation rejects a blank token
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest("");

        // ACT & ASSERT
        mockMvc.perform(post(BASE_URL + "/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(invalidRequest)))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).refreshAccessToken(any());
    }

    @Test
    void registerUser_ShouldReturn201_OnSuccess() throws Exception {
        // ARRANGE
        UserCreateRequest request = new UserCreateRequest(
                "testuser", "testuser@example.com", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(request).getBytes()
        );

        doNothing().when(authService).registerUser(any(UserCreateRequest.class), any(MultipartFile.class));

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
                .file(mockFile))
                .andExpect(status().isCreated())
                .andExpect(content().string("")); // Response body must be empty for 201

        // VERIFY
        verify(authService, times(1)).registerUser(any(UserCreateRequest.class), any());
    }

    @Test
    void registerUser_ShouldReturn400_OnInvalidInput() throws Exception {
        // ARRANGE: Assuming validation rejects a blank email
        UserCreateRequest invalidRequest = new UserCreateRequest(
                "testuser", "", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
                .file(mockFile))
                .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If nickname is blank
        UserCreateRequest invalidRequest2 = new UserCreateRequest(
            "", "testuser@example.com", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile2 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest2).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile2))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If password is blank
        UserCreateRequest invalidRequest3 = new UserCreateRequest(
            "testuser", "testuser@example.com", "", "emailVerificationToken");

        MockMultipartFile mockFile3 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest3).getBytes()
        );
        
        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile3))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If emailVerificationToken is blank
        UserCreateRequest invalidRequest4 = new UserCreateRequest(
            "testuser", "testuser@example.com", "Password123!", "");

        MockMultipartFile mockFile4 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest4).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile4))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If password does not meet complexity requirements
        UserCreateRequest invalidRequest5 = new UserCreateRequest(
            "testuser", "testuser@example.com", "pass", "emailVerificationToken");

        MockMultipartFile mockFile5 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest5).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile5))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If email format is invalid
        UserCreateRequest invalidRequest6 = new UserCreateRequest(
            "testuser", "invalid-email", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile6 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest6).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile6))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If nickname is too short
        UserCreateRequest invalidRequest7 = new UserCreateRequest(
            "ab", "testuser@example.com", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile7 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest7).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile7))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If nickname is too long
        UserCreateRequest invalidRequest8 = new UserCreateRequest(
            "a".repeat(129), "testuser@example.com", "Password123!", "emailVerificationToken");

        MockMultipartFile mockFile8 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest8).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile8))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If password is too short
        UserCreateRequest invalidRequest9 = new UserCreateRequest(
            "testuser", "testuser@example.com", "short", "emailVerificationToken");

        MockMultipartFile mockFile9 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest9).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile9))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request

        // VERIFY: AuthService should not be called
        verify(authService, never()).registerUser(any(), any());

        // If password is too long
        UserCreateRequest invalidRequest10 = new UserCreateRequest(
            "testuser", "testuser@example.com", "a".repeat(257), "emailVerificationToken");

        MockMultipartFile mockFile10 = new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, toJson(invalidRequest10).getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart(BASE_URL + "/register")
            .file(mockFile10))
            .andExpect(status().isBadRequest()); // Expect 400 Bad Request
    }
}