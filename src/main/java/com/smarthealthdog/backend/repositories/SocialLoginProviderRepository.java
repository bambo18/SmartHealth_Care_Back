package com.smarthealthdog.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.SocialLoginProvider;

public interface SocialLoginProviderRepository extends JpaRepository<SocialLoginProvider, Short> {
    Optional<SocialLoginProvider> findByName(String name);
}
