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
     * @param userId
     * @return
     */
    @Transactional
    public LoginResponse generateTokens(Long userId) {
        User user = userService.getUserById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        String refreshToken = refreshTokenService.generateRefreshToken(user);
        String accessToken = refreshTokenService.generateAccessToken(refreshToken);
        String accessExpiration = refreshTokenService.getExpirationFromTokenInISOString(accessToken);

        // 유저의 리프레시 토큰 개수가 최대 개수를 초과하는지 확인하고, 초과하는 경우 오래된 토큰부터 삭제
        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);

        return new LoginResponse(accessToken, refreshToken, accessExpiration);
    }

    /**
     * 로그아웃 시 리프레시 토큰 무효화
     * @param refreshToken
     * @return
     */
    @Transactional
    public void invalidateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 리프레시 토큰이 유효한지 확인
        refreshTokenService.validateRefreshToken(refreshToken);

        User user = refreshTokenService.getUserFromToken(refreshToken);
        if (user == null) {
            throw new BadCredentialsException(ErrorCode.INVALID_JWT);
        }

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        // 해당 리프레시 토큰 삭제
        UUID tokenId = refreshTokenService.getTokenIdFromToken(refreshToken);
        refreshTokenCleanupService.deleteRefreshTokensById(tokenId);
    }

    /**
     * 리프레시 토큰을 사용하여 새로운 액세스 토큰과 리프레시 토큰 생성
     * @param refreshToken
     * @return
     * @throws BadCredentialsException 토큰이 유효하지 않을 경우 발생
     */
    @Transactional
    public LoginResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.LOGIN_FAILURE);
        }

        // 리프레시 토큰이 유효한지 확인
        refreshTokenService.validateRefreshToken(refreshToken);

        User user = refreshTokenService.getUserFromToken(refreshToken);
        if (user == null) {
            throw new BadCredentialsException(ErrorCode.LOGIN_FAILURE);
        }

        // 만료된 리프레시 토큰 삭제
        refreshTokenCleanupService.deleteUserRefreshTokensIfExpired(user);

        String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);
        String accessToken = refreshTokenService.generateAccessToken(newRefreshToken);
        String accessExpiration = refreshTokenService.getExpirationFromTokenInISOString(accessToken);

        refreshTokenCleanupService.enforceMaxRefreshTokenCount(user);

        return new LoginResponse(accessToken, newRefreshToken, accessExpiration);
    }

    /**
     * 유저 회원가입
     * @param request
     * @return 생성된 유저 객체
     */
    @Transactional
    public void registerUser(UserCreateRequest request, MultipartFile profilePicture) {
        emailVerificationService.verifyEmailToken(request.email(), request.emailVerificationToken());
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
     * 카카오 소셜 로그인으로 유저 회원가입
     * @param accessToken 카카오 액세스 토큰
     * @return 생성된 유저 객체
     */
    @Transactional
    public User registerUserViaKakaoInfo(String accessToken) {
        JsonNode kakaoInfo;
        try {
            kakaoInfo = oAuthClient.getKakaoUserInfo(accessToken);
        } catch (Exception e) {
            throw new BadCredentialsException(ErrorCode.SOCIAL_LOGIN_FAILURE);
        }

        if (kakaoInfo == null || kakaoInfo.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.SOCIAL_LOGIN_FAILURE);
        }

        if (!kakaoInfo.has("id")) {
            throw new BadCredentialsException(ErrorCode.SOCIAL_LOGIN_FAILURE);
        }

        String id = kakaoInfo.get("id").asText();
        if (id == null || id.isEmpty()) {
            throw new BadCredentialsException(ErrorCode.SOCIAL_LOGIN_FAILURE);
        }

        SocialLoginUser existingSocialLoginUser = socialLoginUserService.getKakaoSocialLoginUser(id);
        if (existingSocialLoginUser != null) {
            User existingUser = existingSocialLoginUser.getUser();
            return userService.updateUserWithKakaoUserInfo(existingUser, existingSocialLoginUser, kakaoInfo);
        }

        return userService.createUserWithKakaoUserInfo(kakaoInfo);
    }

    /**
     * 이메일 인증 토큰 전송
     * @param email
     */
    @Transactional
    public void sendEmailVerification(String email) {
        userService.getUserByEmail(email).ifPresent(_ -> {
            throw new ForbiddenException(ErrorCode.EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED);
        });

        emailVerificationService.sendEmailVerification(email);
    }
}
