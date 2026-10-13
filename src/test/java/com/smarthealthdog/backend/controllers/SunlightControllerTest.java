package com.smarthealthdog.backend.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.smarthealthdog.backend.domain.LightSensorSample;
import com.smarthealthdog.backend.domain.Permission;
import com.smarthealthdog.backend.domain.PermissionEnum;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.repositories.LightSensorSampleRepository;
import com.smarthealthdog.backend.repositories.PermissionRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RoleRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.utils.JWTUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * 일광 노출 달성률 조회 통합 테스트
 *
 * GET /api/pets/{petId}/sunlight/today 를
 * 실제 JWT 인증 + 권한 검사 + DB 조회까지 거쳐 검증한다.
 *
 * 선택적 date 쿼리 파라미터가
 * 과거 특정 날짜의 누적 결과를 돌려주는지,
 * 응답의 date가 YYYY-MM-DD 문자열로 나가는지 확인한다.
 */
@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SunlightControllerTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private WalkRepository walkRepository;

    @Autowired
    private LightSensorSampleRepository lightSensorSampleRepository;

    private Long petId;
    private String accessToken;

    /*
     * 조도 데이터를 심어 둔 과거 날짜
     */
    private LocalDate sampleDate;


    @BeforeAll
    void setUp() {

        /*
         * 1. 권한 + 역할
         */
        Permission viewWalks = new Permission();
        viewWalks.setName(
                PermissionEnum.CAN_VIEW_OWN_WALK_RECORDS
        );
        viewWalks.setDescription(
                PermissionEnum.CAN_VIEW_OWN_WALK_RECORDS
                        .getDescription()
        );
        permissionRepository.save(viewWalks);

        Role userRole = new Role();
        userRole.setName(RoleEnum.USER);
        userRole.setDescription("Standard user role");
        userRole.setPermissions(new HashSet<>());
        userRole.getPermissions().add(viewWalks);
        roleRepository.save(userRole);


        /*
         * 2. 사용자 + 반려동물
         */
        User owner = User.builder()
                .email("sunlight-owner@example.com")
                .password("{noop}not-used")
                .nickname("조도테스트")
                .role(userRole)
                .build();
        userRepository.save(owner);

        Pet pet = Pet.builder()
                .owner(owner)
                .name("햇살이")
                .species(PetSpecies.DOG)
                .gender(PetGender.MALE)
                .isNeutered(false)
                .weightKg(new BigDecimal("5.50"))
                .build();
        petRepository.save(pet);

        petId = pet.getId();


        /*
         * 3. 3일 전 10:00 ~ 10:30 (Asia/Seoul) 산책
         *
         * 30초 간격 60개 샘플 = 10분 구간 3개
         * 각 구간 평균 3,000 Lux (기준 2,000 Lux 이상)
         *
         * → 인정 구간 3개
         * → 3 × 20,000 = 60,000 (일일 목표 달성)
         */
        sampleDate =
                LocalDate.now(SEOUL)
                        .minusDays(3);

        Instant walkStart =
                sampleDate.atStartOfDay(SEOUL)
                        .plusHours(10)
                        .toInstant();

        Instant walkEnd =
                walkStart.plusSeconds(30 * 60);

        Walk walk = Walk.builder()
                .pet(pet)
                .startTime(walkStart)
                .endTime(walkEnd)
                .distanceKm(new BigDecimal("1.20"))
                .build();
        walkRepository.save(walk);

        for (int i = 0; i < 60; i++) {

            lightSensorSampleRepository.save(
                    LightSensorSample.builder()
                            .walk(walk)
                            .clientSampleId(UUID.randomUUID())
                            .measuredAt(
                                    walkStart.plusSeconds(i * 30L)
                            )
                            .lux(3_000.0)
                            .build()
            );
        }


        /*
         * 4. JWT - subject는 user.publicId
         */
        accessToken =
                jwtUtils.generateAccessToken(
                        owner.getPublicId().toString(),
                        new Date()
                );
    }


    @AfterAll
    void tearDown() {

        lightSensorSampleRepository.deleteAll();
        walkRepository.deleteAll();
        petRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }


    // ============================================================
    // date 전달 - 그 날 하루 누적
    // ============================================================

    @Test
    @DisplayName("date로 과거 날짜를 보내면 그 날의 누적 결과를 돌려준다")
    void today_withPastDate_returnsThatDayProgress() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                petId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .param(
                                        "date",
                                        sampleDate.toString()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                /*
                 * 응답의 date는 요청한 날짜를
                 * YYYY-MM-DD 문자열로 되돌려준다
                 */
                .andExpect(
                        jsonPath("$.sunlight.date")
                                .value(sampleDate.toString())
                )
                .andExpect(
                        jsonPath("$.sunlight.target_lux_minutes")
                                .value(60_000)
                )
                .andExpect(
                        jsonPath("$.sunlight.achieved_lux_minutes")
                                .value(60_000)
                )
                .andExpect(
                        jsonPath("$.sunlight.qualifying_minutes")
                                .value(30)
                )
                .andExpect(
                        jsonPath("$.sunlight.qualified_windows")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.sunlight.sample_count")
                                .value(60)
                )
                .andExpect(
                        jsonPath("$.sunlight.progress_percent")
                                .value(100.0)
                )
                .andExpect(
                        jsonPath("$.sunlight.completed")
                                .value(true)
                );
    }


    // ============================================================
    // date 생략 - 오늘
    // ============================================================

    @Test
    @DisplayName("date를 생략하면 오늘 기준으로 조회한다")
    void today_withoutDate_returnsToday() throws Exception {

        LocalDate today =
                LocalDate.now(SEOUL);

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                petId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sunlight.date")
                                .value(today.toString())
                )
                /*
                 * 오늘은 조도 데이터를 심지 않았으므로
                 * 오류가 아니라 달성률 0
                 */
                .andExpect(
                        jsonPath("$.sunlight.achieved_lux_minutes")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.sunlight.sample_count")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.sunlight.completed")
                                .value(false)
                );
    }


    // ============================================================
    // 데이터가 없는 과거 날짜
    // ============================================================

    @Test
    @DisplayName("조도 데이터가 없는 날짜는 오류가 아니라 달성률 0으로 응답한다")
    void today_withEmptyDate_returnsZeroProgress() throws Exception {

        LocalDate emptyDate =
                sampleDate.minusDays(1);

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                petId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .param(
                                        "date",
                                        emptyDate.toString()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sunlight.date")
                                .value(emptyDate.toString())
                )
                .andExpect(
                        jsonPath("$.sunlight.qualified_windows")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.sunlight.progress_percent")
                                .value(0.0)
                );
    }


    // ============================================================
    // 잘못된 날짜 형식
    // ============================================================

    @Test
    @DisplayName("date 형식이 잘못되면 INVALID_PARAMETER_TYPE으로 400을 응답한다")
    void today_withInvalidDate_returnsBadRequest() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                petId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .param(
                                        "date",
                                        "2026-9-5"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code[0]")
                                .value(
                                        ErrorCode.INVALID_PARAMETER_TYPE
                                                .name()
                                )
                );
    }


    // ============================================================
    // 인증
    // ============================================================

    @Test
    @DisplayName("토큰이 없으면 401")
    void today_withoutToken_returnsUnauthorized() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                petId
                        )
                                .param(
                                        "date",
                                        sampleDate.toString()
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }


    // ============================================================
    // 제거된 중복 엔드포인트
    // ============================================================

    @Test
    @DisplayName("중복이었던 GET /sunlight 은 더 이상 존재하지 않는다")
    void removedSunlightEndpoint_returnsNotFound() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight",
                                petId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .param(
                                        "date",
                                        sampleDate.toString()
                                )
                )
                .andExpect(
                        status().isNotFound()
                );
    }
}
