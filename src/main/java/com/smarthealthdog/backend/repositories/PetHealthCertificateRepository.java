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

    /**
     * 제출 ID로 진단서를 조회한다.
     * 상세 응답에 pet 정보가 필요하므로 함께 가져온다.
     */
    @Query("""
        SELECT c FROM PetHealthCertificate c
        JOIN FETCH c.pet p
        JOIN FETCH p.owner u
        WHERE c.submission.id = :submissionId
    """)
    Optional<PetHealthCertificate> findBySubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * 중복 저장 방지용. uq_pet_health_certificates_submission 위반이
     * 500 으로 노출되기 전에 확인한다.
     */
    boolean existsBySubmissionId(UUID submissionId);
}
