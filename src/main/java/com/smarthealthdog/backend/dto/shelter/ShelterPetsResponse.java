package com.smarthealthdog.backend.dto.shelter;

import java.util.List;

public record ShelterPetsResponse(
        Long shelter_id,
        Integer total,
        List<ShelterPetItem> items,
        Integer limit,
        Integer offset,
        String status
) {
    public static ShelterPetsResponse of(Long shelterId, List<ShelterPetItem> items, Integer limit, Integer offset, String status) {
        return new ShelterPetsResponse(
                shelterId,
                items.size(),
                items,
                limit,
                offset,
                status
        );
    }
}