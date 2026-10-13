package com.smarthealthdog.backend.clients.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;

/**
 * OCR 전체 텍스트에서 진단서 12개 항목을 뽑아낸다.
 *
 * 병원마다 서식이 달라 고정 템플릿을 전제할 수 없으므로, 라벨 문자열을 줄 단위로 찾아
 * 그 뒤(또는 다음 줄)의 값을 취한다. 화이트리스트한 항목만 꺼내며 견주 성명·주소는
 * 추출 대상에 포함되지 않는다.
 */
@Component
public class HealthCertificateFieldExtractor {

    /** 진단서 고유 키워드. 하나도 없으면 진단서 양식이 아니라고 판단한다. */
    private static final List<String> CERTIFICATE_KEYWORDS = List.of(
        "진단서",
        "병명",
        "동물의 표시",
        "동물의표시"
    );

    /** 라벨 → 항목 키. 긴 라벨이 먼저 매칭되도록 길이 내림차순으로 정렬해 사용한다. */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("동물명", "animalName");
        LABELS.put("축명", "animalName");
        LABELS.put("종류", "speciesText");
        LABELS.put("축종", "speciesText");
        LABELS.put("품종", "breed");
        LABELS.put("성별", "genderText");
        LABELS.put("모색", "coatColor");
        LABELS.put("털색", "coatColor");
        LABELS.put("연령", "ageText");
        LABELS.put("나이", "ageText");
        LABELS.put("특징", "features");
        LABELS.put("병명", "diseaseName");
        LABELS.put("진단명", "diseaseName");
        LABELS.put("발병 연월일", "onsetDateText");
        LABELS.put("발병연월일", "onsetDateText");
        LABELS.put("발병 연원일", "onsetDateText");
        LABELS.put("발병연원일", "onsetDateText");
        LABELS.put("발병일", "onsetDateText");
        LABELS.put("진단 연월일", "diagnosedDateText");
        LABELS.put("진단연월일", "diagnosedDateText");
        LABELS.put("진단 연원일", "diagnosedDateText");
        LABELS.put("진단연원일", "diagnosedDateText");
        LABELS.put("진단일", "diagnosedDateText");
        LABELS.put("예후 소견", "prognosis");
        LABELS.put("예후소견", "prognosis");
        LABELS.put("예후", "prognosis");
        LABELS.put("그외의 사항", "remarks");
        LABELS.put("그 외의 사항", "remarks");
        LABELS.put("비고", "remarks");
    }

    /** 긴 라벨 우선으로 매칭하기 위한 정렬된 라벨 목록. */
    private static final List<String> LABELS_BY_LENGTH_DESC = LABELS.keySet().stream()
        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
        .toList();

    /** 라벨과 값을 가르는 구분자. */
    private static final Pattern SEPARATOR = Pattern.compile("^[\\s:：\\-–—.·]+");

    /** yyyy(.|-|/|년) mm(.|-|/|월) dd(일)? 형태의 날짜. */
    private static final Pattern DATE = Pattern.compile(
        "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?"
    );

    /**
     * 진단서 고유 키워드가 하나라도 있는지 확인한다.
     * @param fullText OCR 전체 텍스트
     * @return 진단서 양식으로 보이면 true
     */
    public boolean looksLikeHealthCertificate(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return false;
        }

        String normalized = fullText.replace(" ", "");

        return CERTIFICATE_KEYWORDS.stream()
            .anyMatch(keyword -> normalized.contains(keyword.replace(" ", "")));
    }

    /**
     * OCR 결과에서 12개 항목을 뽑아 진단서 엔티티를 만든다.
     *
     * 반환되는 엔티티에는 반려동물·제출 연관이 설정되어 있지 않다. 저장 직전에 호출자가 채운다.
     *
     * @param ocrResult OCR 결과
     * @return 12개 항목과 신뢰도가 채워진 진단서 엔티티 (미저장)
     */
    public PetHealthCertificate extract(HealthCertificateOcrResult ocrResult) {
        if (ocrResult == null) {
            throw new IllegalArgumentException("OCR 결과가 null일 수 없습니다.");
        }

        Map<String, String> values = parseLabeledValues(ocrResult.fullText());

        String onsetDateText = values.get("onsetDateText");
        String diagnosedDateText = values.get("diagnosedDateText");

        return PetHealthCertificate.builder()
            .animalName(values.get("animalName"))
            .speciesText(values.get("speciesText"))
            .breed(values.get("breed"))
            .genderText(values.get("genderText"))
            .coatColor(values.get("coatColor"))
            .ageText(values.get("ageText"))
            .features(values.get("features"))
            .diseaseName(values.get("diseaseName"))
            .onsetDate(parseDate(onsetDateText))
            .onsetDateText(onsetDateText)
            .diagnosedDate(parseDate(diagnosedDateText))
            .diagnosedDateText(diagnosedDateText)
            .prognosis(values.get("prognosis"))
            .remarks(values.get("remarks"))
            .ocrConfidence(toConfidence(ocrResult.confidence()))
            .build();
    }

    /**
     * 날짜 원문을 LocalDate 로 변환한다.
     *
     * 한국 진단서는 2020.10.28 표기가 흔하고 OCR 이 구분자나 숫자를 틀릴 수 있다.
     * 파싱에 실패해도 등록을 막지 않는다 — 원문은 보존하고 DATE 컬럼만 null 로 둔다.
     *
     * @param text 날짜 원문 (null 가능)
     * @return 파싱된 날짜. 실패하면 null
     */
    public LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    /**
     * 줄 단위로 "라벨 + 값"을 찾는다. 같은 줄에 값이 없으면 다음 줄을 값으로 본다.
     */
    private Map<String, String> parseLabeledValues(String fullText) {
        Map<String, String> values = new LinkedHashMap<>();

        if (fullText == null || fullText.isBlank()) {
            return values;
        }

        String pendingKey = null;

        for (String rawLine : fullText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String normalized = stripSeparator(line);
            String matchedLabel = findLabel(normalized);

            if (matchedLabel == null) {
                // 직전 줄이 값 없는 라벨이었다면 이 줄을 그 값으로 본다.
                if (pendingKey != null) {
                    putIfAbsent(values, pendingKey, normalized);
                    pendingKey = null;
                }
                continue;
            }

            String key = LABELS.get(matchedLabel);
            String value = stripSeparator(normalized.substring(matchedLabel.length()));

            if (value.isEmpty()) {
                pendingKey = values.containsKey(key) ? null : key;
                continue;
            }

            pendingKey = null;
            putIfAbsent(values, key, value);
        }

        return values;
    }

    /**
     * 줄이 어떤 라벨로 시작하는지 찾는다. 긴 라벨을 우선한다.
     */
    private String findLabel(String normalizedLine) {
        for (String label : LABELS_BY_LENGTH_DESC) {
            if (normalizedLine.startsWith(label)) {
                return label;
            }
        }

        return null;
    }

    private String stripSeparator(String text) {
        return SEPARATOR.matcher(text).replaceFirst("").trim();
    }

    private void putIfAbsent(Map<String, String> values, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        values.putIfAbsent(key, value);
    }

    /**
     * 신뢰도를 컬럼 정밀도(NUMERIC(5,4))에 맞춰 0.0 ~ 1.0 범위로 정규화한다.
     */
    private BigDecimal toConfidence(double confidence) {
        double bounded = Math.max(0.0, Math.min(1.0, confidence));

        return BigDecimal.valueOf(bounded).setScale(4, RoundingMode.HALF_UP);
    }
}
