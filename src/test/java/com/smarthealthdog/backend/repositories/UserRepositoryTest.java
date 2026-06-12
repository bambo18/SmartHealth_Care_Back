package com.smarthealthdog.backend.repositories;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.github.f4b6a3.uuid.UuidCreator;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;

@TestInstance(Lifecycle.PER_CLASS)
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private Role testRole;

    // 테스트 실행 전에 필요한 데이터 설정
    @BeforeAll
    void setUp() {
        // 1. Create a new Role instance
        Role role = new Role(); // Use your Role constructor/setter
        role.setName(RoleEnum.USER);
        role.setDescription("Role for regular users");

        // 2. Persist the Role to the database
        this.testRole = roleRepository.save(role);
        
        // Ensure the data is immediately available in the context (optional, but good for safety)
        roleRepository.flush(); 

        // 3. Create permissions and associate them with the role if needed
        Permission perm1 = new Permission();
        perm1.setName(PermissionEnum.CAN_RESET_PASSWORD);
        perm1.setDescription("Permission to reset password");
        permissionRepository.save(perm1);

        role.setPermissions(new java.util.HashSet<>());
        role.getPermissions().add(perm1);
        roleRepository.save(role);
    }

    @AfterAll
    void tearDown() {
        // Clean up test data
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    void findByEmail_ShouldReturnUser_WhenEmailExists() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        User savedUser = userRepository.save(user);

        // Act
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Assert
        assertTrue(foundUser.isPresent());
        assertEquals(savedUser.getEmail(), foundUser.get().getEmail());
    }

    @Test
    void findByNickname_ShouldReturnUser_WhenNicknameExists() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        User savedUser = userRepository.save(user);

        // Act
        Optional<User> foundUser = userRepository.findByNickname("testuser");

        // Assert
        assertTrue(foundUser.isPresent());
        assertEquals(savedUser.getNickname(), foundUser.get().getNickname());
    }

    @Test
    void existsByEmail_ShouldReturnTrue_WhenEmailExists() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsByEmail("test@example.com");

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByNickname_ShouldReturnTrue_WhenNicknameExists() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsByNickname("testuser");

        // Assert
        assertTrue(exists);
    }
    @Test
    void existsById_ShouldReturnTrue_WhenIdExists() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsById(user.getId());

        // Assert
        assertTrue(exists);
    }

    @Test
    void incrementEmailVerificationFailCount_ShouldIncrementCount() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        // Initial fail count is 0 of Short type
        user.setRole(this.testRole); // Use the persisted role
        User savedUser = userRepository.save(user);

        assertEquals( 0, savedUser.getEmailVerificationFailCount());

        // Act
        int newCount = userRepository.incrementEmailVerificationFailCount(savedUser.getId());

        // Assert
        assertEquals(1, newCount);
    }

    @Test
    void resetEmailVerificationFailCount_ShouldResetCount() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        User savedUser = userRepository.save(user);

        // Act
        userRepository.resetEmailVerificationFailCount(savedUser.getId());

        // Assert
        assertEquals(0, savedUser.getEmailVerificationFailCount());
    }

    @Test
    void findUserWithRoleAndPermissionsById_ShouldReturnUserWithRoleAndPermissions() {
        // Arrange: Insert test data using the repository itself
        User user = new User();
        user.setPublicId(UuidCreator.getTimeOrderedEpoch());
        user.setNickname("testuser");
        user.setEmail("test@example.com");
        user.setPassword("hashedpassword");
        user.setRole(this.testRole); // Use the persisted role

        User savedUser = userRepository.save(user);

        // Act
        Optional<User> foundUser = userRepository.findUserWithRoleAndPermissionsById(savedUser.getId());

        // Assert
        assertTrue(foundUser.isPresent());
        assertEquals(savedUser.getId(), foundUser.get().getId());
        assertEquals(savedUser.getRole(), foundUser.get().getRole());
        assertTrue(foundUser.get().getRole().getPermissions().containsAll(this.testRole.getPermissions()));        
        assertTrue(foundUser.get().getRole().getPermissions().size() > 0);
        assertEquals(foundUser.get().getRole().getPermissions().iterator().next().getName(), PermissionEnum.CAN_RESET_PASSWORD);
    }
}