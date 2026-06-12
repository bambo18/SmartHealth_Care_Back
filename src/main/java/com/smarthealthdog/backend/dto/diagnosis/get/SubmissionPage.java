package com.smarthealthdog.backend.dto.diagnosis.get;

import java.util.List;

import lombok.Value;

@Value
public class SubmissionPage {
    public Long pageNumber;
    public Long pageSize;
    public Long totalPages;
    public Long totalElements;
    public Boolean hasNext;
    public List<SubmissionSummary> submissions;
}
