package com.smarthealthdog.backend.dto.diagnosis.get;

import lombok.Value;
import java.math.BigDecimal;

@Value
public class DiagnosisResult {
    private final BigDecimal probability;
    
    // We include the full set of translations. The calling service/controller 
    // can filter this set down to a single preferred language for the final output.
    private final ConditionTranslationResult condition;
}