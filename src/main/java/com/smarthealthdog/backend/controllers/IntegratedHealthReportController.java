package com.smarthealthdog.backend.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.services.IntegratedHealthReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/health-reports")
@RequiredArgsConstructor
public class IntegratedHealthReportController {
    private final IntegratedHealthReportService service;

    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable Long petId,
            @RequestParam IntegratedHealthReportService.PeriodType periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(service.get(petId, userId, periodType, date));
    }
}
