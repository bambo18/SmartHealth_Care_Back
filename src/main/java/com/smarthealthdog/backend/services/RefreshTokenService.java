package com.smarthealthdog.backend.services;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;
import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.repositories.RefreshTokenRepository;
import com.smarthealthdog.backend.utils.JWTUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository; 
    private final RefreshTokenCleanupService refreshTokenCleanupService;
    private final JWTUtils jwtUtils;
    private final UserService userService;

    // 리프레시 토큰 만료 기간(일)
    @Value("${jwt.refresh-token.expiration.days}")
    private Long refreshTokenExpirationInDays;

    // 유저 당 최대 리프레시 토큰 개수
    @Value("${jwt.refresh-token.max-count}")
    private Integer maxRefreshTokenCount;

    /**
     * 엑세스 토큰 생성
     * @param refreshToken
     * @return 생성된 엑세스 토큰
     * @throws BadCredentialsException 리프레시 토큰이 유효하지 않을 경우 발생
     */
    public String generateAccessToken(String refreshToken) {
        validateRefreshToken(refreshToken);

        Jws<Claims> claims = jwtUtils.getAllClaimsFromToken(refreshToken);
        String userId = claims.getPayload().getSubject();

        Date now = new Date();
        return jwtUtils.generateAccessToken(userId, now);
    }

    /**
     * 리프레시 토큰 생성
     * @param user
     * @return 생성된 리프레시 토큰
     */
    public String generateRefreshToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User or User ID cannot be null");
        }

        Instant now = Instant.now();

        Date issuedAt = Date.from(now);
        Instant expiresAt = now.plusSeconds(refreshTokenExpirationInDays * 24 * 60 * 60);

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .id(UuidCreator.getTimeOrderedEpoch()) // UUID v7 생성
            .expiresAt(expiresAt)
            .build();

        refreshTokenRepository.save(refreshToken);
        return jwtUtils.generateRefreshToken(
            user.getPublicId().toString(),
            refreshToken.getId(), 
            issuedAt
        );
    }

    /**
     * 기존 리프레시 토큰을 사용하여 새로운 리프레시 토큰 생성 (기존 토큰 폐기)
     * @param user
     * @param oldToken
     * @return 새로 발급된 리프레시 토큰
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    @Transactional
    public String generateRefreshTokenWithOldToken(User user, String oldToken) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("유저 또는 유저 ID는 null일 수 없습니다.");
        }

        if (oldToken == null || oldToken.isEmpty()) {
            throw new IllegalArgumentException("Old token은 null이거나 비어있을 수 없습니다.");
        }

        validateRefreshToken(oldToken);

        // oldToken에서 클레임 추출
        Jws<Claims> claims = jwtUtils.getAllClaimsFromToken(oldToken);
        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 JTI(토큰 ID) 추출
        String tokenId = claims.getPayload().getId();
        if (tokenId == null || tokenId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // issuedAt 추출
        Date issuedAt = claims.getPayload().getIssuedAt();
        if (issuedAt == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // expiresAt 추출
        Date expiresAtInDate = claims.getPayload().getExpiration();
        if (expiresAtInDate == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        Instant expiresAt = expiresAtInDate.toInstant();

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .id(UuidCreator.getTimeOrderedEpoch()) // UUID v7 생성
            .expiresAt(expiresAt)
            .build();

        refreshTokenRepository.save(refreshToken);

        // 기존 토큰 삭제
        refreshTokenCleanupService.deleteRefreshTokensById(uuid);

        // 새 토큰 생성
        return jwtUtils.generateRefreshToken(
            user.getPublicId().toString(), 
            refreshToken.getId(), 
            issuedAt
        );
    }

    /**
     * 토큰에서 클레임 추출
     * @param token
     * @return 토큰의 클레임
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    public Jws<Claims> getClaimsFromToken(String token) {
        try {
            return jwtUtils.getAllClaimsFromToken(token);
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }
    }

    /**
     * 토큰에서 만료 시간 추출
     * @param token
     * @return 토큰의 만료 시간
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    public Date getExpirationFromToken(String token) throws BadCredentialsException {
        try {
            Jws<Claims> claims = getClaimsFromToken(token);
            if (claims == null) {
                return null;
            }

            return claims.getPayload().getExpiration();
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }
    }

    /**
     * 토큰에서 만료 시간 추출(ISO 8601 형식)
     * @param token
     * @return 토큰의 만료 시간(ISO 8601 형식)
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    public String getExpirationFromTokenInISOString(String token) throws BadCredentialsException {
        Date date = getExpirationFromToken(token);
        if (date == null) {
            return null;
        }

        // 인스턴트로 변환
        Instant instant = date.toInstant();

        // Instant를 UTC의 OffsetDateTime으로 변환
        OffsetDateTime odt = instant.atOffset(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

        // OffsetDateTime을 ISO 8601 문자열로 포맷
        return odt.format(formatter);
    }

    /**
     * 토큰에서 토큰 ID 추출
     * @param token
     * @return 토큰 ID(UUID 형식), 토큰이 유효하지 않을 경우 null 반환
     * @throws BadCredentialsException 토큰이 유효하지 않거나, 토큰 ID가 UUID 형식이 아닐 경우 발생
     */
    public UUID getTokenIdFromToken(String token) throws BadCredentialsException {
        if (token == null || token.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        Jws<Claims> claims = getClaimsFromToken(token);
        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        String tokenId = claims.getPayload().getId();
        if (tokenId == null || tokenId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        try {
            return UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }
    }

    /**
     * 토큰에서 유저 ID 추출 후 유저 객체 조회
     * @param token
     * @return 유저 객체, 토큰이 유효하지 않거나 유저를 찾을 수 없는 경우 null 반환
     */
    public User getUserFromToken(String token) {
        Jws<Claims> claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }

        String userId = claims.getPayload().getSubject();
        if (userId == null || userId.isEmpty()) {
            return null;
        }

        return userService.getUserByPublicId(UUID.fromString(userId));
    }

    /**
     * 토큰에서 토큰 ID 추출
     * @param token
     * @return 토큰 ID(UUID 형식), 토큰이 유효하지 않을 경우 null 반환
     */
    public RefreshToken getTokenById(UUID tokenId) {
        return refreshTokenRepository.findById(tokenId).orElse(null);
    }

    /**
     * 리프레시 토큰 폐기
     * @param token
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    public void revokeRefreshToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 클레임 추출
        Jws<Claims> claims;
        try {
            claims = jwtUtils.getAllClaimsFromToken(token);
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 토큰 ID(jti) 추출
        String tokenId = claims.getPayload().getId();
        if (tokenId == null || tokenId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 ID가 UUID 형식인지 확인
        UUID uuid;
        try {
            uuid = UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 ID가 데이터베이스에서 삭제
        refreshTokenRepository.deleteById(uuid);
    }

    /**
     * 리프레시 토큰 재발급 (기존 토큰 폐기)
     * @param oldToken
     * @return 새로 발급된 리프레시 토큰
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    @Transactional
    public String rotateRefreshToken(String oldToken) throws BadCredentialsException {
        if (oldToken == null || oldToken.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        Jws<Claims> claims;
        try {
            claims = jwtUtils.getAllClaimsFromToken(oldToken);
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 사용자 ID(sub) 추출
        String userId = claims.getPayload().getSubject();
        if (userId == null || userId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        User user;
        try {
            user = userService.getUserByPublicId(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        return generateRefreshTokenWithOldToken(user, oldToken);
    }

    /**
     * 엑세스 토큰 유효성 검사
     * @param token
     * @throws BadCredentialsException 엑세스 토큰이 유효하지 않을 경우 발생
     */
    public void validateAccessToken(String token) throws BadCredentialsException {
        if (token == null || token.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 클레임 추출
        Jws<Claims> claims;
        try {
            claims = jwtUtils.getAllClaimsFromToken(token);
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 사용자 ID(sub) 추출
        String userId = claims.getPayload().getSubject();
        if (userId == null || userId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 유저 ID가 숫자 형식인지 확인
        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 만료 시간(exp) 확인
        Date expiration = claims.getPayload().getExpiration();
        if (expiration == null || expiration.before(new Date())) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }
    }

    /**
     * 리프레시 토큰 유효성 검사
     * @param token
     * @throws BadCredentialsException 리프레시 토큰이 유효하지 않을 경우 발생
     */
    @Transactional
    public void validateRefreshToken(String token) throws BadCredentialsException {
        if (token == null || token.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 클레임 추출
        Jws<Claims> claims;
        try {
            claims = jwtUtils.getAllClaimsFromToken(token);
        } catch (Exception e) {
            refreshTokenCleanupService.removeExpiredTokens();
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        if (claims == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 유저 ID(sub) 추출
        String userId = claims.getPayload().getSubject();
        if (userId == null || userId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 유저 ID가 UUID 형식인지 확인 후 유저 조회 
        User user;
        try {
            UUID publicId = UUID.fromString(userId);
            user = userService.getUserByPublicId(publicId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰에서 토큰 ID(jti) 추출
        String tokenId = claims.getPayload().getId();
        if (tokenId == null || tokenId.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 ID가 UUID 형식인지 확인
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(tokenId);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 ID가 데이터베이스에 존재하는지 확인
        RefreshToken refreshToken = refreshTokenRepository.findById(tokenUuid)
            .orElse(null);

        // 토큰이 존재하지 않거나, 토큰의 유저 ID가 토큰의 유저 ID와 일치하지 않는 경우
        if (refreshToken == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        } else if (refreshToken.getUser() == null || !refreshToken.getUser().getId().equals(user.getId())) {
            // 토큰 ID가 유효하지 않으므로 데이터베이스에서 삭제
            refreshTokenCleanupService.deleteRefreshTokensByIdInNewTransaction(tokenUuid);
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 토큰 만료 시간(exp) 확인
        Date expiration = claims.getPayload().getExpiration();
        if (expiration == null || expiration.before(new Date())) {
            // 토큰이 만료되었으므로 데이터베이스에서 삭제
            refreshTokenCleanupService.deleteRefreshTokensByIdInNewTransaction(tokenUuid);
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }
    }
}