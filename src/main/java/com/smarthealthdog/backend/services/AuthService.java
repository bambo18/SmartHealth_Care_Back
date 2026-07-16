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
                new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND
                )
            );

        refreshTokenCleanupService
            .deleteUserRefreshTokensIfExpired(user);

        String refreshToken =
            refreshTokenService.generateRefreshToken(user);

        String accessToken =
            refreshTokenService.generateAccessToken(
                refreshToken
            );

        String accessExpiration =
            refreshTokenService
                .getExpirationFromTokenInISOString(
                    accessToken
                );

        refreshTokenCleanupService
            .enforceMaxRefreshTokenCount(user);

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
    public void invalidateRefreshToken(
        String refreshToken
    ) {
        if (
            refreshToken == null ||
            refreshToken.isEmpty()
        ) {
            throw new BadCredentialsException(
                ErrorCode.INVALID_JWT
            );
        }

        refreshTokenService.validateRefreshToken(
            refreshToken
        );

        User user =
            refreshTokenService.getUserFromToken(
                refreshToken
            );

        if (user == null) {
            throw new BadCredentialsException(
                ErrorCode.INVALID_JWT
            );
        }

        refreshTokenCleanupService
            .deleteUserRefreshTokensIfExpired(user);

        UUID tokenId =
            refreshTokenService.getTokenIdFromToken(
                refreshToken
            );

        refreshTokenCleanupService
            .deleteRefreshTokensById(tokenId);
    }

    /**
     * 리프레시 토큰으로 새로운 토큰 생성
     *
     * @param refreshToken 기존 리프레시 토큰
     * @return 새로운 토큰 응답
     */
    @Transactional
    public LoginResponse refreshAccessToken(
        String refreshToken
    ) {
        if (
            refreshToken == null ||
            refreshToken.isEmpty()
        ) {
            throw new BadCredentialsException(
                ErrorCode.LOGIN_FAILURE
            );
        }

        refreshTokenService.validateRefreshToken(
            refreshToken
        );

        User user =
            refreshTokenService.getUserFromToken(
                refreshToken
            );

        if (user == null) {
            throw new BadCredentialsException(
                ErrorCode.LOGIN_FAILURE
            );
        }

        refreshTokenCleanupService
            .deleteUserRefreshTokensIfExpired(user);

        String newRefreshToken =
            refreshTokenService.rotateRefreshToken(
                refreshToken
            );

        String accessToken =
            refreshTokenService.generateAccessToken(
                newRefreshToken
            );

        String accessExpiration =
            refreshTokenService
                .getExpirationFromTokenInISOString(
                    accessToken
                );

        refreshTokenCleanupService
            .enforceMaxRefreshTokenCount(user);

        return new LoginResponse(
            accessToken,
            newRefreshToken,
            accessExpiration
        );
    }

    /**
     * 일반 사용자 회원가입
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

        if (
            profilePicture != null &&
            !profilePicture.isEmpty()
        ) {
            userService.setUserProfilePicture(
                user,
                profilePicture
            );
        }
    }

    /**
     * 카카오 액세스 토큰으로 사용자를 조회하거나 생성합니다.
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 조회 또는 생성된 사용자
     */
    @Transactional
    public User registerUserViaKakaoInfo(
        String accessToken
    ) {
        JsonNode kakaoInfo;

        try {
            kakaoInfo =
                oAuthClient.getKakaoUserInfo(
                    accessToken
                );
        } catch (Exception e) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        if (
            kakaoInfo == null ||
            kakaoInfo.isEmpty()
        ) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        if (!kakaoInfo.has("id")) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        String kakaoUserId =
            kakaoInfo.get("id").asText();

        if (
            kakaoUserId == null ||
            kakaoUserId.isBlank()
        ) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        SocialLoginUser existingSocialLoginUser =
            socialLoginUserService
                .getKakaoSocialLoginUser(
                    kakaoUserId
                );

        if (existingSocialLoginUser != null) {
            User existingUser =
                existingSocialLoginUser.getUser();

            return userService
                .updateUserWithKakaoUserInfo(
                    existingUser,
                    existingSocialLoginUser,
                    kakaoInfo
                );
        }

        return userService
            .createUserWithKakaoUserInfo(
                kakaoInfo
            );
    }

    /**
     * 기존 카카오 액세스 토큰 직접 전달 방식입니다.
     *
     * 기존 클라이언트와의 호환성을 위해 유지합니다.
     */
    @Transactional
    public LoginResponse loginUserViaKakaoInfo(
        String accessToken
    ) {
        if (
            accessToken == null ||
            accessToken.isBlank()
        ) {
            throw new BadCredentialsException(
                ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN
            );
        }

        User user =
            registerUserViaKakaoInfo(
                accessToken
            );

        return generateTokens(
            user.getId()
        );
    }

    /**
     * 카카오 인가 코드 로그인 방식입니다.
     *
     * 1. 인가 코드를 카카오 액세스 토큰으로 교환
     * 2. 카카오 사용자 정보 조회
     * 3. 회원 생성 또는 기존 회원 조회
     * 4. 우리 서버 JWT 발급
     *
     * @param authorizationCode 카카오 인가 코드
     * @return 우리 서버 로그인 토큰
     */
    @Transactional
    public LoginResponse loginUserViaKakaoAuthorizationCode(
        String authorizationCode
    ) {
        if (
            authorizationCode == null ||
            authorizationCode.isBlank()
        ) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        String kakaoAccessToken;

        try {
            kakaoAccessToken =
                oAuthClient.getKakaoAccessToken(
                    authorizationCode
                );
        } catch (Exception e) {
            throw new BadCredentialsException(
                ErrorCode.SOCIAL_LOGIN_FAILURE
            );
        }

        return loginUserViaKakaoInfo(
            kakaoAccessToken
        );
    }

    /**
     * 이메일 인증 토큰 전송
     */
    @Transactional
    public void sendEmailVerification(
        String email
    ) {
        userService
            .getUserByEmail(email)
            .ifPresent(existingUser -> {
                throw new ForbiddenException(
                    ErrorCode
                        .EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED
                );
            });

        emailVerificationService
            .sendEmailVerification(email);
    }
}