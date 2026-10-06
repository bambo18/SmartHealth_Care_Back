package com.smarthealthdog.backend.clients.ocr;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;
import com.smarthealthdog.backend.utils.DateTextParser;

/**
 * OCR 텍스트 블록에서 진단서 12개 항목을 뽑는다.
 *
 * 순수 함수다 — 외부 호출이 없어 전부 단위 테스트로 고정된다.
 *
 * 화이트리스트 방식이다: 아래 라벨에 해당하는 값만 꺼내고 나머지는 버린다.
 * 견주 성명 주소가 결과에 섞여 들어갈 경로를 원천 차단한다.
 */
@Component
public class HealthCertificateFieldExtractor {

    /** 이 문서가 진단서인지 판단하는 고유 키워드. 하나라도 있으면 진단서로 본다. */
    private static final List<String> DOCUMENT_KEYWORDS = List.of(
        "진단서", "병명", "동물의 표시", "진단 연원일", "예후 소견"
    );

    /**
     * 라벨 → 결과 키. 매칭은 가장 긴 라벨 우선이므로
     * '발병 연원일' 이 '연령' 등과 충돌하지 않는다.
     */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("동물명", "animalName");
        LABELS.put("종류", "speciesText");
        LABELS.put("품종", "breed");
        LABELS.put("성별", "genderText");
        LABELS.put("모색", "coatColor");
        LABELS.put("연령", "ageText");
        LABELS.put("특징", "features");
        LABELS.put("병명", "diseaseName");
        LABELS.put("발병 연원일", "onsetDateText");
        LABELS.put("발병연원일", "onsetDateText");
        LABELS.put("진단 연원일", "diagnosedDateText");
        LABELS.put("진단연원일", "diagnosedDateText");
        LABELS.put("예후 소견", "prognosis");
        LABELS.put("예후소견", "prognosis");
        LABELS.put("그외의 사항", "remarks");
        LABELS.put("비고", "remarks");
    }

    /**
     * 텍스트 블록에서 12개 항목을 추출한다.
     *
     * @param blocks OCR 이 읽은 텍스트 조각 목록 (null 이면 빈 목록으로 취급)
     * @return 추출 결과. 판별(신뢰도 필수 항목 문서 종류)은 호출자가 한다.
     */
    public HealthCertificateOcrResult extract(List<OcrTextBlock> blocks) {
        List<OcrTextBlock> safeBlocks = blocks == null ? List.of() : blocks;

        Map<String, String> values = new LinkedHashMap<>();
        boolean keywordFound = false;

        for (int i = 0; i < safeBlocks.size(); i++) {
            String text = normalize(safeBlocks.get(i).text());
            if (text.isEmpty()) {
                continue;
            }

            if (!keywordFound && containsDocumentKeyword(text)) {
                keywordFound = true;
            }

            String matchedLabel = findLabel(text);
            if (matchedLabel == null) {
                continue;
            }

            String key = LABELS.get(matchedLabel);
            if (values.containsKey(key)) {
                // 같은 라벨이 두 번 나오면 첫 번째를 신뢰한다 (표 머리글 반복 대비).
                continue;
            }

            String value = valueAfterLabel(text, matchedLabel);

            // 라벨만 있고 값이 다음 블록에 있는 레이아웃을 처리한다.
            if (value.isEmpty() && i + 1 < safeBlocks.size()) {
                String next = normalize(safeBlocks.get(i + 1).text());
                if (findLabel(next) == null) {
                    value = next;
                }
            }

            if (!value.isEmpty()) {
                values.put(key, value);
            }
        }

        String onsetDateText = values.get("onsetDateText");
        String diagnosedDateText = values.get("diagnosedDateText");

        return new HealthCertificateOcrResult(
            values.get("animalName"),
            values.get("speciesText"),
            values.get("breed"),
            values.get("genderText"),
            values.get("coatColor"),
            values.get("ageText"),
            values.get("features"),
            values.get("diseaseName"),
            parseDateOrNull(onsetDateText),
            onsetDateText,
            parseDateOrNull(diagnosedDateText),
            diagnosedDateText,
            values.get("prognosis"),
            values.get("remarks"),
            averageConfidence(safeBlocks),
            keywordFound
        );
    }

    private LocalDate parseDateOrNull(String text) {
        return DateTextParser.parse(text).orElse(null);
    }

    private double averageConfidence(List<OcrTextBlock> blocks) {
        if (blocks.isEmpty()) {
            return 0.0;
        }

        return blocks.stream()
            .mapToDouble(OcrTextBlock::confidence)
            .average()
            .orElse(0.0);
    }

    private boolean containsDocumentKeyword(String text) {
        return DOCUMENT_KEYWORDS.stream().anyMatch(text::contains);
    }

    /**
     * 텍스트가 어떤 라벨로 시작하는지 찾는다.
     * 긴 라벨을 먼저 시도해 '발병 연원일' 이 '연령' 등과 충돌하지 않게 한다.
     */
    private String findLabel(String text) {
        return LABELS.keySet().stream()
            .filter(text::startsWith)
            .max(java.util.Comparator.comparingInt(String::length))
            .orElse(null);
    }

    /** 라벨 뒤의 콜론 공백을 떼고 값만 남긴다. */
    private String valueAfterLabel(String text, String label) {
        String rest = text.substring(label.length());
        return rest.replaceFirst("^[\\s:\uFF1A]+", "").trim();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        // OCR 이 내는 전각 공백과 연속 공백을 하나로 모은다.
        return text.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }
}
