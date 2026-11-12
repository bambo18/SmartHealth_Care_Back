package com.smarthealthdog.backend.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.respiration.CreateRespirationUploadRequest;
import com.smarthealthdog.backend.dto.respiration.RespirationRecordResponse;
import com.smarthealthdog.backend.dto.respiration.RespirationUploadUrlResponse;
import com.smarthealthdog.backend.services.RespirationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/respiration")
@PreAuthorize("hasAuthority('can_use_health_check')")
@RequiredArgsConstructor
public class RespirationController {
    private final RespirationService respirationService;

    private static Long ownerId(UserDetails userDetails) {
        return Long.parseLong(userDetails.getUsername());
    }

    @PostMapping("/upload-url")
    public ResponseEntity<RespirationUploadUrlResponse> prepareUpload(
            @PathVariable Long petId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRespirationUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(respirationService.prepareUpload(petId, ownerId(userDetails), request));
    }

    @PostMapping("/{videoId}/complete")
    public ResponseEntity<RespirationRecordResponse> completeUpload(
            @PathVariable Long petId,
            @PathVariable UUID videoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(respirationService.completeUpload(petId, videoId, ownerId(userDetails)));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<RespirationRecordResponse> getOne(
            @PathVariable Long petId,
            @PathVariable UUID videoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(respirationService.getOne(petId, videoId, ownerId(userDetails)));
    }

    @GetMapping
    public ResponseEntity<List<RespirationRecordResponse>> list(
            @PathVariable Long petId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(respirationService.list(petId, ownerId(userDetails)));
    }
}
