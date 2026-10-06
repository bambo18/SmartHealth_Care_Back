package com.smarthealthdog.backend.clients.ocr;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google Cloud Vision DOCUMENT_TEXT_DETECTION 구현.
 *
 * 타임아웃 에러 매핑 비밀값 로깅 방침은 utils/OAuthClient 를 따른다 —
 * 저장소에서 유일하게 타임아웃과 상태 코드 검사를 제대로 하는 외부 클라이언트다.
 *
 * 재시도하지 않는다. 동기 요청이라 재시도가 사용자 대기 시간을 배로 늘린다.
 *
 * 주의: Vision 은 이미지 단위 실패를 HTTP 200 + responses[0].error 로 돌려준다.
 * 상태 코드만 검사하면 빈 결과가 추출기까지 흘러간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleVisionOcrClient implements HealthCertificateOcrClient {

    /** confidence 가 아예 없을 때의 폴백. 0.0 으로 두면 min-confidence 게이트가 전부 거부한다. */
    private static final double CONFIDENCE_FALLBACK = 1.0;

    private final ObjectMapper objectMapper;
    private final HealthCertificateFieldExtractor fieldExtractor;

    /**
     * OAuthClient 와 동일하게 연결 타임아웃을 건 전용 클라이언트를 쓴다.
     *
     * final 이 아닌 이유: 단위 테스트가 ReflectionTestUtils 로 목을 주입한다.
     */
    private HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Value("${ocr.google.endpoint}")
    private String endpoint;

    @Value("${ocr.google.api-key}")
    private String apiKey;

    @Value("${ocr.google.language-hint}")
    private String languageHint;

    @Value("${ocr.google.read-timeout-seconds}")
    private int readTimeoutSeconds;

    /**
     * 진단서 이미지에서 12개 항목을 추출한다.
     *
     * @param imageBytes 이미지 바이트
     * @param contentType Tika 판정 MIME 타입. Vision 은 바이트에서 형식을 스스로 판정하므로
     *                    요청에 쓰이지 않는다 — 인터페이스 계약 유지를 위해 받는다.
     * @return 추출 결과 (판별은 호출자가 한다)
     * @throws InvalidRequestDataException 바이트가 비었거나({@code INVALID_IMAGE}),
     *         응답을 해석할 수 없는 경우({@code OCR_RECOGNITION_FAILED})
     * @throws ServiceUnavailableException 연결 실패 타임아웃 5xx ({@code OCR_SERVICE_UNAVAILABLE})
     * @throws InternalServerErrorException 4xx — 잘못된 API 키 요청 형식 할당량 초과
     */
    @Override
    public HealthCertificateOcrResult extract(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String requestBody = buildRequestBody(imageBytes);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(readTimeoutSeconds))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        // API 키가 URI 쿼리에 있으므로 URI 를 로깅하면 키가 로그에 남는다.
        // 키 없는 엔드포인트와 키 길이만 남긴다. 이미지 바이트와 OCR 원응답도 로깅하지 않는다 —
        // 견주 성명 주소가 포함된다.
        log.info("Google Vision OCR 요청: endpoint={}, imageBytes={}, apiKeyLength={}",
                 endpoint, imageBytes.length, apiKey == null ? 0 : apiKey.length());

        HttpResponse<String> response = send(request);

        int statusCode = response.statusCode();

        if (statusCode >= 500) {
            log.error("Google Vision OCR 서버 오류: status={}", statusCode);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }

        if (statusCode >= 400) {
            // 잘못된 키 요청 형식 할당량 초과 — 사용자 잘못이 아니므로 500 으로 올린다.
            // 응답 본문은 로깅하지 않는다. 벤더가 요청 일부를 되돌려줄 수 있다.
            log.error("Google Vision OCR 요청 거부: status={}", statusCode);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return parse(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException e) {
            // 인터럽트 플래그를 복원한다 (OAuthClient 와 동일한 방침).
            Thread.currentThread().interrupt();
            log.error("Google Vision OCR 호출이 인터럽트되었습니다.", e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            log.error("Google Vision OCR 연결 실패 또는 타임아웃", e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * images:annotate 요청 본문을 만든다.
     *
     * DOCUMENT_TEXT_DETECTION 은 표 라벨이 빽빽한 인쇄 문서에 맞는 모드다.
     * languageHints 로 한국어를 명시하면 한글 인식률이 올라간다.
     */
    private String buildRequestBody(byte[] imageBytes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "requests", List.of(Map.of(
                    "image", Map.of(
                        "content", Base64.getEncoder().encodeToString(imageBytes)
                    ),
                    "features", List.of(Map.of("type", "DOCUMENT_TEXT_DETECTION")),
                    "imageContext", Map.of("languageHints", List.of(languageHint))
                ))
            ));
        } catch (Exception e) {
            log.error("Google Vision OCR 요청 본문 생성 실패", e);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Vision 응답을 OcrTextBlock 목록으로 바꿔 추출기에 넘긴다.
     *
     * fullTextAnnotation.text 를 줄바꿈으로 쪼개 한 줄을 한 블록으로 쓴다 —
     * 진단서는 "라벨: 값" 이 한 줄에 오는 서식이라 추출기의 기대와 맞는다.
     * 줄 단위 신뢰도는 응답에 없으므로 블록 신뢰도 평균을 모든 줄에 같게 부여한다.
     */
    private HealthCertificateOcrResult parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            // 200 인데 본문을 읽을 수 없다 — 사용자에게는 인식 실패로 알린다.
            log.error("Google Vision OCR 응답 파싱 실패", e);
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode responses = root.path("responses");
        if (!responses.isArray() || responses.isEmpty()) {
            log.error("Google Vision OCR 응답에 responses 가 없습니다.");
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode first = responses.get(0);

        // Vision 은 이미지 단위 실패를 200 + error 로 알린다. 상태 코드만 보면 놓친다.
        JsonNode error = first.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            log.error("Google Vision OCR 이미지 처리 실패: code={}, message={}",
                      error.path("code").asInt(), error.path("message").asText(""));
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode annotation = first.path("fullTextAnnotation");
        if (annotation.isMissingNode() || annotation.isNull()) {
            // 글자가 전혀 없는 사진이다.
            log.info("Google Vision OCR: 텍스트가 검출되지 않았습니다.");
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        String fullText = annotation.path("text").asText("");
        double confidence = averageBlockConfidence(annotation);

        List<OcrTextBlock> blocks = new ArrayList<>();
        for (String line : fullText.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(new OcrTextBlock(trimmed, confidence));
            }
        }

        return fieldExtractor.extract(blocks);
    }

    /**
     * pages[].blocks[].confidence 의 평균. 값이 하나도 없으면 CONFIDENCE_FALLBACK 을 쓴다.
     *
     * 0.0 으로 폴백하면 ocr.certificate.min-confidence 게이트가 모든 요청을 거부해
     * 기능이 전면 중단된다. 품질 게이트가 느슨해지는 대가를 치르더라도 전면 거부를 피한다.
     * 이 경로로 들어오면 log.warn 이 유일한 신호이므로 운영에서 모니터링한다.
     */
    private double averageBlockConfidence(JsonNode annotation) {
        double sum = 0.0;
        int count = 0;

        for (JsonNode page : annotation.path("pages")) {
            for (JsonNode block : page.path("blocks")) {
                JsonNode confidence = block.path("confidence");
                if (confidence.isNumber()) {
                    sum += confidence.asDouble();
                    count++;
                }
            }
        }

        if (count == 0) {
            log.warn("Google Vision OCR 응답에 block confidence 가 없습니다. {} 로 폴백합니다 — "
                     + "품질 게이트가 동작하지 않습니다.", CONFIDENCE_FALLBACK);
            return CONFIDENCE_FALLBACK;
        }

        return sum / count;
    }
}
