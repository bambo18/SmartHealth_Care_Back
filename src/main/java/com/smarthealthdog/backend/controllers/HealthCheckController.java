package com.smarthealthdog.backend.controllers;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.smarthealthdog.backend.dto.healthcheck.HealthCheckCreateRequest;
import com.smarthealthdog.backend.dto.healthcheck.HealthCheckResponse;
import com.smarthealthdog.backend.dto.healthcheck.HealthCheckSummaryResponse;
import com.smarthealthdog.backend.services.HealthCheckService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/health-checks")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('can_use_health_check')")
public class HealthCheckController {
    private final HealthCheckService service;

    private Long userId(UserDetails principal) {
        return Long.parseLong(principal.getUsername());
    }

    @PostMapping
    public ResponseEntity<HealthCheckResponse> create(
            @PathVariable Long petId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody HealthCheckCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(petId, userId(principal), request));
    }

    @GetMapping
    public Page<HealthCheckSummaryResponse> list(
            @PathVariable Long petId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(petId, userId(principal), page, size);
    }

    @GetMapping("/{recordId}")
    public HealthCheckResponse detail(
            @PathVariable Long petId,
            @PathVariable Long recordId,
            @AuthenticationPrincipal UserDetails principal) {
        return service.detail(petId, userId(principal), recordId);
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long petId,
            @PathVariable Long recordId,
            @AuthenticationPrincipal UserDetails principal) {
        service.delete(petId, userId(principal), recordId);
        return ResponseEntity.noContent().build();
    }
}
