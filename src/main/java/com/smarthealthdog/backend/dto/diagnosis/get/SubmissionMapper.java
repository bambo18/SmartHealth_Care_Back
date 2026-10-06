package com.smarthealthdog.backend.dto.diagnosis.get;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.UrineMeasurement;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.utils.ImgUtils;

import lombok.RequiredArgsConstructor;

import com.smarthealthdog.backend.domain.Diagnosis;
import com.smarthealthdog.backend.domain.ConditionTranslation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmissionMapper {
    private final ImgUtils imgUtils;

    /**
     * 진단서 상세 응답을 만듭니다.
     *
     * photoUrl 을 항상 null 로 둡니다. 다른 매퍼들은 imgUtils.getImgUrl 로 채우지만,
     * ProdImgUtils.getImgUrl 은 CloudFront 도메인이 설정돼 있으면 무서명 무만료 URL 을
     * 반환합니다. 진단서에는 견주 성명 주소가 찍혀 있어 그 URL 이 나가면
     * 영구 공개 링크가 됩니다. 이미지는 GET /api/submissions/{id}/image 로만
     * 소유권 검증 후 단기 서명 URL 로 제공합니다.
     *
     * @param submission 제출 정보
     * @param certificate 진단서 기록
     * @param result 매핑된 진단서 결과
     * @return 진단서 상세 응답
     * @throws IllegalArgumentException 인자가 null 인 경우
     */
    public SubmissionDetail<HealthCertificateResult> toSubmissionDetailForCertificate(
            Submission submission,
            HealthCertificateResult result
    ) {
        if (submission == null) {
            throw new IllegalArgumentException("서브미션이 null일 수 없습니다.");
        }

        if (result == null) {
            throw new IllegalArgumentException("진단서 결과가 null일 수 없습니다.");
        }

        SubmissionSummaryPetInfo petInfo = new SubmissionSummaryPetInfo(
            submission.getPet().getId(),
            submission.getPet().getName(),
            submission.getPet().getSpecies()
        );

        return new SubmissionDetail<HealthCertificateResult>(
            submission.getId(),
            petInfo,
            submission.getType(),
            null, // 의도적으로 비웁니다 — 위 주석 참고
            submission.getStatus().name(),
            submission.getSubmittedAt(),
            submission.getCompletedAt(),
            submission.getFailureReason(),
            Set.of(result)
        );
    }

    public SubmissionDetail<DiagnosisResult> toSubmissionDetailForEyeTest(
            Submission submission, 
            List<Diagnosis> diagnoses, 
            List<ConditionTranslation> translations
    ) {
        if (submission == null) {
            throw new IllegalArgumentException("서브미션이 null일 수 없습니다.");
        }

        if (diagnoses == null) {
            throw new IllegalArgumentException("진단이 null일 수 없습니다.");
        }

        if (translations == null || translations.isEmpty()) {
            throw new IllegalArgumentException("번역이 null이거나 비어 있을 수 없습니다.");
        }

        SubmissionSummaryPetInfo petInfo = new SubmissionSummaryPetInfo(
            submission.getPet().getId(),
            submission.getPet().getName(),
            submission.getPet().getSpecies()
        );

        return new SubmissionDetail<DiagnosisResult>(
            submission.getId(),
            petInfo,
            submission.getType(),
            imgUtils.getImgUrl(submission.getPhotoUrl()),
            submission.getStatus().name(),
            submission.getSubmittedAt(),
            submission.getCompletedAt(),
            submission.getFailureReason(),
            diagnoses.stream()
                .map(diagnosis -> toDiagnosisResult(diagnosis, translations))
                .collect(Collectors.toSet())
        );
    }

    public SubmissionDetail<UrineMeasurementResult> toSubmissionDetailForUrineTest(
            Submission submission, 
            List<UrineMeasurement> measurements
    ) {
        if (submission == null) {
            throw new IllegalArgumentException("서브미션이 null일 수 없습니다.");
        }

        if (measurements == null) {
            throw new IllegalArgumentException("소변 측정값이 null일 수 없습니다.");
        }

        SubmissionSummaryPetInfo petInfo = new SubmissionSummaryPetInfo(
            submission.getPet().getId(),
            submission.getPet().getName(),
            submission.getPet().getSpecies()
        );

        return new SubmissionDetail<UrineMeasurementResult>(
            submission.getId(),
            petInfo,
            submission.getType(),
            imgUtils.getImgUrl(submission.getPhotoUrl()),
            submission.getStatus().name(),
            submission.getSubmittedAt(),
            submission.getCompletedAt(),
            submission.getFailureReason(),
            measurements.stream()
                .map(this::toUrineMeasurementResult)
                .collect(Collectors.toSet())
        );
    }

    public SubmissionPage toSubmissionPage(Page<Submission> page) {
        if (page == null) {
            throw new IllegalArgumentException("페이지가 null일 수 없습니다.");
        }

        List<SubmissionSummary> submissionSummaries = page.getContent().stream()
            .map(this::toSubmissionSummary)
            .collect(Collectors.toList());

        return new SubmissionPage(
            page.getNumber() + 1L, // 페이지 번호는 0부터 시작하므로 1을 더합니다.
            (long) page.getSize(),
            (long) page.getTotalPages(),
            page.getTotalElements(),
            page.hasNext(),
            submissionSummaries
        );
    }

    public SubmissionSummary toSubmissionSummary(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("서브미션이 null일 수 없습니다.");
        }

        return new SubmissionSummary(
            submission.getId().toString(),
            new SubmissionSummaryPetInfo(
                submission.getPet().getId(),
                submission.getPet().getName(),
                submission.getPet().getSpecies()
            ),
            submission.getStatus().name(),
            submission.getType().name(),
            submission.getSubmittedAt(),
            submission.getCompletedAt()
        );
    }

    private DiagnosisResult toDiagnosisResult(Diagnosis diagnosis, List<ConditionTranslation> translations) {
        if (diagnosis == null) {
            throw new IllegalArgumentException("진단이 null일 수 없습니다.");
        }

        if (diagnosis.getCondition() == null) {
            throw new IllegalArgumentException("진단의 상태가 null일 수 없습니다.");
        }

        ConditionTranslation translation = translations.stream()
            .filter(t -> t.getCondition().getId().equals(diagnosis.getCondition().getId()))
            .findFirst()
            .orElse(null);

        if (translation == null) {
            throw new IllegalArgumentException("선호하는 언어에 대한 상태 번역이 없습니다.");
        }

        return new DiagnosisResult(
            diagnosis.getProbability(),
            toConditionTranslation(translation)
        );
    }

    private ConditionTranslationResult toConditionTranslation(ConditionTranslation translation) {
        return new ConditionTranslationResult(
            translation.getTranslatedName(),
            translation.getTranslatedDescription()
        );
    }

    private UrineMeasurementResult toUrineMeasurementResult(UrineMeasurement measurement) {
        return new UrineMeasurementResult(
            measurement.getAnalyte().getName(),
            measurement.getValue()
        );
    }
}