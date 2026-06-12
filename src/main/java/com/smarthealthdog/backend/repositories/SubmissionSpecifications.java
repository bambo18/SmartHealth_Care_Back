package com.smarthealthdog.backend.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SubmissionSpecifications {

    /**
     * 서브미션을 필터링하는 스펙 생성기. 삭제된 서브미션은 제외합니다.
     * @param userId 소유자 ID로 필터링
     * @param submittedFrom 제출일 시작 범위 (null 가능)
     * @param submittedTo 제출일 종료 범위 (null 가능)
     * @param completedFrom 완료일 시작 범위 (null 가능)
     * @param completedTo 완료일 종료 범위 (null 가능)
     * @return 서브미션 필터링을 위한 Specification 객체
     */
    public static Specification<Submission> filterSubmissions(
        Long userId,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory Filter: Filter by Owner ID
            // Assuming 'pet' is a field in 'Submission' and 'owner' is a field in 'Pet'
            predicates.add(cb.equal(root.get("pet").get("owner").get("id"), userId));

            // 2. Dynamic Filter: submittedAt Range
            if (submittedFrom != null && submittedTo != null) {
                // submittedFrom <= submittedAt <= submittedTo
                predicates.add(cb.between(root.get("submittedAt"), submittedFrom, submittedTo));
            } else if (submittedFrom != null) {
                // submittedAt >= submittedFrom
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            } else if (submittedTo != null) {
                // submittedAt <= submittedTo
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }
            // If both are null, no filter is added for submittedAt

            // 3. Dynamic Filter: completedAt Range
            if (completedFrom != null && completedTo != null) {
                predicates.add(cb.between(root.get("completedAt"), completedFrom, completedTo));
            } else if (completedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), completedFrom));
            } else if (completedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("completedAt"), completedTo));
            }
            // If both are null, no filter is added for completedAt
            
            // NOTE: To prevent the "N+1 select" problem, you still need to fetch joins.
            // This is done outside the predicate building, typically using the 'query' object:
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                 root.fetch("pet").fetch("owner");
            }

            // Combine all predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 서브미션을 필터링하는 스펙 생성기. 삭제된 서브미션은 제외합니다.
     * @param userId 소유자 ID로 필터링
     * @param submittedFrom 제출일 시작 범위 (null 가능)
     * @param submittedTo 제출일 종료 범위 (null 가능)
     * @param completedFrom 완료일 시작 범위 (null 가능)
     * @param completedTo 완료일 종료 범위 (null 가능)
     * @return 서브미션 필터링을 위한 Specification 객체
     */
    public static Specification<Submission> filterUserSubmissions(
        Long userId,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory Filter: Filter by Owner ID
            // Assuming 'pet' is a field in 'Submission' and 'owner' is a field in 'Pet'
            predicates.add(cb.equal(root.get("pet").get("owner").get("id"), userId));

            // 2. Dynamic Filter: submittedAt Range
            if (submittedFrom != null && submittedTo != null) {
                // submittedFrom <= submittedAt <= submittedTo
                predicates.add(cb.between(root.get("submittedAt"), submittedFrom, submittedTo));
            } else if (submittedFrom != null) {
                // submittedAt >= submittedFrom
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            } else if (submittedTo != null) {
                // submittedAt <= submittedTo
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }
            // If both are null, no filter is added for submittedAt

            // 3. Dynamic Filter: completedAt Range
            if (completedFrom != null && completedTo != null) {
                predicates.add(cb.between(root.get("completedAt"), completedFrom, completedTo));
            } else if (completedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), completedFrom));
            } else if (completedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("completedAt"), completedTo));
            }
            // If both are null, no filter is added for completedAt

            // 4. Mandatory Filter: Exclude Deleted Submissions
            predicates.add(cb.notEqual(root.get("status"), SubmissionStatus.DELETED));
            
            // NOTE: To prevent the "N+1 select" problem, you still need to fetch joins.
            // This is done outside the predicate building, typically using the 'query' object:
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                 root.fetch("pet").fetch("owner");
            }

            // Combine all predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 서브미션을 필터링하는 스펙 생성기. 삭제된 서브미션은 제외합니다.
     * @param userId 소유자 ID로 필터링
     * @param petId 펫 ID로 필터링
     * @param submittedFrom 제출일 시작 범위 (null 가능)
     * @param submittedTo 제출일 종료 범위 (null 가능)
     * @param completedFrom 완료일 시작 범위 (null 가능)
     * @param completedTo 완료일 종료 범위 (null 가능)
     * @return 서브미션 필터링을 위한 Specification 객체
     */
    public static Specification<Submission> filterPetSubmissions(
        Long userId,
        Long petId,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory Filter: Filter by Owner ID
            // Assuming 'pet' is a field in 'Submission' and 'owner' is a field in 'Pet'
            predicates.add(cb.equal(root.get("pet").get("owner").get("id"), userId));

            // 2. Mandatory Filter: Filter by Pet ID
            predicates.add(cb.equal(root.get("pet").get("id"), petId));

            // 3. Dynamic Filter: submittedAt Range
            if (submittedFrom != null && submittedTo != null) {
                // submittedFrom <= submittedAt <= submittedTo
                predicates.add(cb.between(root.get("submittedAt"), submittedFrom, submittedTo));
            } else if (submittedFrom != null) {
                // submittedAt >= submittedFrom
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            } else if (submittedTo != null) {
                // submittedAt <= submittedTo
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }
            // If both are null, no filter is added for submittedAt

            // 4. Dynamic Filter: completedAt Range
            if (completedFrom != null && completedTo != null) {
                predicates.add(cb.between(root.get("completedAt"), completedFrom, completedTo));
            } else if (completedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), completedFrom));
            } else if (completedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("completedAt"), completedTo));
            }
            // If both are null, no filter is added for completedAt
            
            // NOTE: To prevent the "N+1 select" problem, you still need to fetch joins.
            // This is done outside the predicate building, typically using the 'query' object:
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                 root.fetch("pet").fetch("owner");
            }

            // Combine all predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}