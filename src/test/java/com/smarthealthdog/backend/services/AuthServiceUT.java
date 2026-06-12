package com.smarthealthdog.backend.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AuthServiceUT {
    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerUser_shouldCallUserServiceCreateUser_andReturnUser() {
        String nickname = "test_nick";
        String email = "test@example.com";
        String password = "StrongPassword123!";
        String passwordBCryptHash = "$2a$12$MFXz7l1CM1PwjILa/FvfQOImgCIYOwp/EtyHq0RaPg4fkgu.CxIhq"; // Example hash
        String emailVerificationCode = "123456";
        
        // Use a record or class that matches your method signature
        UserCreateRequest mockRequest = new UserCreateRequest(nickname, email, password, emailVerificationCode);

        // Create a fake User object that the mock UserService will return
        User expectedUser = User.builder()
            .id(1L)
            .nickname(nickname)
            .email(email)
            .password(passwordBCryptHash)
            .build();

        doNothing().when(emailVerificationService)
            .verifyEmailToken(email, emailVerificationCode);

        // Program the mock: Tell the UserService what to return when it's called
        when(userService.createUser(nickname, email, password))
            .thenReturn(expectedUser);

        // ACT
        authService.registerUser(mockRequest, null);

        // 3. VERIFY: Assert that the AuthService correctly delegated the call 
        // to the UserService with the exact arguments from the request.
        verify(userService).createUser(
            nickname, 
            email, 
            password
        );
    }

    @Test
    void generateTokens_shouldThrowException_whenUserNotFound() {
        // ARRANGE
        Long userId = 999L; // Non-existent user ID

        when(userService.getUserById(userId)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class, () -> {
            authService.generateTokens(userId);
        });

        verify(userService).getUserById(userId);
    }

    @Test
    void generateTokens_shouldReturnLoginResponse_whenUserExists() {
        // ARRANGE
        User mockUser = mock(User.class);
        when(userService.getUserById(mockUser.getId())).thenReturn(Optional.of(mockUser));
        doNothing().when(refreshTokenCleanupService).deleteUserRefreshTokensIfExpired(mockUser);
        when(refreshTokenService.generateRefreshToken(mockUser)).thenReturn("mockRefreshToken");
        when(refreshTokenService.generateAccessToken("mockRefreshToken")).thenReturn("mockAccessToken");
        Instant now = Instant.now();
        doNothing().when(refreshTokenCleanupService).enforceMaxRefreshTokenCount(mockUser);

        // Convert Instant to OffsetDateTime in UTC
        OffsetDateTime odt = now.atOffset(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

        when(refreshTokenService.getExpirationFromTokenInISOString("mockAccessToken"))
            .thenReturn(odt.format(formatter));

        LoginResponse response = authService.generateTokens(mockUser.getId());

        assertEquals("mockAccessToken", response.accessToken());
        assertEquals("mockRefreshToken", response.refreshToken());
        assertEquals(odt.format(formatter), response.expiration());
    }

    @Test
    void invalidateRefreshToken_shouldThrowException_whenTokenIsNullOrEmpty() {
        // ARRANGE
        String nullToken = null;
        String emptyToken = "";

        // ACT & ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken(nullToken);
        });

        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken(emptyToken);
        });
    }

    @Test
    void invalidateRefreshToken_shouldThrowException_whenTokenIsInvalid() {
        // ARRANGE
        String invalidToken = "invalidToken";
        doThrow(BadCredentialsException.class)
            .when(refreshTokenService).validateRefreshToken(invalidToken);
        
        // ACT & ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken(invalidToken);
        });
    }

    @Test
    void invalidateRefreshToken_shouldThrowException_whenUserNotFoundFromToken() {
        // ARRANGE
        String validToken = "validToken";
        doNothing().when(refreshTokenService).validateRefreshToken(validToken);
        when(refreshTokenService.getUserFromToken(validToken)).thenReturn(null);
        // ACT & ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            authService.invalidateRefreshToken(validToken);
        });
        verify(refreshTokenService).validateRefreshToken(validToken);
        verify(refreshTokenService).getUserFromToken(validToken);
    }

    @Test
    void invalidateRefreshToken_shouldInvalidateTokenSuccessfully() {
        // ARRANGE
        String validToken = "validToken";
        User mockUser = mock(User.class);
        doNothing().when(refreshTokenService).validateRefreshToken(validToken);
        when(refreshTokenService.getUserFromToken(validToken)).thenReturn(mockUser);
        doNothing().when(refreshTokenCleanupService).deleteUserRefreshTokensIfExpired(mockUser);
        UUID mockTokenId = UUID.randomUUID();
        when(refreshTokenService.getTokenIdFromToken(validToken)).thenReturn(mockTokenId);
        doNothing().when(refreshTokenCleanupService).deleteRefreshTokensById(mockTokenId);
        // ACT
        authService.invalidateRefreshToken(validToken);
        // ASSERT
        verify(refreshTokenService).validateRefreshToken(validToken);
        verify(refreshTokenService).getUserFromToken(validToken);
        verify(refreshTokenCleanupService).deleteUserRefreshTokensIfExpired(mockUser);
        verify(refreshTokenService).getTokenIdFromToken(validToken);
        verify(refreshTokenCleanupService).deleteRefreshTokensById(mockTokenId);
    }

    @Test
    void refreshAccessToken_shouldReturnNewAccessToken() {
        // ARRANGE
        String oldRefreshToken = "oldRefreshToken";
        String newAccessToken = "newAccessToken";
        String newRefreshToken = "newRefreshToken";

        User mockUser = mock(User.class);
        doNothing().when(refreshTokenService).validateRefreshToken(oldRefreshToken);
        when(refreshTokenService.getUserFromToken(oldRefreshToken)).thenReturn(mockUser);
        doNothing().when(refreshTokenCleanupService).deleteUserRefreshTokensIfExpired(mockUser);
        when(refreshTokenService.rotateRefreshToken(oldRefreshToken)).thenReturn(newRefreshToken);
        when(refreshTokenService.generateAccessToken(newRefreshToken)).thenReturn(newAccessToken);
        when(refreshTokenService.getExpirationFromTokenInISOString(newAccessToken))
            .thenReturn("2024-12-31T23:59:59Z");
        doNothing().when(refreshTokenCleanupService).enforceMaxRefreshTokenCount(mockUser);

        // ACT
        LoginResponse response = authService.refreshAccessToken(oldRefreshToken);
        // ASSERT
        assertEquals(newAccessToken, response.accessToken());
        assertEquals(newRefreshToken, response.refreshToken());
        assertEquals("2024-12-31T23:59:59Z", response.expiration());
    }
}
