package com.smarthealthdog.backend.dto.periodontitis;

import java.time.Instant;

import com.smarthealthdog.backend.domain.OralHealthRecord;
import com.smarthealthdog.backend.domain.PeriodontalStage;
import com.smarthealthdog.backend.domain.TartarLevel;

public record PeriodontitisRecordResponse(
        Long id,
        Long petId,
        PeriodontalStage periodontalStage,
        TartarLevel tartarLevel,
        Boolean gingivaInflammation,
        Boolean halitosis,
        Integer toothLossCount,
        String note,
        Instant recordedAt
) {
    public static PeriodontitisRecordResponse from(OralHealthRecord record) {
        return new PeriodontitisRecordResponse(
                record.getId(),
                record.getPet().getId(),
                record.getPeriodontalStage(),
                record.getTartarLevel(),
                record.getGingivaInflammation(),
                record.getHalitosis(),
                record.getToothLossCount(),
                record.getNote(),
                record.getRecordedAt()
        );
    }
}