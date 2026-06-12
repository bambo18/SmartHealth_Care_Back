package com.smarthealthdog.backend.repositories;

import com.smarthealthdog.backend.domain.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository  // Optional, but recommended for clarity
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsById(Long id);

    Optional<User> findByEmail(String email);
    Optional<User> findByNickname(String nickname);
    Optional<User> findByPublicId(UUID publicId);

    @Modifying
    @Query("UPDATE User u SET u.emailVerificationFailCount = u.emailVerificationFailCount + 1 WHERE u.id = :userId")
    int incrementEmailVerificationFailCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.emailVerificationFailCount = 0 WHERE u.id = :userId")
    int resetEmailVerificationFailCount(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findUserWithRoleAndPermissionsById(Long id);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findUserWithRoleAndPermissionsByPublicId(UUID publicId);
}
