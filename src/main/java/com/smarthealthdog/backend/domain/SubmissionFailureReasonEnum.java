package com.smarthealthdog.backend.domain;

public enum SubmissionFailureReasonEnum {
    SERVICE_ERROR("현재 서비스의 오류로 인해 진행할 수 없습니다. 현재 진단 요청은 삭제됩니다."),
    INFERENCE_ERROR("진단 중 오류가 발생했습니다. 나중에 다시 시도해주세요."),
    TIMEOUT("진단 서비스 응답 시간이 초과되었습니다. 나중에 다시 시도해주세요.");

    private final String description;

    SubmissionFailureReasonEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
