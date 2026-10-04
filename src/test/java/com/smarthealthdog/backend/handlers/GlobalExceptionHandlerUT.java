package com.smarthealthdog.backend.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.smarthealthdog.backend.dto.ErrorMessage;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;
import com.smarthealthdog.backend.validation.ValidErrorCodeFinder;

/**
 * 503·500 전용 핸들러의 배선을 고정한다.
 *
 * 전용 핸들러가 없으면 두 예외가 맨 아래 Exception catch-all 로 떨어져
 * 503 이어야 할 응답이 500 이 되고, ErrorCode 도 INTERNAL_SERVER_ERROR 로
 * 뭉개진다 — 클라이언트가 재시도 가능 여부를 구분할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerUT {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock private ValidErrorCodeFinder validErrorCodeFinder;

    @Test
    void ServiceUnavailableException은_503과_원래_ErrorCode를_내려준다() {
        ResponseEntity<ErrorMessage> response = handler.handleServiceUnavailableException(
            new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        ErrorMessage body = response.getBody();
        assertNotNull(body);
        assertEquals(List.of(ErrorCode.OCR_SERVICE_UNAVAILABLE.name()), body.getCode());
        assertEquals(List.of(ErrorCode.OCR_SERVICE_UNAVAILABLE.getMessage()), body.getDescriptions());
    }

    @Test
    void InternalServerErrorException은_500과_원래_ErrorCode를_내려준다() {
        ResponseEntity<ErrorMessage> response = handler.handleInternalServerErrorException(
            new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorMessage body = response.getBody();
        assertNotNull(body);
        assertEquals(List.of(ErrorCode.INTERNAL_SERVER_ERROR.name()), body.getCode());
    }

    @Test
    void 응답_본문에_예외_메시지나_스택트레이스가_새어나가지_않는다() {
        // 외부 벤더 응답이나 내부 경로가 사용자에게 노출되면 안 된다.
        ResponseEntity<ErrorMessage> response = handler.handleServiceUnavailableException(
            new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE)
        );

        ErrorMessage body = response.getBody();
        assertNotNull(body);
        assertFalse(
            body.getDescriptions().toString().contains("com.smarthealthdog"),
            "클래스명·스택트레이스가 응답에 포함되면 안 된다: " + body.getDescriptions()
        );
    }

    @Test
    void 신규_ErrorCode_7개는_모두_한국어_메시지를_가진다() {
        List<ErrorCode> added = List.of(
            ErrorCode.OCR_RECOGNITION_FAILED,
            ErrorCode.OCR_REQUIRED_FIELD_MISSING,
            ErrorCode.NOT_A_HEALTH_CERTIFICATE,
            ErrorCode.PERIODONTITIS_INFERENCE_FAILED,
            ErrorCode.OCR_SERVICE_UNAVAILABLE,
            ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND,
            ErrorCode.ORAL_HEALTH_RECORD_NOT_FOUND
        );

        for (ErrorCode code : added) {
            assertNotNull(code.getMessage(), code.name() + " 에 메시지가 없다");
            assertFalse(code.getMessage().isBlank(), code.name() + " 의 메시지가 비어 있다");
        }
    }
}
