package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.EmailVerification;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    void deleteByEmail(String email);

    EmailVerification getByEmail(String email);

    Optional<EmailVerification> findByEmail(String email);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailVerification ev SET ev.emailVerificationTries = ev.emailVerificationTries + 1 WHERE ev.email = :email")
    int incrementEmailVerificationTriesByEmail(@Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailVerification ev SET ev.emailVerificationFailCount = ev.emailVerificationFailCount + 1 WHERE ev.email = :email")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int incrementEmailVerificationFailCountByEmail(@Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailVerification ev SET ev.emailVerificationLockedAt = :lockedAt WHERE ev.email = :email")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int lockEmailVerificationByEmail(@Param("email") String email, @Param("lockedAt") Instant lockedAt);
}
