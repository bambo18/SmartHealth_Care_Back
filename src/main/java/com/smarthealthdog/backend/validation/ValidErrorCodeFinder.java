package com.smarthealthdog.backend.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import lombok.RequiredArgsConstructor;


// @Valid에서 발생한 에러에 대해 적절한 ErrorCode를 반환하는 역할
// 추후 UserCreateRequest 외에 다른 DTO가 추가되면 여기에 케이스를 추가
@Component
@RequiredArgsConstructor
public class ValidErrorCodeFinder {
    private final CreateSocialKakaoUserRequestErrorCode createSocialKakaoUserRequestErrorCode;
    private final EmailVerificationCodeRequestErrorCode emailVerificationCodeRequestErrorCode;
    private final LoginRequestErrorCode loginRequestErrorCode;
    private final RefreshTokenRequestErrorCode refreshTokenRequestErrorCode;
    private final SubmissionUrineTestResultDtoErrorCode submissionUrineTestResultDtoErrorCode;
    private final SubmissionUrineTestUpdateRequestErrorCode submissionUrineTestUpdateRequestErrorCode;
    private final UserCreateRequestErrorCode userCreateRequestErrorCode;
    private final UpdateUserProfileRequestErrorCode updateUserProfileRequestErrorCode;

    public List<ErrorCode> findErrorCode(MethodArgumentNotValidException e) {
        if (e == null) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        BindingResult bindingResult = e.getBindingResult();
        if (bindingResult == null || bindingResult.getTarget() == null) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        // 에러가 발생한 객체의 클래스 이름을 가져옴
        String target = bindingResult.getTarget().getClass().getSimpleName();

        switch (target) {
            case "CreateSocialKakaoUserRequest":
                return createSocialKakaoUserRequestErrorCode.getErrorCode(e);
            case "EmailVerificationCodeRequest":
                return emailVerificationCodeRequestErrorCode.getErrorCode(e);
            case "LoginRequest":
                return loginRequestErrorCode.getErrorCode(e);
            case "RefreshTokenRequest":
                return refreshTokenRequestErrorCode.getErrorCode(e);
            case "SubmissionUrineTestResultDto":
                return submissionUrineTestResultDtoErrorCode.getErrorCode(e); 
            case "SubmissionUrineTestUpdateRequest":
                return submissionUrineTestUpdateRequestErrorCode.getErrorCode(e);
            case "UserCreateRequest":
                return userCreateRequestErrorCode.getErrorCode(e);
            case "UpdateUserProfileRequest":
                return updateUserProfileRequestErrorCode.getErrorCode(e);
            default:
                return List.of(ErrorCode.INVALID_INPUT);
        }
    }
}
