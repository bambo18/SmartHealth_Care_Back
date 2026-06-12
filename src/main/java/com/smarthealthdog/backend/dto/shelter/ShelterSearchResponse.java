package com.smarthealthdog.backend.dto.shelter;

import java.util.List;

public record ShelterSearchResponse(
        Center center,
        double radius_km,
        String sort_by,
        int total,
        List<ShelterItem> items,
        PageMeta page
) {
    public record Center(double lat, double lng) {}
    public record PageMeta(int limit, int offset) {}
}
