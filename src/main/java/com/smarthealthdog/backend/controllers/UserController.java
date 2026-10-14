package com.smarthealthdog.backend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.dto.UpdateUserProfileRequest;
import com.smarthealthdog.backend.dto.UserProfile;
import com.smarthealthdog.backend.dto.walk.WalkResponse;
import com.smarthealthdog.backend.services.UserService;
import com.smarthealthdog.backend.services.WalkService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final WalkService walkService;


    // ============================================================
    // 내 프로필 조회
    // ============================================================

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('can_view_own_profile')")
    public ResponseEntity<UserProfile> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );

        UserProfile userProfile =
                userService.getUserProfileById(
                        userId
                );

        return ResponseEntity.ok(
                userProfile
        );
    }


    // ============================================================
    // 내 프로필 수정
    // ============================================================

    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('can_edit_own_profile')")
    public ResponseEntity<UserProfile> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("request")
            @Valid UpdateUserProfileRequest updatedProfile,
            @RequestPart(
                    value = "profilePicture",
                    required = false
            )
            MultipartFile profilePicture
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );

        UserProfile userProfile =
                userService.updateUserProfile(
                        userId,
                        updatedProfile,
                        profilePicture
                );

        return ResponseEntity.ok(
                userProfile
        );
    }


    // ============================================================
    // 주간 산책 비교
    // ============================================================

    @GetMapping("/me/walks/summary/weekly-comparison")
    @PreAuthorize("hasAuthority('can_view_weekly_summary')")
    public ResponseEntity<Map<String, Object>> weeklyComparison(
            @RequestParam(required = false)
            String timezone,
            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );

        Map<String, Object> body =
                walkService.weeklyComparison(
                        userId,
                        timezone
                );

        return ResponseEntity.ok(
                body
        );
    }


    // ============================================================
    // 이번 주 전체 산책 리스트 조회
    // ============================================================

    @GetMapping("/me/walks/this-week")
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<List<Map<String, Object>>> listThisWeekWalks(
            @RequestParam(required = false)
            String timezone,
            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        /*
         * Walk 엔티티를 그대로 반환하면
         *
         * Walk
         *  -> Pet
         *  -> User
         *  -> Role
         *  -> Permission
         *  -> Role
         *  -> ...
         *
         * 형태의 양방향 관계 때문에
         * Jackson JSON 직렬화 과정에서
         * 무한 순환 참조가 발생할 수 있다.
         *
         * 따라서 API 응답에 필요한 값만
         * WalkResponse를 통해 반환한다.
         */
        List<Map<String, Object>> body =
                walkService
                        .listThisWeekWalks(
                                userId,
                                timezone
                        )
                        .stream()
                        .map(
                                WalkResponse::toSnake
                        )
                        .toList();


        return ResponseEntity.ok(
                body
        );
    }
}