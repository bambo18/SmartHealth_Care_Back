package com.smarthealthdog.backend.dto.hospital;

public record HospitalItem(
        Long hospital_id,
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
