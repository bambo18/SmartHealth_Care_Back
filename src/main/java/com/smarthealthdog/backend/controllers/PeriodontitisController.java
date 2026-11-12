package com.smarthealthdog.backend.controllers;

import java.util.List;

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

import com.smarthealthdog.backend.dto.periodontitis.PeriodontitisRecordRequest;
import com.smarthealthdog.backend.dto.periodontitis.PeriodontitisRecordResponse;
import com.smarthealthdog.backend.services.PeriodontitisService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/periodontitis-records")
@RequiredArgsConstructor
public class PeriodontitisController {

    private final PeriodontitisService periodontitisService;

    @PostMapping
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<PeriodontitisRecordResponse> createRecord(
            @PathVariable Long petId,
            @RequestBody @Valid PeriodontitisRecordRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        PeriodontitisRecordResponse response =
                periodontitisService.createRecord(petId, ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<List<PeriodontitisRecordResponse>> getRecords(
            @PathVariable Long petId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(periodontitisService.getRecords(petId, ownerId));
    }
}