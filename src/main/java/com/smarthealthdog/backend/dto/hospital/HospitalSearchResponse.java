package com.smarthealthdog.backend.dto.hospital;

import java.util.List;

public record HospitalSearchResponse(
        Center center,
        double radius_km,
        String sort_by,
        int total,
        List<HospitalItem> items,
        PageMeta page
) {
    public record Center(double lat, double lng) {}
    public record PageMeta(int limit, int offset) {}
}