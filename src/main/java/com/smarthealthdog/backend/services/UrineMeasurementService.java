package com.smarthealthdog.backend.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.UrineAnalyte;
import com.smarthealthdog.backend.domain.UrineMeasurement;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionUrineTestResultDto;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionUrineTestUpdateRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.UrineMeasurementRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UrineMeasurementService {
    private final UrineAnalyteService urineAnalyteService;
    private final UrineMeasurementRepository urineMeasurementRepository;

    /**
     * 특정 진단에 대한 소변 검사 결과 조회
     * @param submission 진단 엔티티
     * @return 소변 검사 결과 리스트
     */
    public List<UrineMeasurement> getUrineMeasurementsForSubmission(Submission submission) {
        return urineMeasurementRepository.findMeasurementBySubmissionWithAnalyte(submission);
    }

    /**
     * 소변 검사 결과 처리
     * @param submission 진단 엔티티
     * @param request 소변 검사 업데이트 요청 DTO
     * @throws InvalidRequestDataException 유효하지 않은 입력 데이터인 경우 발생
     */
    public void processUrineMeasurements(Submission submission, SubmissionUrineTestUpdateRequest request) {
        List<UrineAnalyte> analytes = urineAnalyteService.getAllUrineAnalytes();
        if (analytes.size() <= 0) {
            throw new IllegalStateException("No urine analytes found in the system.");
        }

        List<SubmissionUrineTestResultDto> results = request.getResults();
        List<UrineMeasurement> measurements = new ArrayList<>();
        for (SubmissionUrineTestResultDto result : results) {
            UrineAnalyte matchingAnalyte = analytes.stream()
                    .filter(analyte -> analyte.getName().equalsIgnoreCase(result.getAnalyte()))
                    .findFirst()
                    .orElse(null);

            if (matchingAnalyte == null) {
                continue;
            }

            List<Short> colorRGB = result.getColorRGB();
            UrineMeasurement measurement = UrineMeasurement.builder()
                    .submission(submission)
                    .analyte(matchingAnalyte)
                    .value(result.getValue())
                    .colorR(colorRGB.get(0))
                    .colorG(colorRGB.get(1))
                    .colorB(colorRGB.get(2))
                    .build();
            measurements.add(measurement);
        }

        if (measurements.isEmpty()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        urineMeasurementRepository.saveAll(measurements);
    }
}
