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
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;

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

    /**
     * 응답이 지연된 제출을 찾는다. 기준은 <b>PROCESSING 진입 시각</b>이다.
     *
     * 제출 시각(submittedAt)으로 재면 재시도해도 값이 바뀌지 않아, 한 번 기준선을
     * 넘은 뒤로는 조건이 영구히 참이 된다.
     */
    Page<Submission> findByStatusAndProcessingStartedAtLessThanEqualOrderByProcessingStartedAtAsc(
        SubmissionStatus status, Instant processingStartedAt, Pageable pageable);
    List<Submission> findFirst100ByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
    List<Submission> findFirst100ByStatusAndPhotoUrlIsNotOrderBySubmittedAtAsc(SubmissionStatus status, String photoUrl);

    @Query("SELECT s FROM Submission s JOIN FETCH s.pet WHERE s.status = :status AND s.photoUrl <> '' ORDER BY s.submittedAt ASC")
    List<Submission> findSubmissionsWaitingToBeProcessedByAmount(@Param("status") SubmissionStatus status, Pageable pageable);

    
    @Modifying
    @Transactional
    @Query("UPDATE Submission s SET s.status = :newStatus WHERE s.id IN :submissionIds")
    int updateStatusByIds(@Param("newStatus") SubmissionStatus newStatus, @Param("submissionIds") List<UUID> submissionIds);

    /**
     * 큐에 실은 제출을 PROCESSING 으로 바꾸면서 진입 시각을 함께 기록한다.
     *
     * 재적재할 때마다 시각이 갱신되어야 시도마다 타임아웃 시계가 새로 돈다.
     * 상태만 바꾸는 {@link #updateStatusByIds} 와 분리해 둔 이유다 — 그쪽은
     * DELETED 전환에도 쓰이므로 시각을 건드리면 안 된다.
     *
     * @param startedAt PROCESSING 진입 시각
     * @param submissionIds 대상 제출 ID 목록
     * @return 갱신된 행 수
     */
    @Modifying
    @Transactional
    @Query("UPDATE Submission s SET s.status = com.smarthealthdog.backend.domain.SubmissionStatus.PROCESSING, "
        + "s.processingStartedAt = :startedAt WHERE s.id IN :submissionIds")
    int markProcessingByIds(@Param("startedAt") Instant startedAt, @Param("submissionIds") List<UUID> submissionIds);

    Page<Submission> findAll(Specification<Submission> spec, Pageable pageable);
    Optional<Submission> findById(UUID id);
    Optional<Submission> findTopByPetOrderBySubmittedAtDesc(Pet pet);

    // 유형을 가리지 않는 위 쿼리는 제출 유형 사이에 교차 간섭을 만든다.
    // 빈도 제한은 같은 유형끼리만 비교해야 한다.
    Optional<Submission> findTopByPetAndTypeOrderBySubmittedAtDesc(Pet pet, SubmissionTypeEnum type);

    // Create a custom query that fetches Submission with Pet with User eagerly
    @Query("SELECT s FROM Submission s JOIN FETCH s.pet p JOIN FETCH p.owner u WHERE s.id = :id")
    Optional<Submission> findByIdWithPetAndUser(@Param("id") UUID id);

    @Query("SELECT s FROM Submission s JOIN FETCH s.pet p JOIN FETCH p.owner u WHERE s.id = :id AND u.id = :userId")
    Optional<Submission> findByIdWithPetAndUserAndOwnerId(@Param("id") UUID id, @Param("userId") Long userId);

    // Check if a submission exists for a given pet and a user
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Submission s JOIN s.pet p JOIN p.owner u WHERE p.id = :petId AND u.id = :ownerId")
    boolean existsByPetIdAndPetOwnerId(Long petId, Long ownerId);
}
