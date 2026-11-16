package com.smarthealthdog.backend.controllers;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.health.ManualHealthCertificateRequest;
import com.smarthealthdog.backend.services.ManualHealthCertificateService;

import lombok.RequiredArgsConstructor;

/** 기존 PetController / SubmissionController와 경로가 겹치지 않는 추가 전용 Controller. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManualHealthCertificateController {
    private final ManualHealthCertificateService manualHealthCertificateService;

    /** OCR 없이 수기 내용만 등록. 이번 버전은 이미지 첨부를 지원하지 않음. */
    @PostMapping(path = "/pets/{petId}/submissions/certificate/manual",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<Map<String, Object>> createManual(
            @PathVariable("petId") Long petId,
            @RequestBody ManualHealthCertificateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manualHealthCertificateService.create(petId, ownerId, request));
    }

    /** OCR 완료 후 누락된 필드나 잘못 인식된 필드를 부분 수정. 17개 필드 모두 지원. */
    @PatchMapping(path = "/submissions/{submissionId}/certificate/fields",
                  consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<Map<String, Object>> updateFields(
            @PathVariable("submissionId") UUID submissionId,
            @RequestBody ManualHealthCertificateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
                manualHealthCertificateService.updateFields(submissionId, ownerId, request));
    }

    /** 신규 5개 OCR 항목까지 포함한 17개 전체 필드 조회. */
    @GetMapping("/submissions/{submissionId}/certificate/fields")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<Map<String, Object>> getFields(
            @PathVariable("submissionId") UUID submissionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
                manualHealthCertificateService.getFields(submissionId, ownerId));
    }
}
