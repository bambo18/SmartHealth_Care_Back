package com.smarthealthdog.backend.domain;

import java.util.Arrays;

public enum PermissionEnum {
    // --- Basic Account Management Permissions ---
    CAN_RESET_PASSWORD("can_reset_password", "비밀번호 재설정"),
    CAN_DEACTIVATE_ACCOUNT("can_deactivate_account", "계정 비활성화"),
    CAN_REACTIVATE_ACCOUNT("can_reactivate_account", "계정 재활성화"),
    CAN_MANAGE_EMAIL_VERIFICATION("can_manage_email_verification", "사용자의 이메일 인증 절차 관리 (이메일 재전송 등)"),

    // --- General User Permissions (User & Profile) ---
    CAN_VIEW_OWN_PROFILE("can_view_own_profile", "자신의 프로필 보기"),
    CAN_EDIT_OWN_PROFILE("can_edit_own_profile", "자신의 프로필 수정"),
    CAN_VIEW_OTHER_USER_PROFILE("can_view_other_user_profile", "다른 사용자 프로필 보기"),
    CAN_FOLLOW_USER("can_follow_user", "사용자 팔로우"),
    CAN_UNFOLLOW_USER("can_unfollow_user", "사용자 언팔로우"),
    CAN_BLOCK_USER("can_block_user", "사용자 차단"),

    // --- General User Permissions (Pet Management) ---
    CAN_ADD_PET("can_add_pet", "반려동물 추가"),
    CAN_VIEW_OWN_PETS("can_view_own_pets", "자신이 등록한 반려동물 목록 보기"),
    CAN_VIEW_OWN_PET_DETAIL("can_view_own_pet_detail", "자신이 등록한 반려동물 상세 정보 보기"),
    CAN_EDIT_PET("can_edit_pet", "반려동물 정보 수정"),
    CAN_DELETE_PET("can_delete_pet", "반려동물 삭제"),

    // --- General User Permissions (Walk & Health Records) ---
    CAN_START_WALK("can_start_walk", "산책 기록 시작"),
    CAN_END_WALK("can_end_walk", "산책 기록 종료"),
    CAN_UPDATE_WALK_RECORD("can_update_walk_record", "산책 기록 수정"),
    CAN_VIEW_OWN_WALK_RECORDS("can_view_own_walk_records", "자신의 산책 기록 목록 보기"),
    CAN_VIEW_OWN_WALK_DETAIL("can_view_own_walk_detail", "자신의 산책 기록 상세 보기"),
    CAN_DELETE_WALK_RECORD("can_delete_walk_record", "산책 기록 삭제"),
    CAN_VIEW_WEEKLY_SUMMARY("can_view_weekly_summary", "주간 산책 요약 보기"),
    CAN_USE_HEALTH_CHECK("can_use_health_check", "건강 검진 기능 사용"),
    CAN_VIEW_OWN_HEALTH_RECORDS("can_view_own_health_records", "자신의 건강 기록 보기"),

    // --- Shelter Account Permissions ---
    CAN_VIEW_SHELT_PROFILE("can_view_shelter_profile", "보호소 프로필 보기"),
    CAN_EDIT_SHELT_PROFILE("can_edit_shelter_profile", "보호소 프로필 수정"),
    CAN_ADD_ADOPTION_ANIMAL("can_add_adoption_animal", "입양 동물 추가"),
    CAN_VIEW_ADOPTION_ANIMALS("can_view_adoption_animals", "입양 동물 목록 보기"),
    CAN_VIEW_ADOPTION_ANIMAL_DETAIL("can_view_adoption_animal_detail", "입양 동물 상세 정보 보기"),
    CAN_EDIT_ADOPTION_ANIMAL("can_edit_adoption_animal", "입양 동물 정보 수정"),
    CAN_DELETE_ADOPTION_ANIMAL("can_delete_adoption_animal", "입양 동물 삭제"),

    // --- Administrator Permissions ---
    CAN_MANAGE_ALL_USERS("can_manage_all_users", "모든 사용자 관리"),
    CAN_MANAGE_ALL_PETS("can_manage_all_pets", "모든 반려동물 관리"),
    CAN_MANAGE_ALL_SHELTERS("can_manage_all_shelters", "모든 보호소 관리"),
    CAN_MANAGE_ALL_WALK_RECORDS("can_manage_all_walk_records", "모든 산책 기록 관리"),
    CAN_MANAGE_ALL_HEALTH_RECORDS("can_manage_all_health_records", "모든 건강 기록 관리"),
    CAN_ACCESS_SYSTEM_METRICS("can_access_system_metrics", "시스템 지표 접근"),
    CAN_ASSIGN_ROLES("can_assign_roles", "역할 할당"),

    // --- AI Model Service Permissions ---
    CAN_UPDATE_HEALTH_RECORDS("can_update_health_records", "반려동물 건강 기록 업데이트");

    private final String name;
    private final String description;

    PermissionEnum(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static PermissionEnum getPermission(final String value) {
        return Arrays.stream(PermissionEnum.values())
            .filter(permission -> permission.getName().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("해당 권한이 없습니다: " + value));
    }

    /**
     * Retrieves the database/string representation of the permission (e.g., "can_login").
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the descriptive text for the permission (e.g., "사용자 로그인").
     */
    public String getDescription() {
        return description;
    }
}