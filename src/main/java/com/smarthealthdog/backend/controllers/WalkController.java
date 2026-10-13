package com.smarthealthdog.backend.controllers;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.services.WalkService;
import com.smarthealthdog.backend.validation.ErrorCode;

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

    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<?> list(

            @PathVariable Long petId,

            @AuthenticationPrincipal
            UserDetails userDetails,

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

        ZoneId zone = parseZone(timezone);
        LocalDate startDate = parseDate(start_date);
        LocalDate endDate = parseDate(end_date);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (limit == null || limit <= 0 || limit > 50 || offset == null || offset < 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        Instant start = startDate == null
                ? null
                : startDate.atStartOfDay(zone).toInstant();
        Instant endExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay(zone).toInstant();

        // --------------------------------------------------------
        // 산책 목록
        // --------------------------------------------------------

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );

        var walkPage =
                walkService.listByPet(
                        petId,
                        userId,
                        start,
                        endExclusive,
                        sort_by,
                        limit,
                        offset
                );


        // --------------------------------------------------------
        // 응답 items
        // --------------------------------------------------------

        var items =
                walkPage.getContent().stream()
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

        Map<String, Object> pageInfo =
                new LinkedHashMap<>();

        pageInfo.put(
                "limit",
                limit
        );

        pageInfo.put(
                "offset",
                offset
        );

        pageInfo.put(
                "sort_by",
                sort_by
        );

        pageInfo.put(
                "start_date",
                start_date
        );

        pageInfo.put(
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
                walkPage.getTotalElements()
        );

        body.put(
                "items",
                items
        );

        body.put(
                "page",
                pageInfo
        );


        return ResponseEntity.ok(body);
    }

        private ZoneId parseZone(String timezone) {
                if (timezone == null || timezone.isBlank()) {
                        return ZoneId.of("UTC");
                }

                try {
                        return ZoneId.of(timezone);
                } catch (DateTimeException e) {
                        throw new InvalidRequestDataException(ErrorCode.INVALID_TIMEZONE);
                }
        }

        private LocalDate parseDate(String date) {
                if (date == null || date.isBlank()) {
                        return null;
                }

                try {
                        return LocalDate.parse(date);
                } catch (DateTimeException e) {
                        throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
                }
        }
}