package com.smarthealthdog.backend.clients.ocr;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;

/**
 * 진단서 이미지에서 글자를 추출하는 외부 OCR 서비스의 추상화.
 *
 * OCR 벤더가 확정되지 않았으므로 비즈니스 코드는 이 인터페이스만 알아야 한다.
 * 벤더 교체는 구현체 하나와 설정 키를 갈아끼우는 일로 끝나야 한다.
 */
public interface HealthCertificateOcrClient {

    /**
     * 이미지에서 전체 텍스트와 평균 신뢰도를 추출한다.
     *
     * @param imageBytes 이미지 바이트
     * @param contentType 이미지의 MIME 타입 (Tika 판정값)
     * @return 인식 결과
     * @throws ServiceUnavailableException 연결 실패·타임아웃·외부 5xx
     * @throws InvalidRequestDataException 200 응답이지만 본문을 해석할 수 없는 경우
     */
    HealthCertificateOcrResult extract(byte[] imageBytes, String contentType);
}
