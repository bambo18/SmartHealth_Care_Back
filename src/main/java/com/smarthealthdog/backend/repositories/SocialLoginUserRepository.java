package com.smarthealthdog.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.SocialLoginProvider;
import com.smarthealthdog.backend.domain.SocialLoginUser;

public interface SocialLoginUserRepository extends JpaRepository<SocialLoginUser, Long> {
    Optional<SocialLoginUser> findByProviderAndProviderUserId(SocialLoginProvider provider, String providerUserId);
}
