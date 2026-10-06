package com.smarthealthdog.backend.dto.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;

public class HealthCertificateMapperUT {

    private final HealthCertificateMapper mapper = new HealthCertificateMapper();

    private static final UUID SUBMISSION_ID =
        UUID.fromString("018f3c2a-7b1e-7000-9c3d-1a2b3c4d5e6f");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-03T12:00:00Z");

    private PetHealthCertificate certificate(LocalDate diagnosedDate, String diagnosedDateText) {
        Pet pet = mock(Pet.class);
        when(pet.getId()).thenReturn(3L);

        Submission submission = Submission.builder()
            .id(SUBMISSION_ID)
            .submittedAt(SUBMITTED_AT)
            .build();

        return PetHealthCertificate.builder()
            .pet(pet)
            .submission(submission)
            .animalName("초코")
            .speciesText("Canine")
            .breed("Pug")
            .genderText("Castrated Male")
            .coatColor("흰색")
            .ageText("16년 3개월")
            .features(null)
            .diseaseName("심장비대, 폐침윤")
            .onsetDate(LocalDate.of(2020, 10, 28))
            .onsetDateText("2020.10.28")
            .diagnosedDate(diagnosedDate)
            .diagnosedDateText(diagnosedDateText)
            .prognosis("폐수종 의심")
            .remarks("비고")
            .ocrConfidence(new BigDecimal("0.9124"))
            .manuallyEdited(false)
            .build();
    }

    @Test
    void toResult는_12개_항목과_메타를_그대로_옮긴다() {
        HealthCertificateResult result =
            mapper.toResult(certificate(LocalDate.of(2020, 10, 28), "2020.10.28"));

        assertEquals("초코", result.animalName());
        assertEquals("Canine", result.speciesText());
        assertEquals("Pug", result.breed());
        assertEquals("Castrated Male", result.genderText());
        assertEquals("흰색", result.coatColor());
        assertEquals("16년 3개월", result.ageText());
        assertNull(result.features());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals(LocalDate.of(2020, 10, 28), result.onsetDate());
        assertEquals("2020.10.28", result.onsetDateText());
        assertEquals("폐수종 의심", result.prognosis());
        assertEquals("비고", result.remarks());
        assertEquals(new BigDecimal("0.9124"), result.ocrConfidence());
        assertFalse(result.manuallyEdited());
    }

    @Test
    void 날짜_파싱_실패는_DATE가_null이고_원문은_보존된다() {
        HealthCertificateResult result = mapper.toResult(certificate(null, "20Z0.1O.28"));

        assertNull(result.diagnosedDate());
        assertEquals("20Z0.1O.28", result.diagnosedDateText());
    }

    @Test
    void toCreatedResponse는_submission_id와_pet_id와_submitted_at을_담는다() {
        HealthCertificateCreatedResponse response =
            mapper.toCreatedResponse(certificate(LocalDate.of(2020, 10, 28), "2020.10.28"));

        assertEquals(SUBMISSION_ID, response.submissionId());
        assertEquals(3L, response.petId());
        assertEquals(SUBMITTED_AT, response.submittedAt());
        assertEquals("심장비대, 폐침윤", response.result().diseaseName());
    }

    @Test
    void null이_들어오면_IllegalArgumentException이다() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toResult(null));
        assertThrows(IllegalArgumentException.class, () -> mapper.toCreatedResponse(null));
    }

    @Test
    void 직렬화_결과는_snake_case_평면_JSON이다() throws Exception {
        // @JsonUnwrapped 가 record 에서 실제로 평탄화되는지 확인한다.
        // 동작하지 않으면 명세 4.2 의 응답 모양이 깨진다.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        JsonNode json = objectMapper.valueToTree(
            mapper.toCreatedResponse(certificate(LocalDate.of(2020, 10, 28), "2020.10.28"))
        );

        assertEquals(SUBMISSION_ID.toString(), json.path("submission_id").asText());
        assertEquals(3, json.path("pet_id").asInt());
        assertTrue(json.has("submitted_at"));

        // 평탄화 확인 — result 중첩 객체가 아니라 같은 레벨에 있어야 한다.
        assertFalse(json.has("result"), "result 가 중첩되면 평탄화가 동작하지 않은 것이다");
        assertEquals("심장비대, 폐침윤", json.path("disease_name").asText());
        assertEquals("2020.10.28", json.path("diagnosed_date_text").asText());
        assertEquals("Castrated Male", json.path("gender_text").asText());
        assertFalse(json.path("is_manually_edited").asBoolean());

        // 견주 성명 주소는 응답에 없어야 한다 (SPEC 1.5 범위 밖).
        assertFalse(json.has("owner_name"));
        assertFalse(json.has("address"));
    }
}
