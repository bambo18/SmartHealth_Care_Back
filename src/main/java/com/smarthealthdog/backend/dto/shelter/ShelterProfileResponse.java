package com.smarthealthdog.backend.dto.shelter;

import java.math.BigDecimal;

import com.smarthealthdog.backend.domain.Shelter;

public record ShelterProfileResponse(
        Long shelter_id,
        String name,
        String address,
        String phone_number,
        String operating_hours,
        String introduction,
        BigDecimal latitude,
        BigDecimal longitude,
        String website_url,
        String contact_email,
        Double rating,
        Integer review_count
) {
    public static ShelterProfileResponse from(Shelter s) {
        return new ShelterProfileResponse(
                s.getId(),
                s.getName(),
                s.getAddress(),
                s.getPhoneNumber(),
                null,  // 운영시간 아직 없음
                s.getIntroduction(),
                s.getLatitude(),
                s.getLongitude(),
                null,  // 웹사이트 아직 없음
                null,  // 이메일 아직 없음
                null,  // 평점 아직 없음
                null   // 리뷰수 아직 없음
        );
    }
}