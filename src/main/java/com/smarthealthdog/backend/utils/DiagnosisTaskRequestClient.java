package com.smarthealthdog.backend.utils;

import java.util.List;

import com.smarthealthdog.backend.dto.diagnosis.create.RequestDiagnosisData;

public interface DiagnosisTaskRequestClient {
    /**
     * AI 진단 작업을 배치로 전송합니다.
     * @param requestDataList 전송할 진단 요청 데이터의 리스트
     */
    void sendDiagnosisTaskInBatch(List<RequestDiagnosisData> requestDataList); 

    /**
     * AI 진단 작업을 배치로 제거합니다.
     * @param celeryTaskStrings 제거할 Celery 작업 문자열의 리스트
     */
    void removeDiagnosisTasksInBatch(List<String> celeryTaskStrings);
}
