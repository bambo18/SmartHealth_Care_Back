package com.smarthealthdog.backend.services;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.repositories.RoleRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class RoleService {
    private final RoleRepository roleRepository;

    /**
     * 일반 유저 역할 조회
     * @return Role 객체
     */
    public Role getUserRole() {
        return roleRepository.findByName(RoleEnum.USER)
            .orElseThrow(() -> new RuntimeException("Role 'USER' not found"));
    }

    public Role getSocialUserRole() {
        return roleRepository.findByName(RoleEnum.SOCIAL_ACCOUNT_USER)
            .orElseThrow(() -> new RuntimeException("Role 'SOCIAL_USER' not found"));
    }
}