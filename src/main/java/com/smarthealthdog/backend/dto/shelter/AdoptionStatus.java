//입양-보호소 부분 (입양 가능 동물 목록 응답 구조)
package com.smarthealthdog.backend.dto.shelter;

public enum AdoptionStatus {
    AVAILABLE, PENDING, ADOPTED;

    public static AdoptionStatus fromNullable(String s) {
        if (s == null || s.isBlank()) return AVAILABLE;
        try { return AdoptionStatus.valueOf(s.toUpperCase()); }
        catch (Exception e) { return AVAILABLE; }
    }
}