package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.utils.JWTUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceUT {
    // Add unit tests for RefreshTokenService here
    @Mock  
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JWTUtils jwtUtils;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private final String MOCK_REFRESH_TOKEN = "mockRefreshToken";
    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private User mockUser = mock(User.class);

    @SuppressWarnings("unchecked")
    private Jws<Claims> mockJws = mock(Jws.class);
    private Claims mockClaims = mock(Claims.class);

    @Test
    void getClaimsFromToken_ShouldReturnClaims_WhenTokenIsValid() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);

        // ACT
        Jws<Claims> claims = refreshTokenService.getClaimsFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(claims != null);
    }

    @Test
    void getExpirationFromToken_ShouldReturnNull_WhenClaimsAreNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);

        // ACT
        Date expiration = refreshTokenService.getExpirationFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(expiration == null);
    }

    @Test
    void getExpirationFromToken_ShouldReturnNull_WhenExpirationIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(null);

        // ACT
        Date expiration = refreshTokenService.getExpirationFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(expiration == null);
    }

    @Test
    void getExpirationFromToken_ShouldThrowBadCredentialsException_WhenTokenIsInvalid() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenThrow(new IllegalArgumentException("Invalid token"));

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getExpirationFromToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void getExpirationFromToken_ShouldReturnExpirationDate_WhenTokenIsValid() {
        // ARRANGE
        Date mockExpiration = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(mockExpiration);

        // ACT
        Date expiration = refreshTokenService.getExpirationFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(expiration != null && expiration.equals(mockExpiration));
    }

    @Test
    void getExpirationFromTokenInISOString_ShouldReturnNull_WhenExpirationIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(null);

        // ACT
        String expiration = refreshTokenService.getExpirationFromTokenInISOString(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(expiration == null);
    }

    @Test
    void getExpirationFromTokenInISOString_ShouldReturnISOString_WhenExpirationIsValid() {
        // ARRANGE
        Date mockExpiration = Date.from(Instant.parse("2024-12-31T23:59:59Z"));
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getExpiration()).thenReturn(mockExpiration);

        // ACT
        String expiration = refreshTokenService.getExpirationFromTokenInISOString(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(expiration != null && expiration.equals("2024-12-31T23:59:59Z"));
    }

    @Test
    void getUserFromToken_ShouldReturnNull_WhenClaimsAreNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);

        // ACT
        User user = refreshTokenService.getUserFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(user == null);
    }

    @Test
    void getUserFromToken_ShouldReturnNull_WhenSubjectIsNullOrEmpty() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn(null);

        // ACT
        User user1 = refreshTokenService.getUserFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(user1 == null);

        // ARRANGE
        when(mockClaims.getSubject()).thenReturn("");

        // ACT
        User user2 = refreshTokenService.getUserFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(user2 == null);
    }

    @Test
    void getUserFromToken_ShouldReturnNull_WhenUserDoesNotExist() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(null);

        // ACT
        User user = refreshTokenService.getUserFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(user == null);
    }

    @Test
    void getUserFromToken_ShouldReturnUser_WhenTokenIsValid() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);

        // ACT
        User user = refreshTokenService.getUserFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(user != null && user.equals(mockUser));
    }

    @Test
    void getTokenById_ShouldReturnNull_WhenTokenDoesNotExist() {
        // ARRANGE
        when(refreshTokenRepository.findById(uuid)).thenReturn(Optional.empty());
        // ACT
        RefreshToken token = refreshTokenService.getTokenById(uuid);
        // ASSERT
        assertTrue(token == null);
    }

    @Test
    void getTokenById_ShouldReturnToken_WhenTokenExists() {
        // ARRANGE
        RefreshToken mockRefreshToken = mock(RefreshToken.class);
        when(refreshTokenRepository.findById(uuid)).thenReturn(Optional.of(mockRefreshToken));
        // ACT
        RefreshToken token = refreshTokenService.getTokenById(uuid);
        // ASSERT
        assertTrue(token != null && token.equals(mockRefreshToken));
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenClaimsAreNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);

        // ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenJTIIsNullOrEmpty() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn(null);

        // ACT
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(MOCK_REFRESH_TOKEN);
        });

        when(mockClaims.getId()).thenReturn("");
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldThrowException_WhenJTIIsInvalidUUID() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("invalid-uuid");

        // ACT
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.getTokenIdFromToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void getTokenIdFromToken_ShouldReturnUUID_WhenJTIIsValidUUID() {
        // ARRANGE
        String validUUID = "00000000-0000-0000-0000-000000000000";
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn(validUUID);

        // ACT
        UUID tokenId = refreshTokenService.getTokenIdFromToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        assertTrue(tokenId != null && tokenId.equals(UUID.fromString(validUUID)));
    }

    @Test
    void generateAccessToken_ShouldReturnNewAccessToken_WhenRefreshTokenIsValid() {
        RefreshToken mockRefreshToken = mock(RefreshToken.class);
        when(mockUser.getId()).thenReturn(1234L);
        when(mockRefreshToken.getUser()).thenReturn(mockUser);

        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);

        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);
        when(mockClaims.getId()).thenReturn(UUID.randomUUID().toString());
        when(refreshTokenRepository.findById(any(UUID.class))).thenReturn(
            Optional.of(mockRefreshToken)
        );
        when(mockClaims.getExpiration()).thenReturn(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        when(jwtUtils.generateAccessToken(any(String.class), any(Date.class))).thenReturn("newAccessToken");

        String newAccessToken = refreshTokenService.generateAccessToken(MOCK_REFRESH_TOKEN);
        assertTrue(newAccessToken != null && !newAccessToken.isEmpty());
    }

    @Test
    void validateAccessToken_ShouldThrowBadCredentialsException_WhenAccessTokenIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void validateAccessToken_ShouldThrowBadCredentialsException_WhenJTIIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn(null);

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void validateAccessToken_ShouldThrowBadCredentialsException_WhenJTIIsEmpty() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn("");

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void validateAccessToken_ShouldThrowBadCredentialsException_WhenTokenIsExpired() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn("userId");
        when(mockClaims.getExpiration()).thenReturn(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateAccessToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void generateRefreshToken_ShouldReturnNewRefreshToken() {
        User mockUser = mock(User.class);

        ReflectionTestUtils.setField(
            refreshTokenService, 
            "refreshTokenExpirationInDays", 
            7L // Use 'L' for a Long value
        );

        when(mockUser.getPublicId()).thenReturn(UUID.randomUUID());
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(jwtUtils.generateRefreshToken(
            any(String.class), 
            any(UUID.class), 
            any(Date.class)
        )).thenReturn("newRefreshToken");

        String newRefreshToken = refreshTokenService.generateRefreshToken(mockUser);
        assertTrue(newRefreshToken != null && !newRefreshToken.isEmpty());
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenOldTokenIsNullOrEmpty() {
        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, null);
        });

        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, "");
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenClaimsIsNull() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);
        when(mockUser.getId()).thenReturn(1234L);

        // ACT & ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenSubjectIsNullOrEmpty() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });

        when(mockClaims.getSubject()).thenReturn("");
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenSubjectIsNotUUID() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn("not-a-uuid");

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenJTIIsNullOrEmpty() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);

        when(mockClaims.getId()).thenReturn(null);
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });

        when(mockClaims.getId()).thenReturn("");
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenJTIIsInvalidUUID() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);
        when(mockClaims.getId()).thenReturn("invalid-uuid");

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenTokenNotInDatabase() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);
        when(mockClaims.getId()).thenReturn(UUID.randomUUID().toString());
        when(refreshTokenRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });

        verify(refreshTokenRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenTokenDoesNotHaveUserOrUserIdMismatch() {
        User anotherUser = mock(User.class);
        RefreshToken anotherUserToken = mock(RefreshToken.class);

        when(mockUser.getId()).thenReturn(1234L);

        when(anotherUserToken.getUser()).thenReturn(anotherUser);
        when(anotherUser.getId()).thenReturn(5678L);

        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(mockUser);
        when(mockClaims.getId()).thenReturn(UUID.randomUUID().toString());
        when(refreshTokenRepository.findById(any(UUID.class))).thenReturn(
            Optional.of(anotherUserToken)
        );
        doNothing().when(refreshTokenCleanupService).deleteRefreshTokensByIdInNewTransaction(any(UUID.class));

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });

        verify(refreshTokenCleanupService).deleteRefreshTokensByIdInNewTransaction(any(UUID.class));
    }

    @Test
    void generateRefreshTokenWithOldToken_ShouldThrowBadCredentialsException_WhenRefreshTokenIsExpired() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenThrow(new JwtException(MOCK_REFRESH_TOKEN));
        doNothing().when(refreshTokenCleanupService).removeExpiredTokens();

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.generateRefreshTokenWithOldToken(mockUser, MOCK_REFRESH_TOKEN);
        });

        verify(refreshTokenCleanupService).removeExpiredTokens();
    }

    @Test
    void validateRefreshToken_ShouldThrowBadCredentialsException_WhenRefreshTokenIsExpired() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        String tokenId = UUID.randomUUID().toString();
        when(mockClaims.getSubject()).thenReturn(tokenId);
        when(userService.getUserByPublicId(UUID.fromString(tokenId))).thenReturn(mockUser);
        when(mockClaims.getId()).thenReturn(UUID.randomUUID().toString());
        when(refreshTokenRepository.findById(any(UUID.class))).thenReturn(
            Optional.of(mock(RefreshToken.class))
        );
        when(mockClaims.getExpiration()).thenReturn(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void validateRefreshToken_ShouldThrowBadCredentialsException_WhenJTIIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn(null);

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void validateRefreshToken_ShouldThrowBadCredentialsException_WhenJTIIsInvalidUUID() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("invalid-uuid");

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.validateRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void revokeRefreshToken_ShouldThrowBadCredentialsException_WhenClaimsAreEmpty() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.isEmpty()).thenReturn(true);

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void revokeRefreshToken_ShouldThrowBadCredentialsException_WhenJTIIsNull() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn(null);

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void revokeRefreshToken_ShouldThrowBadCredentialsException_WhenJTIIsEmpty() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("");

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void revokeRefreshToken_ShouldThrowBadCredentialsException_WhenJTIIsInvalidUUID() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("invalid-uuid");

        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.revokeRefreshToken(MOCK_REFRESH_TOKEN);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void revokeRefreshToken_ShouldCallDeleteById_WhenJTIIsValidUUID() {
        // ARRANGE
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getId()).thenReturn("00000000-0000-0000-0000-000000000000");

        // ACT
        refreshTokenService.revokeRefreshToken(MOCK_REFRESH_TOKEN);

        // ASSERT
        verify(refreshTokenRepository).deleteById(uuid);
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenOldTokenIsNullOrEmpty() {
        // ACT & ASSERT
        BadCredentialsException thrown = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(null);
        });

        assertTrue(thrown.getErrorCode() == ErrorCode.INVALID_JWT);

        // ACT & ASSERT
        BadCredentialsException thrown2 = assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken("");
        });
        assertTrue(thrown2.getErrorCode() == ErrorCode.INVALID_JWT);
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenClaimsIsNull() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(null);

        // ACT & ASSERT
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenSubjectIsNullOrEmpty() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(MOCK_REFRESH_TOKEN);
        });

        when(mockClaims.getSubject()).thenReturn("");
        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenSubjectIsNotNumeric() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        when(mockClaims.getSubject()).thenReturn("non-numeric");

        assertThrows(BadCredentialsException.class, () -> {
            refreshTokenService.rotateRefreshToken(MOCK_REFRESH_TOKEN);
        });
    }

    @Test
    void rotateRefreshToken_ShouldThrowBadCredentialsException_WhenUserDoesNotExist() {
        when(jwtUtils.getAllClaimsFromToken(MOCK_REFRESH_TOKEN)).thenReturn(mockJws);
        when(mockJws.getPayload()).thenReturn(mockClaims);
        UUID subjectUuid = UUID.randomUUID();
        when(mockClaims.getSubject()).thenReturn(subjectUuid.toString());
        when(userService.getUserByPublicId(subjectUuid)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            refreshTokenService.rotateRefreshToken(MOCK_REFRESH_TOKEN);
        });
    }
}
