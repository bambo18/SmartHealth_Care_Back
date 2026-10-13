package com.smarthealthdog.backend.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    @Test
    void search_rejectsNegativeOffset() throws Exception {
        mockMvc.perform(get("/api/shelters/search")
                        .param("location", "Seoul")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shelterSearchService);
    }

}