package com.smarthealthdog.backend.services;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.SocialLoginProvider;
import com.smarthealthdog.backend.repositories.SocialLoginProviderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialLoginProviderService {
    private final SocialLoginProviderRepository socialLoginProviderRepository;

    /**
     * 카카오 소셜 로그인 제공자 조회
     * @return SocialLoginProvider 객체
     */
    public SocialLoginProvider getKakaoProvider() {
        return socialLoginProviderRepository.findByName("KAKAO")
            .orElseThrow(() -> new IllegalStateException("카카오 소셜 로그인 제공자가 존재하지 않습니다."));
    }
}
