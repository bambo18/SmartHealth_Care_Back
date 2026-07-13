package com.smarthealthdog.backend.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.smarthealthdog.backend.domain.SocialLoginUser;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.exceptions.ForbiddenException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.utils.OAuthClient;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCleanupService refreshTokenCleanupService;
    private final SocialLoginUserService socialLoginUserService;
    private final UserService userService;
    private final OAuthClient oAuthClient;

    /**
     * 로그인 시 액세스 토큰과 리프레시 토큰 생성
     *
     * @param userId 사용자 ID
     * @return 액세스 토큰과 리프레시 토큰
     */
    @Transactional
    public LoginResponse generateTokens(Long userId) {
        User user = userService.getUserById(userId)
            .orElseThrow(() ->
                new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND)
            );

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        String refreshToken = refreshTokenService.generateRefreshToken(user);
        String accessToken = refreshTokenService.generateAccessToken(refreshToken);
        String accessExpiration =
            refreshTokenService.getExpirationFromTokenInISOString(accessToken);

        // 리프레시 토큰 최대 개수 초과 시 오래된 토큰 삭제
        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);

        return new LoginResponse(
            accessToken,
            refreshToken,
            accessExpiration
        );
    }

    /**
     * 로그아웃 시 리프레시 토큰 무효화
     *
     * @param refreshToken 리프레시 토큰
     */
    @Transactional
    public void invalidateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 리프레시 토큰 유효성 검사
        refreshTokenService.validateRefreshToken(refreshToken);

        User user = refreshTokenService.getUserFromToken(refreshToken);
        if (user == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        // 현재 리프레시 토큰 삭제
        UUID tokenId =
            refreshTokenService.getTokenIdFromToken(refreshToken);

        refreshTokenCleanupService.deleteRefreshTokensById(tokenId);
    }

    /**
     * 리프레시 토큰으로 새로운 액세스 토큰과 리프레시 토큰 생성
     *
     * @param refreshToken 기존 리프레시 토큰
     * @return 새로운 토큰 응답
     */
    @Transactional
    public LoginResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.LOGIN_FAILURE);
        }

        // 리프레시 토큰 유효성 검사
        refreshTokenService.validateRefreshToken(refreshToken);

        User user = refreshTokenService.getUserFromToken(refreshToken);
        if (user == null) {
            throw new BadCredentialsException(ErrorCode.LOGIN_FAILURE);
        }

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        String newRefreshToken =
            refreshTokenService.rotateRefreshToken(refreshToken);

        String accessToken =
            refreshTokenService.generateAccessToken(newRefreshToken);

        String accessExpiration =
            refreshTokenService.getExpirationFromTokenInISOString(accessToken);

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);

        return new LoginResponse(
            accessToken,
            newRefreshToken,
            accessExpiration
        );
    }

    /**
     * 일반 사용자 회원가입
     *
     * @param request 회원가입 요청
     * @param profilePicture 프로필 이미지
     */
    @Transactional
    public void registerUser(
        UserCreateRequest request,
        MultipartFile profilePicture
    ) {
        emailVerificationService.verifyEmailToken(
            request.email(),
            request.emailVerificationToken()
        );

        User user = userService.createUser(
            request.nickname(),
            request.email(),
            request.password()
        );

        if (profilePicture != null && !profilePicture.isEmpty()) {
            userService.setUserProfilePicture(user, profilePicture);
        }
    }

    /**
     * 카카오 액세스 토큰으로 사용자 정보를 조회하고
     * 기존 사용자를 업데이트하거나 새로운 사용자를 생성
     *
     * 기존 코드와의 호환성을 위해 User 반환 방식을 유지한다.
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 조회 또는 생성된 사용자
     */
    @Transactional
    public User registerUserViaKakaoInfo(String accessToken) {
        JsonNode kakaoInfo;

        try {
            kakaoInfo = oAuthClient.getKakaoUserInfo(accessToken);
        } catch (Exception e) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        if (kakaoInfo == null || kakaoInfo.isEmpty()) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        if (!kakaoInfo.has("id")) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        String kakaoUserId = kakaoInfo.get("id").asText();

        if (kakaoUserId == null || kakaoUserId.isBlank()) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        SocialLoginUser existingSocialLoginUser =
            socialLoginUserService.getKakaoSocialLoginUser(kakaoUserId);

        if (existingSocialLoginUser != null) {
            User existingUser = existingSocialLoginUser.getUser();

            return userService.updateUserWithKakaoUserInfo(
                existingUser,
                existingSocialLoginUser,
                kakaoInfo
            );
        }

        return userService.createUserWithKakaoUserInfo(kakaoInfo);
    }

    /**
     * 카카오 로그인 처리 후 우리 서버의 JWT 발급
     *
     * 1. 카카오 액세스 토큰으로 사용자 정보 조회
     * 2. 기존 회원 조회 또는 신규 회원 생성
     * 3. 우리 서버 액세스 토큰과 리프레시 토큰 생성
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 우리 서버 로그인 토큰 응답
     */
    @Transactional
    public LoginResponse loginUserViaKakaoInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException(
                ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN
            );
        }

        User user = registerUserViaKakaoInfo(accessToken);

        return generateTokens(user.getId());
    }

    /**
     * 이메일 인증 토큰 전송
     *
     * @param email 인증 이메일 주소
     */
    @Transactional
    public void sendEmailVerification(String email) {
        userService.getUserByEmail(email).ifPresent(existingUser -> {
            throw new ForbiddenException(
                ErrorCode.EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED
            );
        });

        emailVerificationService.sendEmailVerification(email);
    }
}