package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant; // Good practice for timestamps/expiry

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(
    name = "social_login_user", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider_id", "provider_user_id"}),
        @UniqueConstraint(columnNames = {"user_id", "provider_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLoginUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id; // Primary Key

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Links to your main User entity

    @ManyToOne(fetch = FetchType.EAGER) // Fetching the provider name eagerly is often useful
    @JoinColumn(name = "provider_id", nullable = false)
    private SocialLoginProvider provider;

    // 소셜 제공자가 할당한 고유 ID (예: Google의 'sub', Kakao의 'id')
    @Column(name = "provider_user_id", nullable = false, unique = true) 
    private String providerUserId; 

    @Column(name = "extra_data", columnDefinition = "TEXT")
    private String extraData; // JSON or other serialized data from the provider

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}