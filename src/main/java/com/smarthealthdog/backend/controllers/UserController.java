package com.smarthealthdog.backend.controllers;

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

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('can_view_own_profile')")
    public ResponseEntity<UserProfile> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        UserProfile userProfile = userService.getUserProfileById(userId);
        return ResponseEntity.ok(userProfile);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('can_edit_own_profile')")
    public ResponseEntity<UserProfile> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("request") @Valid UpdateUserProfileRequest updatedProfile,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        UserProfile userProfile = userService.updateUserProfile(userId, updatedProfile, profilePicture);
        return ResponseEntity.ok(userProfile);
    }

    @GetMapping("/me/walks/summary/weekly-comparison")
    @PreAuthorize("hasAuthority('can_view_weekly_summary')")
    public ResponseEntity<Map<String, Object>> weeklyComparison(
            @RequestParam(required = false) String timezone,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        Map<String, Object> body = walkService.weeklyComparison(userId, timezone);
        return ResponseEntity.ok(body);
    }

    
    //이번 주 전체 산책 리스트 조회 API (구현?)
    @GetMapping("/me/walks/this-week")
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<?> listThisWeekWalks(
            @RequestParam(required = false) String timezone,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        var body = walkService.listThisWeekWalks(userId, timezone);
        return ResponseEntity.ok(body);
    }
}
