package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.ShelterItem;
import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.repositories.ShelterSearchRepository;

@ExtendWith(MockitoExtension.class)
class ShelterSearchServiceUT {

    @Mock
    private ShelterSearchRepository shelterRepository;

    @InjectMocks
    private ShelterSearchService shelterSearchService;

    /*
     * 고양시 좌표. 거리 계산/정렬 확인용.
     */
    private static final double GOYANG_LAT = 37.6582;
    private static final double GOYANG_LNG = 126.8224;


    // ============================================================
    // 헬퍼
    // ============================================================

    private Shelter shelter(
            Long id,
            String name,
            String province,
            String district,
            String jurisdiction,
            Double lat,
            Double lng
    ) {

        Shelter shelter = new Shelter();

        shelter.setId(id);
        shelter.setName(name);
        shelter.setProvince(province);
        shelter.setDistrict(district);
        shelter.setJurisdiction(jurisdiction);
        shelter.setActive(true);

        if (lat != null) {
            shelter.setLatitude(BigDecimal.valueOf(lat));
        }

        if (lng != null) {
            shelter.setLongitude(BigDecimal.valueOf(lng));
        }

        return shelter;
    }

    private ShelterSearchResponse search(
            String province,
            String district,
            Double lat,
            Double lng
    ) {

        return shelterSearchService.search(
                null,
                province,
                district,
                lat,
                lng,
                null,
                1000,
                0
        );
    }


    // ============================================================
    // 1. province / district 없음 -> 전체 보호소
    // ============================================================

    @Test
    void search_withoutRegion_returnsAllShelters() {

        when(shelterRepository.findAllActive()).thenReturn(List.of(
                shelter(1L, "A", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82),
                shelter(2L, "B", "서울특별시", "마포구", "서울특별시: 마포구", 37.55, 126.90)
        ));

        ShelterSearchResponse response = search(null, null, null, null);

        assertEquals(2, response.total());
        verify(shelterRepository, never()).searchActiveByProvince(anyString());
    }


    /*
     * 좌표만 보내도 범위가 좁혀지면 안 된다.
     *
     * 과거에는 radius_km 기본값 2km 필터가 걸려
     * 결과가 0건이 되었다.
     */
    @Test
    void search_withCoordinatesOnly_doesNotNarrowByDistance() {

        when(shelterRepository.findAllActive()).thenReturn(List.of(
                shelter(1L, "가까움", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82),
                shelter(2L, "아주 멂", "제주특별자치도", "제주시", "제주특별자치도: 제주시", 33.49, 126.53)
        ));

        ShelterSearchResponse response = search(null, null, GOYANG_LAT, GOYANG_LNG);

        assertEquals(2, response.total());

        for (ShelterItem item : response.items()) {
            assertNotNull(item.distance_m());
        }
    }


    // ============================================================
    // 2. province만 -> 해당 시·도 전체
    // ============================================================

    @Test
    void search_withProvinceOnly_usesProvinceQuery() {

        when(shelterRepository.searchActiveByProvince("경기도")).thenReturn(List.of(
                shelter(1L, "고양", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82),
                shelter(2L, "용인", "경기도", "용인시", "경기도: 용인시", 37.24, 127.17)
        ));

        ShelterSearchResponse response = search("경기도", null, null, null);

        assertEquals(2, response.total());
        verify(shelterRepository, never()).findAllActive();
    }


