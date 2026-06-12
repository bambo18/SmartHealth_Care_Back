package com.smarthealthdog.backend.domain;

public enum RoleEnum {
    ADMIN, // 관리자
    SHELTER, // 보호소 관리자
    USER, // 일반 사용자
    BANNED_USER, // 정지된 사용자
    DEACTIVATED_USER, // 비활성화된 사용자
    DELETED_USER, // 탈퇴한 사용자
    SOCIAL_ACCOUNT_USER, // 소셜 계정으로 가입한 사용자 (역할 생성 및 권한 부여 필요)
    AI_MODEL_SERVICE // 진단 AI 모델 서비스 전용 역할
}
