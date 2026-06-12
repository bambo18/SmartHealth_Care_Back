package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceUT {
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername("nonexistent@example.com");
        });
    }

    @Test
    void loadUserByUsername_ShouldThrowException_WhenUserIdNotFound() {
        UUID nonExistentPublicId = UUID.randomUUID();
        when(userRepository.findUserWithRoleAndPermissionsByPublicId(nonExistentPublicId)).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername(nonExistentPublicId.toString());
        });
    }

    @Test
    void loadUserByUsername_ShouldThrowException_WhenUserHasNoRole() {
        User mockUser = mock(User.class);

        UUID publicId = UUID.randomUUID();
        when(userRepository.findUserWithRoleAndPermissionsByPublicId(publicId)).thenReturn(Optional.of(mockUser));
        when(mockUser.getRole()).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername(publicId.toString());
        });
    }

    @Test
    void loadUserByUsername_ShouldReturnUserDetails_WhenUserExists() {
        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        
        UUID publicId = UUID.randomUUID();
        when(userRepository.findUserWithRoleAndPermissionsByPublicId(publicId)).thenReturn(Optional.of(mockUser));
        when(mockUser.getRole()).thenReturn(mockRole);
        when(mockRole.getPermissions()).thenReturn(null);

        when(mockUser.getId()).thenReturn(1L);
        when(mockUser.getPassword()).thenReturn("hashedpassword");

        customUserDetailsService.loadUserByUsername(publicId.toString());
    }
}
