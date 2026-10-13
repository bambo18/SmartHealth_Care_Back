package com.smarthealthdog.backend.clients.ocr;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google Cloud Vision 의 DOCUMENT_TEXT_DETECTION 을 사용하는 구현체.
 *
 * 진단서는 병원마다 서식이 달라 고정 템플릿을 전제할 수 없으므로, 전체 텍스트를 받아
 * {@link HealthCertificateFieldExtractor} 가 항목을 뽑는다.
 *
 * 재시도는 하지 않는다 — 동기 요청이라 재시도가 사용자 대기 시간을 배로 늘린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleVisionOcrClient implements HealthCertificateOcrClient {

    private final ObjectMapper objectMapper;

    @Value("${ocr.google.api-key}")
    private String apiKey;

    @Value("${ocr.google.endpoint}")
    private String endpoint;

    @Value("${ocr.google.language-hint}")
    private String languageHint;

    @Value("${ocr.google.connect-timeout-seconds}")
    private int connectTimeoutSeconds;

    @Value("${ocr.google.read-timeout-seconds}")
    private int readTimeoutSeconds;

    private HttpClient httpClient;

    @PostConstruct
    void initHttpClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public HealthCertificateOcrResult extract(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "?key=" + apiKey))
            .timeout(Duration.ofSeconds(readTimeoutSeconds))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=utf-8")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(imageBytes), StandardCharsets.UTF_8))
            .build();

        // API 키는 로그에 남기지 않는다. 이미지 바이트도 개인정보이므로 길이만 기록한다.
        log.info("진단서 OCR 요청: endpoint={}, imageBytes={}, contentType={}",
            endpoint, imageBytes.length, contentType);

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("진단서 OCR 요청이 중단되었습니다.", e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            log.error("진단서 OCR 요청에 실패했습니다: {}", e.getMessage(), e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }

        int statusCode = response.statusCode();

        if (statusCode >= 500) {
            log.error("진단서 OCR 서비스 오류: status={}", statusCode);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }

        if (statusCode < 200 || statusCode >= 300) {
            // 잘못된 요청이나 잘못된 키는 사용자 잘못이 아니다.
            log.error("진단서 OCR 요청이 거부되었습니다: status={}", statusCode);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return parseResponse(response.body());
    }

    /**
     * Vision images:annotate 요청 본문을 만든다.
     *
     * 이미지는 base64 로 JSON 본문에 담기므로 크기가 약 1.33배로 불어난다.
     * 업로드 상한(7MB)은 이 팽창을 감안한 값이다.
     */
    private String buildRequestBody(byte[] imageBytes) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode requests = root.putArray("requests");

        ObjectNode requestNode = requests.addObject();
        requestNode.putObject("image")
            .put("content", Base64.getEncoder().encodeToString(imageBytes));
        requestNode.putArray("features")
            .addObject()
            .put("type", "DOCUMENT_TEXT_DETECTION");
        requestNode.putObject("imageContext")
            .putArray("languageHints")
            .add(languageHint);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * fullTextAnnotation 의 전체 텍스트와 블록 신뢰도의 평균을 꺼낸다.
     *
     * 원응답에는 견주 성명·주소가 포함되므로 본문 전체를 로그에 남기지 않는다.
     */
    private HealthCertificateOcrResult parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("진단서 OCR 응답을 해석하지 못했습니다: {}", e.getMessage());
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode firstResponse = root.path("responses").path(0);
        if (firstResponse.isMissingNode() || firstResponse.has("error")) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode annotation = firstResponse.path("fullTextAnnotation");
        String fullText = annotation.path("text").asText("");
        if (fullText.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        return new HealthCertificateOcrResult(fullText, averageBlockConfidence(annotation));
    }

    /**
     * 페이지의 모든 블록 신뢰도를 평균낸다. 블록 신뢰도가 하나도 없으면 페이지 신뢰도를 쓴다.
     */
    private double averageBlockConfidence(JsonNode annotation) {
        double sum = 0.0;
        int count = 0;

        for (JsonNode page : annotation.path("pages")) {
            for (JsonNode block : page.path("blocks")) {
                if (block.has("confidence")) {
                    sum += block.path("confidence").asDouble();
                    count++;
                }
            }

            if (count == 0 && page.has("confidence")) {
                sum += page.path("confidence").asDouble();
                count++;
            }
        }

        if (count == 0) {
            return 0.0;
        }

        return sum / count;
    }
}
