package com.smarthealthdog.backend.clients.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;

public class HealthCertificateFieldExtractorUT {

    private final HealthCertificateFieldExtractor extractor = new HealthCertificateFieldExtractor();

    private static final String SAMPLE = String.join("\n",
        "진 단 서",
        "동물의 표시",
        "동물명: 초코",
        "종류: Canine",
        "품종: Pug",
        "성별: Castrated Male",
        "모색: 흰색",
        "연령: 16년 3개월",
        "병명: 심장비대, 폐침윤",
        "발병 연월일: 2020.10.28",
        "진단 연월일: 2020.10.28",
        "예후 소견: 엑스레이 검사상 폐침윤이 확인됩니다.",
        "그외의 사항: 10.28일 진료에 대한 진단서 발급 원합니다."
    );

    @Test
    void looksLikeHealthCertificate_ShouldAcceptSample() {
        assertTrue(extractor.looksLikeHealthCertificate(SAMPLE));
    }

    @Test
    void looksLikeHealthCertificate_ShouldRejectUnrelatedText() {
        assertFalse(extractor.looksLikeHealthCertificate("오늘 산책 기록\n거리 3.2km"));
        assertFalse(extractor.looksLikeHealthCertificate(""));
        assertFalse(extractor.looksLikeHealthCertificate(null));
    }

    @Test
    void extract_ShouldFillAllLabeledFields() {
        PetHealthCertificate certificate = extractor.extract(new HealthCertificateOcrResult(SAMPLE, 0.9124));

        assertEquals("초코", certificate.getAnimalName());
        assertEquals("Canine", certificate.getSpeciesText());
        assertEquals("Pug", certificate.getBreed());
        assertEquals("Castrated Male", certificate.getGenderText());
        assertEquals("흰색", certificate.getCoatColor());
        assertEquals("16년 3개월", certificate.getAgeText());
        assertEquals("심장비대, 폐침윤", certificate.getDiseaseName());
        assertEquals("2020.10.28", certificate.getOnsetDateText());
        assertEquals(LocalDate.of(2020, 10, 28), certificate.getOnsetDate());
        assertEquals("2020.10.28", certificate.getDiagnosedDateText());
        assertEquals(LocalDate.of(2020, 10, 28), certificate.getDiagnosedDate());
        assertEquals("엑스레이 검사상 폐침윤이 확인됩니다.", certificate.getPrognosis());
        assertEquals("10.28일 진료에 대한 진단서 발급 원합니다.", certificate.getRemarks());
    }

    @Test
    void extract_ShouldNormalizeConfidenceToColumnPrecision() {
        PetHealthCertificate certificate = extractor.extract(new HealthCertificateOcrResult(SAMPLE, 0.91239));

        // NUMERIC(5,4) 범위를 벗어나면 저장 시 터진다.
        assertEquals(4, certificate.getOcrConfidence().scale());
        assertTrue(certificate.getOcrConfidence().doubleValue() >= 0.0);
        assertTrue(certificate.getOcrConfidence().doubleValue() <= 1.0);
    }

    @Test
    void extract_ShouldTakeNextLine_WhenLabelHasNoValueOnTheSameLine() {
        String text = String.join("\n", "진단서", "병명", "슬개골 탈구", "진단 연월일", "2024-03-15");

        PetHealthCertificate certificate = extractor.extract(new HealthCertificateOcrResult(text, 0.9));

        assertEquals("슬개골 탈구", certificate.getDiseaseName());
        assertEquals("2024-03-15", certificate.getDiagnosedDateText());
        assertEquals(LocalDate.of(2024, 3, 15), certificate.getDiagnosedDate());
    }

    @Test
    void extract_ShouldKeepRawText_WhenDateCannotBeParsed() {
        // OCR 이 0 을 알파벳 O 로 잘못 읽은 경우. 등록은 막지 않고 DATE 컬럼만 null 로 둔다.
        String text = String.join("\n", "진단서", "병명: 심장비대", "진단 연월일: 202O.1O.28");

        PetHealthCertificate certificate = extractor.extract(new HealthCertificateOcrResult(text, 0.9));

        assertEquals("202O.1O.28", certificate.getDiagnosedDateText());
        assertNull(certificate.getDiagnosedDate());
    }

    @Test
    void parseDate_ShouldAcceptCommonKoreanFormats() {
        assertEquals(LocalDate.of(2020, 10, 28), extractor.parseDate("2020.10.28"));
        assertEquals(LocalDate.of(2020, 10, 28), extractor.parseDate("2020-10-28"));
        assertEquals(LocalDate.of(2020, 10, 28), extractor.parseDate("2020/10/28"));
        assertEquals(LocalDate.of(2020, 10, 28), extractor.parseDate("2020년 10월 28일"));
        assertEquals(LocalDate.of(2020, 1, 5), extractor.parseDate("2020.1.5"));
    }

    @Test
    void parseDate_ShouldReturnNull_WhenInputIsUnusable() {
        assertNull(extractor.parseDate(null));
        assertNull(extractor.parseDate(""));
        assertNull(extractor.parseDate("알 수 없음"));
        assertNull(extractor.parseDate("2020.13.45"));
    }

    @Test
    void extract_ShouldLeaveOptionalFieldsNull_WhenTheyAreAbsent() {
        // 나머지 10개 항목은 비어도 등록을 허용한다.
        String text = String.join("\n", "진단서", "병명: 심장비대", "진단 연월일: 2020.10.28");

        PetHealthCertificate certificate = extractor.extract(new HealthCertificateOcrResult(text, 0.9));

        assertNull(certificate.getAnimalName());
        assertNull(certificate.getBreed());
        assertNull(certificate.getFeatures());
        assertNull(certificate.getOnsetDateText());
        assertEquals("심장비대", certificate.getDiseaseName());
    }
}