    /*
     * 프론트가 짧은 이름을 보내도 동작해야 한다.
     */
    @Test
    void search_withShortProvinceName_stillMatches() {

        when(shelterRepository.searchActiveByProvince("경기")).thenReturn(List.of(
                shelter(1L, "고양", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
        ));

        ShelterSearchResponse response = search("경기", null, null, null);

        assertEquals(1, response.total());
    }


    // ============================================================
    // 3. province + district -> 해당 시·군·구
    // ============================================================

    @Test
    void search_withProvinceAndDistrict_usesAdministrativeRegionQuery() {

        when(shelterRepository.searchActiveByAdministrativeRegion("경기도", "고양시"))
                .thenReturn(List.of(
                        shelter(1L, "고양", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
                ));

        ShelterSearchResponse response = search("경기도", "고양시", null, null);

        assertEquals(1, response.total());
    }


    /*
     * 관외 위탁 보호소.
     *
     * 소재지는 경기도 양주시지만
     * 관할에 파주시가 있으므로 파주시 검색에 포함된다.
     */
    @Test
    void search_includesOutsideDistrictShelterByJurisdiction() {

        Shelter outsideDistrict = shelter(
                1L,
                "한국동물구조관리협회",
                "경기도",
                "양주시",
                "경기도: 구리시, 김포시, 양주시, 파주시 | 서울특별시: 강남구, 강동구",
                37.8701,
                126.9835
        );

        when(shelterRepository.searchActiveByAdministrativeRegion("경기도", "파주시"))
                .thenReturn(List.of(outsideDistrict));

        ShelterSearchResponse response = search("경기도", "파주시", null, null);

        assertEquals(1, response.total());
        assertEquals("한국동물구조관리협회", response.items().get(0).name());
    }


    /*
     * 오탐 1 — 세그먼트 교차.
     *
     * 같은 jurisdiction 문자열에 경기도와 강남구가 모두 있지만
     * 서로 다른 시·도 세그먼트에 속하므로 제외되어야 한다.
     */
    @Test
    void search_rejectsJurisdictionMatchAcrossProvinceSegments() {

        Shelter outsideDistrict = shelter(
                1L,
                "한국동물구조관리협회",
                "경기도",
                "양주시",
                "경기도: 구리시, 김포시, 양주시, 파주시 | 서울특별시: 강남구, 강동구",
                37.8701,
                126.9835
        );

        when(shelterRepository.searchActiveByAdministrativeRegion("경기도", "강남구"))
                .thenReturn(List.of(outsideDistrict));

        ShelterSearchResponse response = search("경기도", "강남구", null, null);

        assertEquals(0, response.total());
        assertTrue(response.items().isEmpty());
    }


    /*
     * 오탐 2 — 부분 문자열.
     *
     * 서구로 조회했을 때 강서구가 걸리면 안 된다.
     */
    @Test
    void search_rejectsDistrictSubstringMatch() {

        when(shelterRepository.searchActiveByAdministrativeRegion("부산광역시", "서구"))
                .thenReturn(List.of(
                        shelter(1L, "강서구 보호소", "부산광역시", "강서구",
                                "부산광역시: 강서구", 35.20, 128.98)
                ));

        ShelterSearchResponse response = search("부산광역시", "서구", null, null);

        assertEquals(0, response.total());
    }


    /*
     * 프론트가 고양시 덕양구처럼 하위 구까지 보내면
     * DB에 저장된 고양시로 접어서 비교한다.
     */
    @Test
    void search_foldsSubDistrictToCityLevel() {

        when(shelterRepository.searchActiveByAdministrativeRegion("경기도", "고양시"))
                .thenReturn(List.of(
                        shelter(1L, "고양", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
                ));

        ShelterSearchResponse response = search("경기도", "고양시 덕양구", null, null);

        assertEquals(1, response.total());
    }


    // ============================================================
    // 4. 좌표는 거리 계산과 정렬에만 쓴다
    // ============================================================

    @Test
    void search_withCoordinates_sortsByDistanceAscending() {

        when(shelterRepository.searchActiveByProvince("경기도")).thenReturn(List.of(
                shelter(1L, "멂", "경기도", "용인시", "경기도: 용인시", 37.24, 127.17),
                shelter(2L, "가까움", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
        ));

        ShelterSearchResponse response = search("경기도", null, GOYANG_LAT, GOYANG_LNG);

        assertEquals(2, response.total());
        assertEquals("가까움", response.items().get(0).name());
        assertEquals("멂", response.items().get(1).name());

        assertTrue(
                response.items().get(0).distance_m()
                        < response.items().get(1).distance_m()
        );
    }


    @Test
    void search_withoutCoordinates_leavesDistanceNull() {

        when(shelterRepository.searchActiveByProvince("경기도")).thenReturn(List.of(
                shelter(1L, "고양", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
        ));

        ShelterSearchResponse response = search("경기도", null, null, null);

        assertNull(response.items().get(0).distance_m());
    }


    /*
     * 명세 예시 요청을 그대로 재현한다.
     *
     * GET /api/shelters/search
     *   ?province=경기도
     *   &district=고양시
     *   &lat=37.6582
     *   &lng=126.8224
     *   &sort_by=distance
     *
     * 조회 범위는 경기도 + 고양시가 정하고,
     * 좌표는 그 결과의 거리 계산/정렬에만 쓴다.
     */
    @Test
    void search_specExample_filtersByRegionThenSortsByDistance() {

        when(shelterRepository.searchActiveByAdministrativeRegion("경기도", "고양시"))
                .thenReturn(List.of(
                        shelter(1L, "고양 먼쪽", "경기도", "고양시",
                                "경기도: 고양시", 37.70, 126.90),
                        shelter(2L, "고양 가까운쪽", "경기도", "고양시",
                                "경기도: 고양시", 37.66, 126.83)
                ));

        ShelterSearchResponse response = shelterSearchService.search(
                null,
                "경기도",
                "고양시",
                GOYANG_LAT,
                GOYANG_LNG,
                "distance",
                1000,
                0
        );

        /*
         * 범위: 지역 조건이 정한다
         */
        assertEquals("distance", response.sort_by());
        assertEquals(2, response.total());
        verify(shelterRepository, never()).findAllActive();

        /*
         * 정렬: 거리가 정한다
         */
        assertEquals("고양 가까운쪽", response.items().get(0).name());
        assertEquals("고양 먼쪽", response.items().get(1).name());

        assertTrue(
                response.items().get(0).distance_m()
                        < response.items().get(1).distance_m()
        );

        /*
         * 반경은 범위 결정에 쓰이지 않는다
         */
        assertEquals(0.0, response.radius_km());
    }


    // ============================================================
    // 5. radius_km은 조회 범위에 쓰지 않는다
    // ============================================================

    @Test
    void search_responseRadiusIsUnused() {

        when(shelterRepository.findAllActive()).thenReturn(List.of(
                shelter(1L, "A", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
        ));

        ShelterSearchResponse response = search(null, null, GOYANG_LAT, GOYANG_LNG);

        assertEquals(0.0, response.radius_km());
    }


    // ============================================================
    // 6. 전체를 뜻하는 값은 조건 없음으로 취급한다
    // ============================================================

    @Test
    void search_treatsAllKeywordAsNoRegion() {

        when(shelterRepository.findAllActive()).thenReturn(List.of(
                shelter(1L, "A", "경기도", "고양시", "경기도: 고양시", 37.65, 126.82)
        ));

        ShelterSearchResponse response = search("전체", "전체", null, null);

        assertEquals(1, response.total());
        verify(shelterRepository, never()).searchActiveByProvince(anyString());
    }
}
