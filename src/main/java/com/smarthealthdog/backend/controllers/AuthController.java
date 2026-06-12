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

    @PostMapping("/register")
    public ResponseEntity<Void> createUser(
            @RequestPart("request") @Valid UserCreateRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture
    ) {
        authService.registerUser(request, profilePicture);
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @PostMapping("/register/send-email-verification")
    public ResponseEntity<Void> sendEmailVerification(@Valid @RequestBody EmailVerificationCodeRequest request) {
        authService.sendEmailVerification(request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.email(),
                loginRequest.password()
            )
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        LoginResponse response = authService.generateTokens(Long.parseLong(userDetails.getUsername()));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@Valid @RequestBody RefreshTokenRequest refreshToken) {
        authService.invalidateRefreshToken(refreshToken.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshToken) {
        LoginResponse response = authService.refreshAccessToken(refreshToken.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/social/kakao")
    public ResponseEntity<Void> registerUserForSocialLoginKakao(@RequestBody CreateSocialKakaoUserRequest request) {
        authService.registerUserViaKakaoInfo(request.accessToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }
}