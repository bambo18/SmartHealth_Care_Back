package com.smarthealthdog.backend.domain;

public enum SubmissionFailureReasonEnum {
    SERVICE_ERROR("현재 서비스의 오류로 인해 진행할 수 없습니다. 현재 진단 요청은 삭제됩니다."),
    INFERENCE_ERROR("진단 중 오류가 발생했습니다. 나중에 다시 시도해주세요."),
    TIMEOUT("진단 서비스 응답 시간이 초과되었습니다. 나중에 다시 시도해주세요."),

    // ── 진단서 OCR 전용 ──────────────────────────────────────────────
    // 아래는 모두 "사용자가 다시 찍으면 해결되는" 실패다. 위의 세 가지처럼
    // 서비스 장애로 읽히면 사용자가 재시도를 포기하므로 따로 둔다.
    // RemoveServiceFailureAIInferenceTasks 의 자동 삭제 대상(SERVICE_ERROR·TIMEOUT)에는
    // 포함되지 않는다 — 사용자가 결과를 확인하고 직접 지워야 한다.

    IMAGE_UNREADABLE("이미지를 읽을 수 없습니다. 지원하는 형식인지, 파일이 손상되지 않았는지 확인해주세요."),
    NO_TEXT_DETECTED("이미지에서 글자를 찾지 못했습니다. 더 밝고 선명한 사진으로 다시 시도해주세요."),
    NOT_A_CERTIFICATE("진단서 양식으로 보이지 않는 이미지입니다."),
    LOW_CONFIDENCE("진단서를 인식하지 못했습니다. 더 밝고 선명한 사진으로 다시 시도해주세요."),
    REQUIRED_FIELD_MISSING("진단서에서 병명 또는 진단 연원일을 찾지 못했습니다.");

    private final String description;

    SubmissionFailureReasonEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
