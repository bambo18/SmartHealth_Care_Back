package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.ShelterItem;
import com.smarthealthdog.backend.dto.shelter.ShelterItem.Location;
import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.repositories.ShelterSearchRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShelterSearchService {

    private final ShelterSearchRepository shelterRepository;
    // private final ExternalPlaceClient externalPlaceClient; // 나중에 붙일 자리

    /**
     * 보호소 검색 & 목록 조회
     */
    public ShelterSearchResponse search(
            String locationText,
            Double lat,
            Double lng,
            Double radiusKm,
            String sortBy,
            Integer limit,
            Integer offset
    ) {
        // 1) 기본값 세팅
        double baseLat = lat != null ? lat : 37.5665;   // default 서울
        double baseLng = lng != null ? lng : 126.9780;  // default 서울
        double radius = (radiusKm != null) ? radiusKm : 2.0;
        String sort = (sortBy != null && !sortBy.isBlank()) ? sortBy : "distance";
        int pageSize = (limit != null) ? Math.min(limit, 50) : 20;
        int pageOffset = (offset != null) ? offset : 0;
        
        /*
         * 외부API(kakao/google 등) 호출 
         * List<ShelterItem> externalItems = externalPlaceClient.search(...); // 카카오/구글 결과
           items.addAll(externalItems);
         */

        // 2) 내부 DB에서 1차 검색
        List<Shelter> dbShelters;
        if (locationText != null && !locationText.isBlank()) {
            dbShelters = shelterRepository.findByAddressContainingIgnoreCase(locationText);
            if (dbShelters.isEmpty()) {
                dbShelters = shelterRepository.findByNameContainingIgnoreCase(locationText);
            }
        } else {
            dbShelters = shelterRepository.findAll();
        }

        // 3) 내부 DB → API 스키마로 변환
        List<ShelterItem> items = new ArrayList<>();
        for (Shelter s : dbShelters) {
            Double sLat = toDouble(s.getLatitude());
            Double sLng = toDouble(s.getLongitude());

            Integer distanceM = null;
            if (sLat != null && sLng != null) {
                distanceM = (int) Math.round(haversineMeters(baseLat, baseLng, sLat, sLng));
            }

            ShelterItem item = new ShelterItem(
                    s.getId(),
                    s.getName(),
                    s.getAddress(),
                    s.getPhoneNumber(),
                    (sLat != null && sLng != null) ? new Location(sLat, sLng) : null,
                    distanceM,
                    null,          // rating — 내부 DB에는 없음
                    null,          // review_count — 내부 DB에는 없음
                    null,          // website_url
                    null,          // place_url
                    null           // open_now
            );
            // 반경 필터
            if (distanceM == null || distanceM <= radius * 1000) {
                items.add(item);
            }
        }

        // 4) 정렬
        items.sort(switch (sort) {
            case "rating" -> Comparator
                    .comparing((ShelterItem i) -> i.rating() == null ? 0.0 : i.rating())
                    .reversed()
                    .thenComparing(i -> i.review_count() == null ? 0 : i.review_count(), Comparator.reverseOrder());
            case "review_count" -> Comparator
                    .comparing((ShelterItem i) -> i.review_count() == null ? 0 : i.review_count(), Comparator.reverseOrder())
                    .thenComparing(i -> i.rating() == null ? 0.0 : i.rating(), Comparator.reverseOrder());
            default -> Comparator
                    .comparing((ShelterItem i) -> i.distance_m() == null ? Integer.MAX_VALUE : i.distance_m());
        });

        // 5) 페이징
        int toIndex = Math.min(items.size(), pageOffset + pageSize);
        List<ShelterItem> paged = items.subList(
                Math.min(pageOffset, items.size()),
                toIndex
        );

        return new ShelterSearchResponse(
                new ShelterSearchResponse.Center(baseLat, baseLng),
                radius,
                sort,
                paged.size(),
                paged,
                new ShelterSearchResponse.PageMeta(pageSize, pageOffset)
        );
    }

    private static Double toDouble(BigDecimal v) {
        return v != null ? v.doubleValue() : null;
    }

    // Haversine 거리(m 단위)
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
