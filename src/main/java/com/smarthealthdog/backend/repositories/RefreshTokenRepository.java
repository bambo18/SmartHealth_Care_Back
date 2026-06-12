package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.RefreshToken;
import com.smarthealthdog.backend.domain.User;


@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    public void deleteById(UUID id);
    public void deleteByUser(User user);
    public void deleteByUserAndId(User user, UUID id);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt <= :now")
    public int deleteAllExpiredSince(@Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt <= :now AND rt.user = :user")
    public int deleteAllExpiredSinceByUser(@Param("now") Instant now, @Param("user") User user);

    public boolean existsByIdAndUser(UUID id, User user);

    public List<RefreshToken> findByUser(User user);
}
