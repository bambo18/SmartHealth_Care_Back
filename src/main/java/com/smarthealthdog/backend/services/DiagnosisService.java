package com.smarthealthdog.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.Diagnosis;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiagnosisService {
    private final ConditionService conditionService;
    private final DiagnosisRepository diagnosisRepository;

    /**
     * 특정 제출 ID에 대한 모든 진단 결과를 조회합니다.
     * @param submissionId 제출 ID
     * @return 해당 제출에 대한 진단 결과 목록
     */
    public List<Diagnosis> getDiagnosesBySubmissionId(UUID submissionId) {
        return diagnosisRepository.findBySubmissionIdWithCondition(submissionId);
    }

    /**
     * 제출된 진단 결과를 처리하고 데이터베이스에 저장합니다.
     * @param submission 제출 정보
     * @param request 제출 결과 요청 객체
     */
    @Transactional
    public void processInferenceResult(Submission submission, SubmissionResultRequest request) {
        Pet pet = submission.getPet();

        // 반려동물의 종에 해당하는 모든 질병 조건을 미리 로드
        List<Condition> conditions = conditionService.getConditionsBySpecies(pet.getSpecies());
        
        // 각 결과 DTO를 Diagnosis 엔티티로 변환하고 저장
        List<Diagnosis> diagnosesToSave = request.getResults().stream()
            .map(resultDto -> {
                // 결과 DTO의 질병 이름에 해당하는 Condition 엔티티 찾기
                Condition condition = conditions.stream()
                    .filter(c -> c.getName().equals(resultDto.getDisease()))
                    .findFirst()
                    .orElse(null);

                if (condition == null) {
                    return null;
                }

                // Diagnosis 엔티티 생성
                return Diagnosis.builder()
                    .submission(submission)
                    .condition(condition)
                    .probability(resultDto.getProbability())
                    .modelMd5Hash(resultDto.getModelMd5Hash())
                    .build();
            })
            .collect(Collectors.toList()); // Collect all built entities

        // 모든 진단이 null인 경우 예외 처리
        if (diagnosesToSave.stream().allMatch(d -> d == null)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // null이 아닌 진단만 필터링하여 저장
        diagnosesToSave = diagnosesToSave.stream()
            .filter(d -> d != null)
            .collect(Collectors.toList());

        diagnosisRepository.saveAll(diagnosesToSave);
    }
}
