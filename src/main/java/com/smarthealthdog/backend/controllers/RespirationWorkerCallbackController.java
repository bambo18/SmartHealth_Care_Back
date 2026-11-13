package com.smarthealthdog.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.respiration.RespirationWorkerFailureRequest;
import com.smarthealthdog.backend.dto.respiration.RespirationWorkerResultRequest;
import com.smarthealthdog.backend.dto.respiration.RespirationWorkerStatusRequest;
import com.smarthealthdog.backend.services.RespirationWorkerCallbackService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasAuthority('can_update_health_records')")
@RequiredArgsConstructor
public class RespirationWorkerCallbackController {
    private final RespirationWorkerCallbackService callbackService;

    /** AI team's API: step 1 - save a positive integer BPM, stay PROCESSING. */
    @PatchMapping("/api/respiration/{videoId}")
    public ResponseEntity<Void> saveRate(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerResultRequest request) {
        callbackService.saveResultOnly(videoId, request.respiratoryRate());
        return ResponseEntity.ok().build();
    }

    /** AI team's API: step 2 - COMPLETED or FAILED. */
    @PatchMapping("/api/respiration/{videoId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerStatusRequest request) {
        callbackService.reportStatus(videoId, request.status(), request.failureReason());
        return ResponseEntity.ok().build();
    }

    /** Legacy, single-call result endpoint retained for compatibility. */
    @PatchMapping("/api/internal/respiration/{videoId}/result")
    public ResponseEntity<Void> reportResult(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerResultRequest request) {
        callbackService.reportResult(videoId, request.respiratoryRate());
        return ResponseEntity.ok().build();
    }

    /** Legacy failure endpoint retained for compatibility. */
    @PatchMapping("/api/internal/respiration/{videoId}/failure")
    public ResponseEntity<Void> reportFailure(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerFailureRequest request) {
        callbackService.reportFailure(videoId, request.failureReason());
        return ResponseEntity.ok().build();
    }
}
