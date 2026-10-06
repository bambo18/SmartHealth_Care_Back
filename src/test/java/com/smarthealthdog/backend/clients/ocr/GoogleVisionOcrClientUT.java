package com.smarthealthdog.backend.clients.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class GoogleVisionOcrClientUT {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @InjectMocks
    private GoogleVisionOcrClient client;

    private static final byte[] IMAGE = new byte[] { 1, 2, 3, 4 };

    @BeforeEach
    void setUp() {
        // ObjectMapper 와 추출기는 실제 구현을 쓴다 — 응답 파싱 결과를 끝까지 확인하기 위해.
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(client, "fieldExtractor", new HealthCertificateFieldExtractor());

        ReflectionTestUtils.setField(client, "endpoint", "http://localhost:9999/v1/images:annotate");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "languageHint", "ko");
        ReflectionTestUtils.setField(client, "readTimeoutSeconds", 15);

        // @InjectMocks 가 못 채우는 필드 초기화값을 목으로 교체한다.
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
    }

    private void stubResponse(int status, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        if (body != null) {
            when(httpResponse.body()).thenReturn(body);
        }
        when(httpClient.send(any(HttpRequest.class), bodyHandler())).thenReturn(httpResponse);
    }

    private static String visionBody(String fullText, String confidenceJson) {
        return """
            {
              "responses": [
                {
                  "fullTextAnnotation": {
                    "text": "%s",
                    "pages": [ { "blocks": [ %s ] } ]
                  }
                }
              ]
            }
            """.formatted(fullText, confidenceJson);
    }

    @Test
    void 정상_응답에서_12개_항목을_추출한다() throws Exception {
        String fullText = "진단서\\n병명: 심장비대\\n진단 연원일: 2020.10.28";
        stubResponse(200, visionBody(fullText, "{\"confidence\": 0.9}"));

        HealthCertificateOcrResult result = client.extract(IMAGE, "image/jpeg");

        assertEquals("심장비대", result.diseaseName());
        assertEquals("2020.10.28", result.diagnosedDateText());
        assertTrue(result.documentKeywordFound());
        assertEquals(0.9, result.averageConfidence(), 0.0001);
    }

    @Test
    void 요청_본문은_DOCUMENT_TEXT_DETECTION_과_한국어_힌트를_담는다() throws Exception {
        stubResponse(200, visionBody("진단서\\n병명: 심장비대\\n진단 연원일: 2020.10.28",
                                     "{\"confidence\": 0.9}"));

        client.extract(IMAGE, "image/jpeg");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest request = captor.getValue();
        assertEquals("POST", request.method());

        // API 키는 URI 쿼리로 간다.
        assertTrue(request.uri().toString().contains("key=test-api-key"));

        String body = bodyOf(request);
        assertTrue(body.contains("DOCUMENT_TEXT_DETECTION"), "모드가 본문에 있어야 한다");
        assertTrue(body.contains("languageHints"), "언어 힌트가 본문에 있어야 한다");
        assertTrue(body.contains("\"ko\""), "한국어 힌트여야 한다");
        assertTrue(body.contains(Base64.getEncoder().encodeToString(IMAGE)),
                   "이미지는 base64 로 담긴다");
    }

    @Test
    void 바이트가_비면_호출하지_않고_INVALID_IMAGE로_거부한다() throws Exception {
        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[0], "image/jpeg")
        );

        assertEquals(ErrorCode.INVALID_IMAGE, e.getErrorCode());
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void 서버_오류_5xx는_503으로_매핑한다() throws Exception {
        stubResponse(500, null);

        ServiceUnavailableException e = assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 타임아웃과_연결_실패는_503으로_매핑한다() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
            .thenThrow(new IOException("connect timed out"));

        ServiceUnavailableException e = assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 인터럽트는_플래그를_복원하고_503으로_매핑한다() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
            .thenThrow(new InterruptedException("interrupted"));

        assertThrows(ServiceUnavailableException.class, () -> client.extract(IMAGE, "image/jpeg"));

        assertTrue(Thread.interrupted(), "인터럽트 플래그가 복원되어야 한다");
    }

    @Test
    void 요청_거부_4xx는_500으로_올린다() throws Exception {
        // 잘못된 API 키 할당량 초과 — 사용자 잘못이 아니다.
        stubResponse(403, null);

        InternalServerErrorException e = assertThrows(
            InternalServerErrorException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, e.getErrorCode());
    }

    @Test
    void 본문을_파싱할_수_없으면_OCR_RECOGNITION_FAILED로_거부한다() throws Exception {
        stubResponse(200, "not json at all");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void responses가_비면_OCR_RECOGNITION_FAILED로_거부한다() throws Exception {
        stubResponse(200, "{\"responses\": []}");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void HTTP_200인데_responses0_error가_있으면_거부한다() throws Exception {
        // Vision 고유 경로: 이미지 단위 실패를 200 으로 알린다.
        // 이걸 놓치면 모든 항목이 null 인 진단서가 저장된다.
        stubResponse(200, """
            { "responses": [ { "error": { "code": 3, "message": "Bad image data" } } ] }
            """);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void fullTextAnnotation이_없으면_거부한다() throws Exception {
        // 글자가 전혀 없는 사진.
        stubResponse(200, "{\"responses\": [ {} ]}");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(IMAGE, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void block_confidence가_없으면_1_0으로_폴백한다() throws Exception {
        // 의도적 fail-open. 0.0 으로 폴백하면 min-confidence 게이트가
        // 모든 요청을 거부해 기능이 전면 중단된다.
        stubResponse(200, visionBody("진단서\\n병명: 심장비대\\n진단 연원일: 2020.10.28", ""));

        HealthCertificateOcrResult result = client.extract(IMAGE, "image/jpeg");

        assertEquals(1.0, result.averageConfidence(), 0.0001);
    }

    @Test
    void block_confidence가_여러개면_평균을_쓴다() throws Exception {
        stubResponse(200, visionBody(
            "진단서\\n병명: 심장비대\\n진단 연원일: 2020.10.28",
            "{\"confidence\": 1.0}, {\"confidence\": 0.6}"
        ));

        HealthCertificateOcrResult result = client.extract(IMAGE, "image/jpeg");

        assertEquals(0.8, result.averageConfidence(), 0.0001);
    }

    /**
     * send 의 반환 타입이 BodyHandler 의 타입 파라미터에서 추론되므로,
     * 타입 없는 any() 로는 HttpResponse&lt;String&gt; 을 stub 할 수 없다.
     */
    private static HttpResponse.BodyHandler<String> bodyHandler() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static String bodyOf(HttpRequest request) {
        StringBuilder collected = new StringBuilder();

        request.bodyPublisher().orElseThrow().subscribe(
            new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                @Override
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(java.nio.ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    collected.append(new String(bytes, StandardCharsets.UTF_8));
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new IllegalStateException(throwable);
                }

                @Override
                public void onComplete() {
                }
            }
        );

        return collected.toString();
    }
}
