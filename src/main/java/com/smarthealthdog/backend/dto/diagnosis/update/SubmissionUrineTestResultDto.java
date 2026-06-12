package com.smarthealthdog.backend.dto.diagnosis.update;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class SubmissionUrineTestResultDto {
    @NotBlank
    @Size(min = 1, max = 512)
    String analyte;
    @NotBlank
    @Size(min = 1, max = 512)
    String value;
    @NotEmpty
    List<Short> colorRGB;
}
