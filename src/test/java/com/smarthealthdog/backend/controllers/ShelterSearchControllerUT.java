package com.smarthealthdog.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.handlers.GlobalExceptionHandler;
import com.smarthealthdog.backend.services.ShelterSearchService;

class ShelterSearchControllerUT {

    private ShelterSearchService shelterSearchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        shelterSearchService = mock(ShelterSearchService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ShelterSearchController(shelterSearchService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void stubEmptyResponse() {
        when(shelterSearchService.search(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ShelterSearchResponse(
                new ShelterSearchResponse.Center(0.0, 0.0),
                0.0,
                "distance",
                0,
                List.of(),
                new ShelterSearchResponse.PageMeta(20, 0)
        ));
    }

    @Test
    void search_rejectsNegativeOffset() throws Exception {
        mockMvc.perform(get("/api/shelters/search")
                        .param("location", "Seoul")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shelterSearchService);
    }

    /*
     * 위도만 보내면 거리 계산을 할 수 없다.
     */
    @Test
    void search_rejectsLatitudeWithoutLongitude() throws Exception {
        mockMvc.perform(get("/api/shelters/search")
                        .param("lat", "37.5"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shelterSearchService);
    }

    /*
     * 사용자가 선택한 행정구역이 서비스까지 그대로 전달되어야 한다.
     */
    @Test
    void search_passesProvinceAndDistrictToService() throws Exception {
        stubEmptyResponse();

        mockMvc.perform(get("/api/shelters/search")
                        .param("province", "경기도")
                        .param("district", "파주시"))
                .andExpect(status().isOk());

        verify(shelterSearchService).search(
                isNull(),
                eq("경기도"),
                eq("파주시"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    /*
     * 좌표는 받되 조회 범위를 좁히지 않는다.
     * 서비스로는 그대로 넘어간다.
     */
    @Test
    void search_passesCoordinatesToService() throws Exception {
        stubEmptyResponse();

        mockMvc.perform(get("/api/shelters/search")
                        .param("lat", "37.6582")
                        .param("lng", "126.8224"))
                .andExpect(status().isOk());

        verify(shelterSearchService).search(
                isNull(),
                isNull(),
                isNull(),
                eq(37.6582),
                eq(126.8224),
                isNull(),
                isNull(),
                isNull()
        );
    }

    /*
     * 구버전 프론트가 radius_km를 보내도 400이 되지 않고,
     * 서비스 호출에도 영향을 주지 않는다.
     */
    @Test
    void search_acceptsAndIgnoresLegacyRadiusKm() throws Exception {
        stubEmptyResponse();

        mockMvc.perform(get("/api/shelters/search")
                        .param("province", "경기도")
                        .param("radius_km", "100")
                        .param("lat", "37.6582")
                        .param("lng", "126.8224"))
                .andExpect(status().isOk());

        verify(shelterSearchService).search(
                isNull(),
                eq("경기도"),
                isNull(),
                eq(37.6582),
                eq(126.8224),
                isNull(),
                isNull(),
                isNull()
        );
    }

    /*
     * 이전에는 1000km를 넘는 radius_km가 400이었다.
     * 이제는 값 자체를 쓰지 않으므로 거부하지 않는다.
     */
    @Test
    void search_doesNotRejectOutOfRangeRadiusKm() throws Exception {
        stubEmptyResponse();

        mockMvc.perform(get("/api/shelters/search")
                        .param("radius_km", "999999"))
                .andExpect(status().isOk());
    }
}
