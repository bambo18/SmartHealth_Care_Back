package com.smarthealthdog.backend.dto.shelter;

public record ShelterItem(
        Long shelter_id,
        String name,
        String address,
        String phone_number,
        Location location,
        Integer distance_m,
        Double rating,
        Integer review_count,
        String website_url,
        String place_url,
        Boolean open_now
) {
    public record Location(double lat, double lng) {}
}

