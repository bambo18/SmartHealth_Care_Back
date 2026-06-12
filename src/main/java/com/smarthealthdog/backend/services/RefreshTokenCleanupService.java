package com.smarthealthdog.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class RefreshTokenCleanupService {
    private final RefreshTokenRepository refreshTokenRepository; 

    // 리프레시 토큰 만료 기간(일)
    @Value("${jwt.refresh-token.expiration.days}")
    private Long refreshTokenExpirationInDays;

    // 유저 당 최대 리프레시 토큰 개수
    @Value("${jwt.refresh-token.max-count}")
    private Integer maxRefreshTokenCount;

    /**
     * 유저의 만료된 리프레시 토큰 삭제
     * @param user
     */
    @Transactional
    public void deleteUserRefreshTokensIfExpired(User user) {
        Instant now = Instant.now();
        refreshTokenRepository.deleteAllExpiredSinceByUser(now, user);
    }

    /**
     * 특정 리프레시 토큰 삭제 (다른 트랜잭션에서 실행)
     * @param tokenId UUID 형식의 토큰 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRefreshTokensByIdInNewTransaction(UUID tokenId) {
        refreshTokenRepository.deleteById(tokenId);
    }

    /**
     * 특정 리프레시 토큰 삭제
     * @param tokenId UUID 형식의 토큰 ID
     */
    @Transactional
    public void deleteRefreshTokensById(UUID tokenId) {
        refreshTokenRepository.deleteById(tokenId);
    }

    /**
     * 유저의 리프레시 토큰 개수가 최대 개수를 초과하는지 확인하고, 초과하는 경우 오래된 토큰부터 삭제
     * @param user
     */
    @Transactional
    public void enforceMaxRefreshTokenCount(User user) {
        if (maxRefreshTokenCount == null || maxRefreshTokenCount <= 0) {
            return;
        }

        // 유저의 모든 리프레시 토큰 조회
        List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);
        int tokenCount = tokens.size();

        // 최대 개수를 초과하는 경우 오래된 토큰부터 삭제
        if (tokenCount > maxRefreshTokenCount) {
            int lastTokenToDeleteIndex = tokenCount - maxRefreshTokenCount - 1;
            RefreshToken tokenToDelete = tokens.get(lastTokenToDeleteIndex);

            refreshTokenRepository.deleteAllExpiredSinceByUser(tokenToDelete.getExpiresAt(), user);
        }
    }

    /** 
     * 만료된 토큰 제거
     * @throws RuntimeException DB 오류 발생 시 발생
    */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteAllExpiredSince(now);
    }
}
