package com.smarthealthdog.backend.dto.periodontitis;

import com.smarthealthdog.backend.domain.PeriodontalStage;
import com.smarthealthdog.backend.domain.TartarLevel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PeriodontitisRecordRequest(
        @NotNull PeriodontalStage periodontalStage,
        TartarLevel tartarLevel,
        Boolean gingivaInflammation,
        Boolean halitosis,
        @Min(0) int toothLossCount,
        String note
) {}