package com.smarthealthdog.backend.repositories;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;

@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryTest {
    @Autowired
    private RoleRepository roleRepository;

    // 테스트 실행 전에 필요한 데이터 설정
    @BeforeEach
    void setUp() {
        // 1. Create a new Role instance
        Role role = new Role(); // Use your Role constructor/setter
        role.setName(RoleEnum.USER);
        role.setDescription("Role for unverified users");

        roleRepository.save(role);

        // Ensure the data is immediately available in the context (optional, but good for safety)
        roleRepository.flush(); 
    }

    @Test
    void findByName_ShouldReturnRole_WhenNameExists() {
        // Act
        Optional<Role> foundRole = roleRepository.findByName(RoleEnum.USER);

        // Assert
        assertTrue(foundRole.isPresent());
        assertEquals(RoleEnum.USER, foundRole.get().getName());
    }
}