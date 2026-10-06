package com.smarthealthdog.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.PetHealthCertificate;

@Repository
public interface PetHealthCertificateRepository extends JpaRepository<PetHealthCertificate, Long> {

    Optional<PetHealthCertificate> findBySubmissionId(UUID submissionId);

    /**
     * 진단서를 제출·반려동물과 함께 조회한다. 응답 DTO 가 반려동물 정보를 필요로 하므로
     * LAZY 연관을 미리 당겨 N+1 을 피한다.
     */
    @Query("SELECT c FROM PetHealthCertificate c JOIN FETCH c.submission s JOIN FETCH c.pet p WHERE s.id = :submissionId")
    Optional<PetHealthCertificate> findBySubmissionIdWithPet(@Param("submissionId") UUID submissionId);
}
