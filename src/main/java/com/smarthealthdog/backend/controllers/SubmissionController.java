package com.smarthealthdog.backend.controllers;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.data.web.SortDefault.SortDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.diagnosis.get.DiagnosisResult;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionDetail;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionPage;
import com.smarthealthdog.backend.dto.diagnosis.get.UrineMeasurementResult;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionStatusUpdateRequest;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionUrineTestUpdateRequest;
import com.smarthealthdog.backend.services.SubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService submissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionPage> getSubmissionList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "submittedFrom", required = false) Instant submittedFrom,
            @RequestParam(value = "submittedTo", required = false) Instant submittedTo,
            @RequestParam(value = "completedFrom", required = false) Instant completedFrom,
            @RequestParam(value = "completedTo", required = false) Instant completedTo,
            @PageableDefault(
                page = 0,
                size = 15
            ) 
            @SortDefaults({
                @SortDefault(sort = "submittedAt", direction = Sort.Direction.DESC),
                @SortDefault(sort = "status", direction = Sort.Direction.ASC)
            })
            Pageable pageable
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
            submissionService.getSubmissionsByUserId(
                userId, 
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo,
                pageable
            )
        );
    }

    @GetMapping("/pets/{petId}")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionPage> getSubmissionsByPetId(
            @PathVariable("petId") Long petId,
            @RequestParam(value = "submittedFrom", required = false) Instant submittedFrom,
            @RequestParam(value = "submittedTo", required = false) Instant submittedTo,
            @RequestParam(value = "completedFrom", required = false) Instant completedFrom,
            @RequestParam(value = "completedTo", required = false) Instant completedTo,
            @PageableDefault(
                page = 0,
                size = 15
            ) 
            @SortDefaults({
                @SortDefault(sort = "submittedAt", direction = Sort.Direction.DESC),
                @SortDefault(sort = "status", direction = Sort.Direction.ASC)
            })
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
            submissionService.getSubmissionsByPetId(
                petId, 
                userId, 
                submittedFrom, 
                submittedTo, 
                completedFrom, 
                completedTo, 
                pageable
            )
        );
    }

    @PatchMapping("/{id}/eye")
    @PreAuthorize("hasAuthority('can_update_health_records')")
    public ResponseEntity<Void> updateDiagnosis(
            @PathVariable("id") UUID submissionId,
            @Valid @RequestBody SubmissionResultRequest request
    ) {
        submissionService.completeEyeTest(submissionId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/urine")
    @PreAuthorize("hasAuthority('can_update_health_records')")
    public ResponseEntity<Void> updateUrineTestResults(
            @PathVariable("id") UUID submissionId,
            @Valid @RequestBody SubmissionUrineTestUpdateRequest request 
    ) {
        submissionService.completeUrineTest(submissionId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('can_update_health_records')")
    public ResponseEntity<Void> updateSubmissionStatus(
            @PathVariable("id") UUID submissionId,
            @RequestBody SubmissionStatusUpdateRequest statusUpdateRequest
    ) {
        submissionService.updateSubmissionStatusAfterInference(submissionId, statusUpdateRequest);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/eye")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionDetail<DiagnosisResult>> getSubmissionById(
            @PathVariable("id") UUID submissionId,
            @RequestParam(value = "languageCode", required = false) String languageCode,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(submissionService.getSubmissionAndDiagnosesById(submissionId, languageCode, userId));
    }

    @GetMapping("/{id}/urine")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionDetail<UrineMeasurementResult>> getUrineSubmissionById(
            @PathVariable("id") UUID submissionId,
            @RequestParam(value = "languageCode", required = false) String languageCode,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(submissionService.getSubmissionAndUrineMeasurementsById(submissionId, languageCode, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<Void> deleteSubmissionById(
            @PathVariable("id") UUID submissionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        submissionService.deleteSubmissionById(submissionId, userId);
        return ResponseEntity.noContent().build();
    }
}