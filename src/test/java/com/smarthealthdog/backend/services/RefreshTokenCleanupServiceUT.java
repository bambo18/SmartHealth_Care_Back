package com.smarthealthdog.backend.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenCleanupServiceUT {
    @Mock
    private RefreshTokenRepository refreshTokenRepository; 

    @InjectMocks
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @Test
    void deleteUserRefreshTokensIfExpired_ShouldInvokeRepositoryMethod() {
        User mockUser = mock(User.class);
        when(refreshTokenRepository.deleteAllExpiredSinceByUser(any(Instant.class), any(User.class))).thenReturn(0);

        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(mockUser);

        verify(refreshTokenRepository).deleteAllExpiredSinceByUser(any(Instant.class), any(User.class));
    }

    @Test
    void deleteRefreshTokensByIdInNewTransaction_ShouldInvokeRepositoryMethod() {
        refreshTokenCleanupService.deleteRefreshTokensByIdInNewTransaction(null);

        verify(refreshTokenRepository).deleteById(any());
    }

    @Test
    void deleteRefreshTokensById_ShouldInvokeRepositoryMethod() {
        refreshTokenCleanupService.deleteRefreshTokensById(null);

        verify(refreshTokenRepository).deleteById(any());
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldInvokeRepositoryMethod() {
        ReflectionTestUtils.setField(refreshTokenCleanupService, "maxRefreshTokenCount", 3);
        User mockUser = mock(User.class);
        when(refreshTokenRepository.findByUser(any(User.class))).thenReturn(java.util.Collections.emptyList());

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(mockUser);

        verify(refreshTokenRepository).findByUser(any(User.class));
    }

    @Test
    void enforceMaxRefreshTokenCount_ShouldRemoveExcessTokens_WhenUserHasMoreThanMaxTokens() {
        ReflectionTestUtils.setField(refreshTokenCleanupService, "maxRefreshTokenCount", 2);
        User mockUser = mock(User.class);
        RefreshToken token1 = mock(RefreshToken.class);
        when(token1.getExpiresAt()).thenReturn(Instant.now().minusSeconds(300));
        when(refreshTokenRepository.findByUser(any(User.class))).thenReturn(java.util.Arrays.asList(token1, mock(RefreshToken.class), mock(RefreshToken.class)));
        when(refreshTokenRepository.deleteAllExpiredSinceByUser(any(Instant.class), any(User.class))).thenReturn(0);

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(mockUser);
        verify(refreshTokenRepository).deleteAllExpiredSinceByUser(any(Instant.class), eq(mockUser));
    }

    @Test
    void removeExpiredTokens_ShouldInvokeRepositoryMethod() {
        when(refreshTokenRepository.deleteAllExpiredSince(any(Instant.class))).thenReturn(0);

        refreshTokenCleanupService.removeExpiredTokens();

        verify(refreshTokenRepository).deleteAllExpiredSince(any(Instant.class));
    }
}
        
