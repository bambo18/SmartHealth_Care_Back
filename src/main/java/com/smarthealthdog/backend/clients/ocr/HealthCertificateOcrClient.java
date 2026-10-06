package com.smarthealthdog.backend.clients.ocr;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;

/**
 * 진단서 OCR 추상화.
 *
 * 구현(GoogleVisionOcrClient)은 이 인터페이스 뒤에 숨긴다.
 * 테스트에서 가짜 구현으로 갈아끼우기 위한 것이기도 하다.
 */
public interface HealthCertificateOcrClient {

    /**
     * 진단서 이미지에서 12개 항목을 추출한다.
     *
     * @param imageBytes 이미지 바이트
     * @param contentType Tika 가 판정한 MIME 타입
     * @return 추출 결과 (판별은 호출자가 한다)
     * @throws ServiceUnavailableException 연결 실패 타임아웃 5xx — {@code OCR_SERVICE_UNAVAILABLE}
     * @throws InternalServerErrorException 4xx (잘못된 요청 키) — 사용자 잘못이 아니다
     * @throws InvalidRequestDataException 200 이지만 본문 파싱 실패 — {@code OCR_RECOGNITION_FAILED}
     */
    HealthCertificateOcrResult extract(byte[] imageBytes, String contentType);
}
