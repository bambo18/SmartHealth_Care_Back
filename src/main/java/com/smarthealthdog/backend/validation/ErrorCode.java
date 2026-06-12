package com.smarthealthdog.backend.validation;

public enum ErrorCode {
    LOGIN_FAILURE("로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요."),
    SOCIAL_LOGIN_FAILURE("소셜 로그인에 실패했습니다. 다시 시도해주세요."),
    INVALID_INPUT("잘못된 입력입니다."),
    RESOURCE_NOT_FOUND("요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR("서버에서 예기치 않은 오류가 발생했습니다."),
    FORBIDDEN("권한이 없습니다."),
    INVALID_JWT("잘못된 JWT 토큰이거나 만료되었습니다."),

    // 유저 생성 관련 오류
    INVALID_NICKNAME("닉네임은 3-128자여야 합니다."),
    INVALID_EMAIL("이미 사용 중인 이메일이거나 형식이 올바르지 않습니다."),
    INVALID_PASSWORD("비밀번호는 8-256자여야 하며, 최소 하나의 대문자, 소문자, 숫자 및 특수 문자(!@#$%^&*()-+)을 포함해야 합니다."),
    INVALID_EMAIL_VERIFICATION("이메일 인증 토큰이 만료되었거나 유효하지 않습니다."),

    EMAIL_VERIFICATION_TRIES_EXCEEDED("이메일 인증 시도 횟수를 초과했습니다. 하루 후 다시 시도해주세요."),
    EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED("이메일 인증 실패 횟수를 초과했습니다. 잠시 후 다시 시도해주세요."),

    // 소셜 유저 생성 관련 오류
    INVALID_SOCIAL_ACCESS_TOKEN("소셜 액세스 토큰이 유효하지 않습니다."),

    // 유저 수정 관련
    INVALID_IMAGE("이미지 파일이 유효하지 않거나 지원되지 않는 형식입니다."),

    // 산책 관련 오류
    INVALID_WALK_TIME_RANGE("산책의 종료 시간은 시작 시간 이후여야 합니다."),
    INVALID_WALK_DISTANCE("산책 거리는 0 이상이어야 합니다."),
    INVALID_WALK_PATH("산책 경로가 유효하지 않습니다."),
    WALK_NOT_FOUND("산책을 찾을 수 없습니다."),
    INVALID_TIMEZONE("유효하지 않은 타임존 문자열입니다."),

    // 진단 관련 오류
    INVALID_PAGE_SIZE("페이지 크기는 1에서 15 사이여야 합니다."),
    INVALID_SORT_PROPERTY("정렬 속성이 올바르지 않습니다."),
    REQUEST_TOO_FREQUENT("진단 요청이 너무 자주 발생했습니다. 잠시 후 다시 시도해주세요."),

    // URL 쿼리 파리미터 관련 오류
    INVALID_PARAMETER_TYPE("요청 파라미터의 형식이 올바르지 않습니다."),
    MISSING_REQUIRED_PARAMETER("필수 요청 파라미터가 누락되었습니다."),
    INVALID_PATH_VARIABLE("요청 경로 변수의 형식이 올바르지 않습니다."),

    // 날짜 파라미터 관련 오류
    INVALID_DATE_RANGE("날짜 범위가 올바르지 않습니다. 시작 날짜는 종료 날짜 이전이어야 합니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}