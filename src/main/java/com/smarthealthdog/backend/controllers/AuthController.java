package com.smarthealthdog.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.dto.CreateSocialKakaoUserRequest;
import com.smarthealthdog.backend.dto.EmailVerificationCodeRequest;
import com.smarthealthdog.backend.dto.KakaoAuthorizationCodeRequest;
import com.smarthealthdog.backend.dto.LoginRequest;
import com.smarthealthdog.backend.dto.LoginResponse;
import com.smarthealthdog.backend.dto.RefreshTokenRequest;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.services.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;

    /**
     * 일반 사용자 회원가입
     */
    @PostMapping("/register")
    public ResponseEntity<Void> createUser(
        @RequestPart("request")
        @Valid
        UserCreateRequest request,

        @RequestPart(
            value = "profilePicture",
            required = false
        )
        MultipartFile profilePicture
    ) {
        authService.registerUser(
            request,
            profilePicture
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(null);
    }

    /**
     * 이메일 인증 코드 전송
     */
    @PostMapping("/register/send-email-verification")
    public ResponseEntity<Void> sendEmailVerification(
        @Valid
        @RequestBody
        EmailVerificationCodeRequest request
    ) {
        authService.sendEmailVerification(
            request.email()
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(null);
    }

    /**
     * 일반 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(
        @Valid
        @RequestBody
        LoginRequest loginRequest
    ) {
        Authentication authentication =
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.email(),
                    loginRequest.password()
                )
            );

        UserDetails userDetails =
            (UserDetails) authentication.getPrincipal();

        LoginResponse response =
            authService.generateTokens(
                Long.parseLong(
                    userDetails.getUsername()
                )
            );

        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(
        @Valid
        @RequestBody
        RefreshTokenRequest refreshToken
    ) {
        authService.invalidateRefreshToken(
            refreshToken.refreshToken()
        );

        return ResponseEntity
            .status(HttpStatus.NO_CONTENT)
            .body(null);
    }

    /**
     * 액세스 토큰 재발급
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
        @Valid
        @RequestBody
        RefreshTokenRequest refreshToken
    ) {
        LoginResponse response =
            authService.refreshAccessToken(
                refreshToken.refreshToken()
            );

        return ResponseEntity.ok(response);
    }

    /**
     * 기존 카카오 액세스 토큰 직접 전달 로그인 방식입니다.
     *
     * 기존 클라이언트와의 호환성을 위해 유지합니다.
     */
    @PostMapping("/login/social/kakao")
    public ResponseEntity<LoginResponse> loginWithKakao(
        @Valid
        @RequestBody
        CreateSocialKakaoUserRequest request
    ) {
        LoginResponse response =
            authService.loginUserViaKakaoInfo(
                request.accessToken()
            );

        return ResponseEntity.ok(response);
    }

    /**
     * 카카오 인가 코드 로그인 방식입니다.
     *
     * 프론트는 카카오에서 전달받은 인가 코드만 전송합니다.
     * 백엔드가 카카오 액세스 토큰 발급부터
     * 우리 서버 JWT 생성까지 자동으로 처리합니다.
     */
    @PostMapping("/login/social/kakao/code")
    public ResponseEntity<LoginResponse> loginWithKakaoAuthorizationCode(
        @Valid
        @RequestBody
        KakaoAuthorizationCodeRequest request
    ) {
        LoginResponse response =
            authService
                .loginUserViaKakaoAuthorizationCode(
                    request.code()
                );

        return ResponseEntity.ok(response);
    }
}