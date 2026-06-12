package com.smarthealthdog.backend.controllers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.CreateWalkRequest;
import com.smarthealthdog.backend.dto.walk.WalkResponse;
import com.smarthealthdog.backend.services.WalkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/walks")
@RequiredArgsConstructor
public class WalkController {
    private final WalkService walkService;

    /* 산책 시작 */
    @PostMapping
    @PreAuthorize("hasAuthority('can_start_walk')")
    public ResponseEntity<Void> start(
            @PathVariable Long petId,
            @RequestBody @Valid CreateWalkRequest req,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        walkService.create(petId, userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    /*산책 종료*/
    @PatchMapping("/{walkId}/end")
    @PreAuthorize("hasAuthority('can_end_walk')")
    public ResponseEntity<Map<String, Object>> end(
            @PathVariable Long petId,
            @PathVariable Long walkId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());

        Walk walk = walkService.end(petId, walkId, userId);

        return ResponseEntity.ok(Map.of(
                "walk", WalkResponse.toDetailSnake(walk)
        ));
    }


    /** 특정 반려동물 산책 목록 조회 */
    /** TODO: 
     * 1. 타임존 처리 - 쿼리파라미터로 IANA 타임존 문자열 받기
     * 2. 날짜 파라미터 검증 - 형식이 YYYY-MM-DD인지 확인
     * 3. (JWT 붙인 후) 소유권 검증
     * 4. 정렬 기준 처리 (date_asc, date_desc)
     * 5. limit/offset 페이징 처리
     */
    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<?> list(
             @PathVariable Long petId,
             @RequestParam(required = false) String timezone, // IANA 타임존 문자열
             @RequestParam(required = false) String start_date, // YYYY-MM-DD
             @RequestParam(required = false) String end_date,   // YYYY-MM-DD
             @RequestParam(required = false, defaultValue = "date_desc") String sort_by,
             @RequestParam(required = false, defaultValue = "20") Integer limit,
             @RequestParam(required = false, defaultValue = "0") Integer offset
     ) {
         // 날짜 파싱 (00:00:00Z ~ 23:59:59Z)
         OffsetDateTime start = null;
         OffsetDateTime end = null;
         try {
             if (start_date != null && !start_date.isBlank())
                 start = OffsetDateTime.parse(start_date + "T00:00:00Z");
             if (end_date != null && !end_date.isBlank())
                 end = OffsetDateTime.parse(end_date + "T23:59:59Z");
         } catch (Exception e) {
             return ResponseEntity.badRequest().body(Map.of(
                     "status", 400,
                     "message", "유효하지 않은 쿼리 파라미터입니다. 날짜 형식을 확인하세요."
             ));
         }
        

         // (JWT 붙인 후) 소유권 검증 추가 예정
         List<Walk> all = walkService.listByPet(petId, start, end, sort_by);

         // limit/offset 슬라이싱
         int from = Math.max(0, offset);
         int to = Math.min(all.size(), from + Math.max(0, limit));
         List<Walk> pageSlice = (from < to) ? all.subList(from, to) : List.of();

         var items = pageSlice.stream()
        .map(w -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("walk_id", w.getId());
            m.put("start_time", w.getStartTime());
            m.put("end_time", w.getEndTime());
            m.put("duration", w.getDurationSeconds());
            m.put("distance", w.getDistanceKm());
            return m;
        })
        .toList();

         Map<String, Object> page = new java.util.LinkedHashMap<>();
        page.put("limit", limit);
        page.put("offset", offset);
        page.put("sort_by", sort_by);
        page.put("start_date", start_date); // null이어도 OK
        page.put("end_date", end_date);     // null이어도 OK

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("pet_id", petId);
        body.put("total", all.size());
        body.put("items", items);
        body.put("page", page);

        return ResponseEntity.ok(body);
            
    }
}