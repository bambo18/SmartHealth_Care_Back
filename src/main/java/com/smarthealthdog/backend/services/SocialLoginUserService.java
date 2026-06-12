package com.smarthealthdog.backend.services;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.SocialLoginUser;
import com.smarthealthdog.backend.repositories.SocialLoginUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialLoginUserService {
    private final SocialLoginUserRepository socialLoginUserRepository; 
    private final SocialLoginProviderService socialLoginProviderService;

    /**
     * 카카오 소셜 로그인 사용자를 providerUserId로 조회
     * @param providerUserId 카카오 제공자 사용자 ID
     * @return SocialLoginUser 객체 또는 null
     */
    public SocialLoginUser getKakaoSocialLoginUser(String providerUserId) {
        return socialLoginUserRepository.findByProviderAndProviderUserId(
            socialLoginProviderService.getKakaoProvider(),
            providerUserId
        ).orElse(null);
    }
}
