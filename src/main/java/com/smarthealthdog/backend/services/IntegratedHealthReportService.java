package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/** Read-only weekly/monthly report. No AI inference or new diagnosis is performed. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegratedHealthReportService {
    public enum PeriodType { WEEK, MONTH }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;
    private final PetService petService;

    public Map<String, Object> get(Long petId, Long ownerId, PeriodType periodType, LocalDate selectedDate) {
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        LocalDate fromDate = periodType == PeriodType.WEEK
                ? selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : selectedDate.withDayOfMonth(1);
        LocalDate toDateExclusive = periodType == PeriodType.WEEK
                ? fromDate.plusWeeks(1) : fromDate.plusMonths(1);
        Instant start = fromDate.atStartOfDay(KST).toInstant();
        Instant end = toDateExclusive.atStartOfDay(KST).toInstant();

        // submissions.submitted_at is timestamp WITHOUT time zone. Current VPS appears to store
        // UTC-naive timestamps (e.g. 09:43 UTC for an 18:43 KST upload). Verify before production use.
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("petId", petId)
                .addValue("fromUtc", start.toString())
                .addValue("toUtc", end.toString());

        List<Map<String, Object>> selfChecks = jdbc.queryForList("""
            SELECT h.id AS "recordId", h.created_at AS "recordedAt", h.overall_stage AS "overallStage",
                   COUNT(a.id) AS "areaCount", COALESCE(BOOL_OR(a.urgent_hit), FALSE) AS "urgentHit"
            FROM health_check_records h
            LEFT JOIN health_check_areas a ON a.record_id = h.id
            WHERE h.pet_id = :petId
              AND h.created_at >= CAST(:fromUtc AS TIMESTAMPTZ)
              AND h.created_at < CAST(:toUtc AS TIMESTAMPTZ)
            GROUP BY h.id, h.created_at, h.overall_stage
            ORDER BY h.created_at DESC
            """, p);

        List<Map<String, Object>> certificates = jdbc.queryForList("""
            SELECT c.id AS "certificateId", c.submission_id AS "submissionId",
                   s.submitted_at AS "submittedAtUtcNaive", c.disease_name AS "diseaseName",
                   c.diagnosed_date_text AS "diagnosedDateText", c.prognosis AS "prognosis",
                   c.ocr_confidence AS "ocrConfidence", c.is_manually_edited AS "manuallyEdited"
            FROM pet_health_certificates c
            JOIN submissions s ON s.id = c.submission_id
            WHERE s.pet_id = :petId AND s.status = 'COMPLETED'
              AND (s.submitted_at AT TIME ZONE 'UTC') >= CAST(:fromUtc AS TIMESTAMPTZ)
              AND (s.submitted_at AT TIME ZONE 'UTC') < CAST(:toUtc AS TIMESTAMPTZ)
            ORDER BY s.submitted_at DESC
            """, p);

        List<Map<String, Object>> walks = jdbc.queryForList("""
            SELECT w.id AS "walkId", w.start_time AS "startTime", w.end_time AS "endTime",
                   w.distance_km AS "distanceKm", w.active_duration_seconds AS "activeDurationSeconds"
            FROM walks w
            WHERE w.pet_id = :petId AND w.end_time IS NOT NULL
              AND w.start_time >= CAST(:fromUtc AS TIMESTAMPTZ)
              AND w.start_time < CAST(:toUtc AS TIMESTAMPTZ)
            ORDER BY w.start_time DESC
            """, p);

        // diagnoses.condition_id references conditions.id. We intentionally return conditionId
        // until the name/label columns of conditions are verified.
        List<Map<String, Object>> eyes = jdbc.queryForList("""
            SELECT
                s.id AS "submissionId",
                s.submitted_at AS "submittedAtUtcNaive",
                d.condition_id AS "conditionId",
                c.name AS "conditionName",
                c.species AS "species",
                c.description AS "description",
                d.probability AS "probability"
            FROM submissions s
            LEFT JOIN diagnoses d
                ON d.submission_id = s.id
            LEFT JOIN conditions c
                ON c.id = d.condition_id
            WHERE s.pet_id = :petId
                AND s.type = 'EYE'
                AND s.status = 'COMPLETED'
                AND (s.submitted_at AT TIME ZONE 'UTC')
                    >= CAST(:fromUtc AS TIMESTAMPTZ)
                AND (s.submitted_at AT TIME ZONE 'UTC')
                    < CAST(:toUtc AS TIMESTAMPTZ)
            ORDER BY s.submitted_at DESC,
                d.probability DESC NULLS LAST
             """, p);

        List<Map<String, Object>> urines = jdbc.queryForList("""
            SELECT s.id AS "submissionId", s.submitted_at AS "submittedAtUtcNaive",
                   a.name AS "analyteName", m.value AS "value"
            FROM submissions s
            LEFT JOIN urine_measurements m ON m.submission_id = s.id
            LEFT JOIN urine_analytes a ON a.id = m.analyte_id
            WHERE s.pet_id = :petId AND s.type = 'URINE' AND s.status = 'COMPLETED'
              AND (s.submitted_at AT TIME ZONE 'UTC') >= CAST(:fromUtc AS TIMESTAMPTZ)
              AND (s.submitted_at AT TIME ZONE 'UTC') < CAST(:toUtc AS TIMESTAMPTZ)
            ORDER BY s.submitted_at DESC, a.name
            """, p);

        List<Map<String, Object>> respirations = jdbc.queryForList("""
            SELECT r.id AS "videoId", r.status AS "status", r.uploaded_at AS "uploadedAt",
                   r.completed_at AS "completedAt", r.respiratory_rate AS "respiratoryRate",
                   r.duration_sec AS "durationSec", r.failure_reason AS "failureReason"
            FROM respiration_records r
            WHERE r.pet_id = :petId
              AND COALESCE(r.uploaded_at, r.created_at) >= CAST(:fromUtc AS TIMESTAMPTZ)
              AND COALESCE(r.uploaded_at, r.created_at) < CAST(:toUtc AS TIMESTAMPTZ)
            ORDER BY r.created_at DESC
            """, p);

        BigDecimal totalDistance = walks.stream()
                .map(w -> asDecimal(w.get("distanceKm")))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long totalActiveSeconds = walks.stream().mapToLong(w -> {
            Object v = w.get("activeDurationSeconds");
            return v instanceof Number n ? n.longValue() : 0L;
        }).sum();
        long respirationCompleted = respirations.stream()
                .filter(r -> "COMPLETED".equals(String.valueOf(r.get("status")))).count();
        boolean urgentSelfCheck = selfChecks.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.get("urgentHit")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("selfCheckCount", selfChecks.size());
        summary.put("certificateCount", certificates.size());
        summary.put("walkCount", walks.size());
        summary.put("totalWalkDistanceKm", totalDistance);
        summary.put("totalWalkActiveMinutes", BigDecimal.valueOf(totalActiveSeconds)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        summary.put("eyeSubmissionCount", uniqueCount(eyes, "submissionId"));
        summary.put("urineSubmissionCount", uniqueCount(urines, "submissionId"));
        summary.put("respirationCompletedCount", respirationCompleted);
        summary.put("respirationOtherCount", respirations.size() - respirationCompleted);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("level", urgentSelfCheck ? "URGENT_FLAG_RECORDED" : "NOT_ASSESSED");
        assessment.put("consultationSuggested", urgentSelfCheck ? Boolean.TRUE : null);
        assessment.put("reasons", urgentSelfCheck
                ? List.of("해당 기간의 자가진단에 즉시 확인이 필요한 증상이 기록되었습니다.")
                : List.of("자동 진료 필요성 판정 규칙은 아직 수의사 검토 전입니다. 기록만으로 정상 상태를 보장할 수 없습니다."));
        assessment.put("disclaimer", "이 리포트는 기존 기록의 요약이며 수의사의 진단 또는 진료 소견서가 아닙니다.");

        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("selfChecks", selfChecks);
        sections.put("certificates", certificates);
        sections.put("walks", walks);
        sections.put("eyes", eyes);
        sections.put("urines", urines);
        sections.put("respirations", respirations);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("petId", petId);
        report.put("petName", pet.getName());
        report.put("periodType", periodType.name());
        report.put("startDate", fromDate.toString());
        report.put("endDate", toDateExclusive.minusDays(1).toString());
        report.put("timeZone", KST.getId());
        report.put("summary", summary);
        report.put("assessment", assessment);
        report.put("sections", sections);
        report.put("reportType", "AUTOMATED_RECORD_SUMMARY");
        return report;
    }

    private static long uniqueCount(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(row -> row.get(key)).filter(Objects::nonNull).distinct().count();
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(value.toString());
    }
}
