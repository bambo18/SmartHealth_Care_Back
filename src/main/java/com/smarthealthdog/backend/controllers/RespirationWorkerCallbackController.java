package com.smarthealthdog.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.respiration.RespirationWorkerFailureRequest;
import com.smarthealthdog.backend.dto.respiration.RespirationWorkerResultRequest;
import com.smarthealthdog.backend.services.RespirationWorkerCallbackService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/internal/respiration")
@PreAuthorize("hasAuthority('can_update_health_records')")
@RequiredArgsConstructor
public class RespirationWorkerCallbackController {
    private final RespirationWorkerCallbackService callbackService;

    @PatchMapping("/{videoId}/result")
    public ResponseEntity<Void> reportResult(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerResultRequest request) {
        callbackService.reportResult(videoId, request.respiratoryRate());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{videoId}/failure")
    public ResponseEntity<Void> reportFailure(
            @PathVariable UUID videoId,
            @Valid @RequestBody RespirationWorkerFailureRequest request) {
        callbackService.reportFailure(videoId, request.failureReason());
        return ResponseEntity.ok().build();
    }
}
