package com.smarthealthdog.backend.dto.diagnosis.get;

import lombok.Value;

@Value
public class UrineMeasurementResult {
    private final String analyte;
    private final String resultValue;
}
