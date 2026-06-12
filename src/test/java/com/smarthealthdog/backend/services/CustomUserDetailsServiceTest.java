package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.UserCreateRequest;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;


@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
public class CustomUserDetailsServiceTest {
    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordEncoder tokenEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeAll
    void setup() {
        ReflectionTestUtils.setField(
            emailVerificationService,
            "emailVerificationSecret",
            "test-email-verification-secret"
        );

        ReflectionTestUtils.setField(
            emailVerificationService,
            "allowedEmails",
            "test@test.com" 
        );

        Permission loginPermission = new Permission();
        loginPermission.setName(PermissionEnum.CAN_RESET_PASSWORD);
        loginPermission.setDescription("Can reset password");
        permissionRepository.save(loginPermission);

        Permission startWalkPermission = new Permission();
        startWalkPermission.setName(PermissionEnum.CAN_START_WALK);
        startWalkPermission.setDescription("Can start a walk");
        permissionRepository.save(startWalkPermission);

        Role role = new Role();
        role.setName(RoleEnum.USER);
        role.setDescription("User");
        role.setPermissions(Set.of(loginPermission, startWalkPermission));

        roleRepository.save(role);

        Instant now = Instant.now();
        String token = "000000";
        String hashedToken = tokenEncoder.encode(token + "test-email-verification-secret");
        EmailVerification emailVerification = EmailVerification.builder()
            .email("test@test.com")
            .emailVerificationToken(hashedToken)
            .emailVerificationRequestedAt(now)
            .emailVerificationExpiry(now.plusSeconds(60 * 15))
            .build();

        emailVerificationRepository.save(emailVerification);

        UserCreateRequest userRequest = new UserCreateRequest(
            "testuser",
            "test@test.com",
            "Test@1234!",
            token
        );

        authService.registerUser(userRequest, null);

        User loadUser = userRepository.findByEmail("test@test.com").orElseThrow();
        if (loadUser == null) {
            throw new RuntimeException("Failed to find test user after creation");
        }
    }

    @AfterAll
    void tearDown() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        emailVerificationRepository.deleteAll();
    }

    @Test
    void loadUserByUsername_ShouldThrowException_WhenUserNotFound() {
        // 없는 이메일과 없는 ID로 테스트
        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername("nonexistentuser@gmail.com");
        });

        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername("9999");
        });

        // 이메일 형식이지만 없는 ID로 테스트
        assertThrows(BadCredentialsException.class, () -> {
            customUserDetailsService.loadUserByUsername("asdf");
        });
    }

    @Test
    @Transactional
    void loadUserByUsername_ShouldReturnUserDetails_WhenUserExists() {
        User user = userRepository.findByEmail("test@test.com").orElseThrow();
        UserDetails sameUser1 = customUserDetailsService.loadUserByUsername(user.getPublicId().toString());

        assertTrue(sameUser1.getUsername().equals(user.getId().toString()));

        Role role = roleRepository.findByName(RoleEnum.USER).orElseThrow();
        Set<Permission> permission = role.getPermissions();

        assertTrue(sameUser1.getAuthorities().size() > 0, "Expected some authorities, but got none");
        assertTrue(sameUser1.getAuthorities().size() == permission.size(),
            "Expected " + permission.size() + " authorities, but got " + sameUser1.getAuthorities().size());

        sameUser1.getAuthorities().forEach(auth -> {
            assertTrue(permission.stream().anyMatch(p -> p.getName().getName().equals(auth.getAuthority())));
        });
    }
}
