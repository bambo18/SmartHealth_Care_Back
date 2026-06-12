package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.repositories.RoleRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceUT {

    // 1. Mock the dependency
    @Mock
    private RoleRepository roleRepository;

    // 2. Inject the mocks into the service class
    @InjectMocks
    private RoleService roleService;
    
    // Test Role objects
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Initialize predictable Role objects

        userRole = new Role();
        userRole.setId((short) 2L);
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Role for regular users");
    }

    // --- Tests for getUserRole() ---

    @Test
    void getUserRole_ShouldReturnRole_WhenRoleExists() {
        // ARRANGE: Define the mock behavior
        when(roleRepository.findByName(RoleEnum.USER))
            .thenReturn(Optional.of(userRole));

        // ACT
        Role result = roleService.getUserRole();

        // ASSERT
        assertEquals(RoleEnum.USER, result.getName(), 
                     "The returned role should be USER.");
        
        verify(roleRepository).findByName(RoleEnum.USER);
    }

    @Test
    void getUserRole_ShouldThrowRuntimeException_WhenRoleNotFound() {
        // ARRANGE: Define the mock behavior for a not found scenario
        when(roleRepository.findByName(RoleEnum.USER))
            .thenReturn(Optional.empty());

        // ACT & ASSERT: Expect a RuntimeException to be thrown
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> roleService.getUserRole(),
            "Should throw RuntimeException when role is not found.");

        assertEquals("Role 'USER' not found", exception.getMessage());
        
        // VERIFY: Ensure the repository method was called
        verify(roleRepository).findByName(RoleEnum.USER);
    }
}