package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;

import jakarta.transaction.Transactional;

@Repository  // Optional, but recommended for clarity
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    @Modifying
    @Transactional
    void deleteByFailureReason(SubmissionFailureReasonEnum failureReason);

    @Modifying
    @Transactional
    void deleteByStatusAndFailureReason(SubmissionStatus status, SubmissionFailureReasonEnum failureReason);

    // 개발 전용: 상태별로 가장 오래된 100개의 서브미션 조회
    // List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
    Page<Submission> findByStatusInAndFailureReasonIn(
        List<SubmissionStatus> status, 
        List<SubmissionFailureReasonEnum> failureReason,
        Pageable pageable
    );
    Page<Submission> findByStatusAndSubmittedAtLessThanEqualOrderBySubmittedAtAsc(
        SubmissionStatus status, Instant submittedAt, Pageable pageable);
    List<Submission> findFirst100ByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
    List<Submission> findFirst100ByStatusAndPhotoUrlIsNotOrderBySubmittedAtAsc(SubmissionStatus status, String photoUrl);

    @Query("SELECT s FROM Submission s JOIN FETCH s.pet WHERE s.status = :status AND s.photoUrl <> '' ORDER BY s.submittedAt ASC")
    List<Submission> findSubmissionsWaitingToBeProcessedByAmount(@Param("status") SubmissionStatus status, Pageable pageable);

    
    @Modifying
    @Transactional
    @Query("UPDATE Submission s SET s.status = :newStatus WHERE s.id IN :submissionIds")
    int updateStatusByIds(@Param("newStatus") SubmissionStatus newStatus, @Param("submissionIds") List<UUID> submissionIds);

    Page<Submission> findAll(Specification<Submission> spec, Pageable pageable);
    Optional<Submission> findById(UUID id);
    Optional<Submission> findTopByPetOrderBySubmittedAtDesc(Pet pet);

    // Create a custom query that fetches Submission with Pet with User eagerly
    @Query("SELECT s FROM Submission s JOIN FETCH s.pet p JOIN FETCH p.owner u WHERE s.id = :id")
    Optional<Submission> findByIdWithPetAndUser(@Param("id") UUID id);

    @Query("SELECT s FROM Submission s JOIN FETCH s.pet p JOIN FETCH p.owner u WHERE s.id = :id AND u.id = :userId")
    Optional<Submission> findByIdWithPetAndUserAndOwnerId(@Param("id") UUID id, @Param("userId") Long userId);

    // Check if a submission exists for a given pet and a user
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Submission s JOIN s.pet p JOIN p.owner u WHERE p.id = :petId AND u.id = :ownerId")
    boolean existsByPetIdAndPetOwnerId(Long petId, Long ownerId);
}
