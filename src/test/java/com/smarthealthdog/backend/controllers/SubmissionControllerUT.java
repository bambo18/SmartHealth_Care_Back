package com.smarthealthdog.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smarthealthdog.backend.services.SubmissionService;

class SubmissionControllerUT {

    private SubmissionService submissionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SubmissionController(submissionService)
        ).build();
    }

    @Test
    void updateSubmissionStatus_rejectsRequestWithoutStatus() throws Exception {
        mockMvc.perform(patch("/api/submissions/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionService);
    }
}