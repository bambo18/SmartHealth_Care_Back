package com.smarthealthdog.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.WalkResponse;
import com.smarthealthdog.backend.services.WalkService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WalkQueryController {

    private final WalkService walkService;

    /** 개별 산책 상세 조회 (임시: userId 쿼리파라미터로 소유권 확인) */
    @GetMapping("/api/walks/{walkId}")
    @PreAuthorize("hasAuthority('can_view_own_walk_detail')")
    public ResponseEntity<Map<String, Object>> getDetail(
            @PathVariable Long walkId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        Walk w = walkService.get(walkId, userId); // 없으면 404(NotFoundException)

        return ResponseEntity.ok(Map.of(
                "walk", WalkResponse.toDetailSnake(w)
        ));
    }

    @DeleteMapping("/api/walks/{walkId}")
    @PreAuthorize("hasAuthority('can_delete_walk_record')")
    public ResponseEntity<Void> delete(
            @PathVariable Long walkId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        walkService.delete(walkId, userId);
        return ResponseEntity.noContent().build(); // 명세 권장 204
    }
}
