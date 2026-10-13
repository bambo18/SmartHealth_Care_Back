package com.smarthealthdog.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetails;

import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.services.WalkService;

class WalkControllerUT {

    private WalkService walkService;
    private WalkController walkController;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        walkService = mock(WalkService.class);
        walkController = new WalkController(walkService);
        userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("42");
    }

    @Test
    void list_convertsLocalDatesUsingRequestedTimezone() {
        when(walkService.listByPet(
                eq(7L), eq(42L), any(), any(), eq("date_desc"), eq(10), eq(37)))
                .thenReturn(Page.empty());

        walkController.list(
                7L, userDetails, "Asia/Seoul", "2026-10-06", "2026-10-07",
                "date_desc", 10, 37);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(walkService).listByPet(
                eq(7L), eq(42L), startCaptor.capture(), endCaptor.capture(),
                eq("date_desc"), eq(10), eq(37));

        ZoneId seoul = ZoneId.of("Asia/Seoul");
        assertEquals(
                LocalDate.parse("2026-10-06").atStartOfDay(seoul).toInstant(),
                startCaptor.getValue());
        assertEquals(
                LocalDate.parse("2026-10-08").atStartOfDay(seoul).toInstant(),
                endCaptor.getValue());
    }

    @Test
    void list_rejectsUnknownTimezoneBeforeCallingService() {
        assertThrows(InvalidRequestDataException.class, () -> walkController.list(
                7L, userDetails, "Invalid/Zone", null, null, "date_desc", 10, 0));

        verifyNoInteractions(walkService);
    }
}