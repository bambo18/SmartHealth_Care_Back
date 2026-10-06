package com.smarthealthdog.backend.clients.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;

public class HealthCertificateFieldExtractorUT {

    private final HealthCertificateFieldExtractor extractor = new HealthCertificateFieldExtractor();

    private static OcrTextBlock block(String text) {
        return new OcrTextBlock(text, 0.95);
    }

    private List<OcrTextBlock> 정상_진단서() {
        return List.of(
            block("진단서"),
            block("동물의 표시"),
            block("동물명: 초코"),
            block("종류: Canine"),
            block("품종: Pug"),
            block("성별: Castrated Male"),
            block("모색: 흰색"),
            block("연령: 16년 3개월"),
            block("특징: 없음"),
            block("병명: 심장비대, 폐침윤"),
            block("발병 연원일: 2020.10.28"),
            block("진단 연원일: 2020.10.28"),
            block("예후 소견: 엑스레이 검사상 폐침윤이 확인됩니다."),
            block("그외의 사항: 10.28일 진료에 대한 진단서 발급 원합니다.")
        );
    }

    @Test
    void 라벨_뒤의_값을_12개_항목으로_추출한다() {
        HealthCertificateOcrResult result = extractor.extract(정상_진단서());

        assertEquals("초코", result.animalName());
        assertEquals("Canine", result.speciesText());
        assertEquals("Pug", result.breed());
        assertEquals("Castrated Male", result.genderText());
        assertEquals("흰색", result.coatColor());
        assertEquals("16년 3개월", result.ageText());
        assertEquals("없음", result.features());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals("2020.10.28", result.onsetDateText());
        assertEquals("2020.10.28", result.diagnosedDateText());
        assertEquals("엑스레이 검사상 폐침윤이 확인됩니다.", result.prognosis());
        assertEquals("10.28일 진료에 대한 진단서 발급 원합니다.", result.remarks());
    }

    @Test
    void 날짜는_원문과_파싱값을_함께_담는다() {
        HealthCertificateOcrResult result = extractor.extract(정상_진단서());

        assertEquals(LocalDate.of(2020, 10, 28), result.onsetDate());
        assertEquals(LocalDate.of(2020, 10, 28), result.diagnosedDate());
    }

    @Test
    void 날짜_파싱에_실패해도_원문은_보존하고_파싱값만_null로_둔다() {
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("병명: 심장비대"),
            block("진단 연원일: 20Z0.1O.28")   // OCR 이 0 을 O 로 틀린 경우
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("20Z0.1O.28", result.diagnosedDateText());
        assertNull(result.diagnosedDate());
    }

    @Test
    void 콜론_없이_라벨과_값이_떨어져_있어도_추출한다() {
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("병명"),
            block("심장비대"),
            block("진단 연원일"),
            block("2020.10.28")
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("심장비대", result.diseaseName());
        assertEquals("2020.10.28", result.diagnosedDateText());
    }

    @Test
    void 문서_고유_키워드가_하나라도_있으면_documentKeywordFound가_true다() {
        assertTrue(extractor.extract(정상_진단서()).documentKeywordFound());

        assertTrue(extractor.extract(List.of(block("병명: 심장비대"))).documentKeywordFound());
    }

    @Test
    void 진단서와_무관한_텍스트는_documentKeywordFound가_false다() {
        List<OcrTextBlock> blocks = List.of(
            block("영수증"),
            block("합계 12,000원"),
            block("카드 승인")
        );

        assertFalse(extractor.extract(blocks).documentKeywordFound());
    }

    @Test
    void averageConfidence는_블록_신뢰도의_평균이다() {
        List<OcrTextBlock> blocks = List.of(
            new OcrTextBlock("진단서", 1.0),
            new OcrTextBlock("병명: 심장비대", 0.8),
            new OcrTextBlock("진단 연원일: 2020.10.28", 0.6)
        );

        assertEquals(0.8, extractor.extract(blocks).averageConfidence(), 0.0001);
    }

    @Test
    void 블록이_비면_신뢰도는_0이고_모든_항목이_null이다() {
        HealthCertificateOcrResult result = extractor.extract(List.of());

        assertEquals(0.0, result.averageConfidence(), 0.0001);
        assertNull(result.diseaseName());
        assertNull(result.diagnosedDateText());
        assertFalse(result.documentKeywordFound());
    }

    @Test
    void 견주_성명과_주소는_추출하지_않는다() {
        // SPEC 1.5 범위 밖: 견주 성명 주소는 추출하지도 저장하지도 않는다.
        // 결과 레코드에 해당 필드가 없어야 하며, 소유자 라벨이 다른 항목으로
        // 잘못 흘러들어가지도 않아야 한다.
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("소유자 성명: 홍길동"),
            block("주소: 서울시 강남구"),
            block("병명: 심장비대"),
            block("진단 연원일: 2020.10.28")
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("심장비대", result.diseaseName());
        assertNull(result.animalName());
        assertNull(result.features());
        assertNull(result.remarks());
    }
}
