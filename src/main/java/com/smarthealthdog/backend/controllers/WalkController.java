package com.smarthealthdog.backend.controllers;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
import com.smarthealthdog.backend.dto.walk.EndWalkRequest;
import com.smarthealthdog.backend.dto.walk.WalkResponse;
import com.smarthealthdog.backend.services.WalkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}/walks")
@RequiredArgsConstructor
public class WalkController {

    private final WalkService walkService;


    // ============================================================
    // 산책 시작
    // ============================================================

    @PostMapping
    @PreAuthorize("hasAuthority('can_start_walk')")
    public ResponseEntity<Map<String, Object>> start(

            @PathVariable Long petId,

            @RequestBody
            @Valid
            CreateWalkRequest req,

            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        Walk walk =
                walkService.create(
                        petId,
                        userId,
                        req
                );


        /*
         * 조도 센서 데이터를 이후 이 walk_id에 연결해야 하므로
         * 산책 생성 직후 walk_id 반환
         */
        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "walk_id",
                walk.getId()
        );

        body.put(
                "pet_id",
                walk.getPet().getId()
        );

        body.put(
                "start_time",
                walk.getStartTime()
        );

        body.put(
                "status",
                "IN_PROGRESS"
        );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(body);
    }


    // ============================================================
    // 산책 종료
    // ============================================================

    @PatchMapping("/{walkId}/end")
    @PreAuthorize("hasAuthority('can_end_walk')")
    public ResponseEntity<Map<String, Object>> end(

            @PathVariable Long petId,

            @PathVariable Long walkId,

            @RequestBody
            @Valid
            EndWalkRequest req,

            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        Walk walk =
                walkService.end(
                        petId,
                        walkId,
                        userId,
                        req
                );


        return ResponseEntity.ok(
                Map.of(
                        "walk",
                        WalkResponse.toDetailSnake(walk)
                )
        );
    }


    // ============================================================
    // 특정 반려동물 산책 목록 조회
    // ============================================================

    /**
     * TODO:
     *
     * 1. 타임존 처리
     * 2. 날짜 파라미터 검증
     * 3. 소유권 검증
     * 4. 정렬 기준 처리
     * 5. limit / offset 페이징
     */
    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<?> list(

            @PathVariable Long petId,

            @RequestParam(required = false)
            String timezone,

            @RequestParam(required = false)
            String start_date,

            @RequestParam(required = false)
            String end_date,

            @RequestParam(
                    required = false,
                    defaultValue = "date_desc"
            )
            String sort_by,

            @RequestParam(
                    required = false,
                    defaultValue = "20"
            )
            Integer limit,

            @RequestParam(
                    required = false,
                    defaultValue = "0"
            )
            Integer offset
    ) {

        OffsetDateTime start = null;
        OffsetDateTime end = null;


        // --------------------------------------------------------
        // 날짜 파싱
        // --------------------------------------------------------

        try {

            if (start_date != null
                    && !start_date.isBlank()) {

                start =
                        OffsetDateTime.parse(
                                start_date
                                        + "T00:00:00Z"
                        );
            }


            if (end_date != null
                    && !end_date.isBlank()) {

                end =
                        OffsetDateTime.parse(
                                end_date
                                        + "T23:59:59Z"
                        );
            }

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "status",
                                    400,

                                    "message",
                                    "유효하지 않은 쿼리 파라미터입니다. 날짜 형식을 확인하세요."
                            )
                    );
        }


        // --------------------------------------------------------
        // 산책 목록
        // --------------------------------------------------------

        List<Walk> all =
                walkService.listByPet(
                        petId,
                        start,
                        end,
                        sort_by
                );


        // --------------------------------------------------------
        // limit / offset
        // --------------------------------------------------------

        int from =
                Math.max(
                        0,
                        offset
                );


        int to =
                Math.min(
                        all.size(),
                        from
                                + Math.max(
                                        0,
                                        limit
                                )
                );


        List<Walk> pageSlice =
                (from < to)
                        ? all.subList(
                                from,
                                to
                        )
                        : List.of();


        // --------------------------------------------------------
        // 응답 items
        // --------------------------------------------------------

        var items =
                pageSlice.stream()
                        .map(
                                walk -> {

                                    Map<String, Object> item =
                                            new LinkedHashMap<>();

                                    item.put(
                                            "walk_id",
                                            walk.getId()
                                    );

                                    item.put(
                                            "start_time",
                                            walk.getStartTime()
                                    );

                                    /*
                                     * 진행 중인 산책은 null 가능
                                     */
                                    item.put(
                                            "end_time",
                                            walk.getEndTime()
                                    );

                                    item.put(
                                            "duration",
                                            walk.getDurationSeconds()
                                    );

                                    item.put(
                                            "distance",
                                            walk.getDistanceKm()
                                    );

                                    return item;
                                }
                        )
                        .toList();


        // --------------------------------------------------------
        // 페이지 정보
        // --------------------------------------------------------

        Map<String, Object> page =
                new LinkedHashMap<>();

        page.put(
                "limit",
                limit
        );

        page.put(
                "offset",
                offset
        );

        page.put(
                "sort_by",
                sort_by
        );

        page.put(
                "start_date",
                start_date
        );

        page.put(
                "end_date",
                end_date
        );


        // --------------------------------------------------------
        // 최종 응답
        // --------------------------------------------------------

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "pet_id",
                petId
        );

        body.put(
                "total",
                all.size()
        );

        body.put(
                "items",
                items
        );

        body.put(
                "page",
                page
        );


        return ResponseEntity.ok(body);
    }
}