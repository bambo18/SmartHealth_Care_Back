package com.smarthealthdog.backend.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Component;


@Component
public class DateUtils {
    /**
     * 시간대 ID를 기반으로 해당 주의 일요일 자정(00:00:00)의 Instant를 반환합니다.
     * @param timeZoneId 유효한 시간대 ID 문자열 (예: "Asia/Seoul", "UTC") 
     * @return 해당 주의 일요일 자정(00:00:00)의 Instant
     */
    public Instant getStartOfWeekSundayInstant(ZoneId timeZoneId) {
        // 1. 해당 타임존의 현재 ZonedDateTime 가져오기
        ZonedDateTime nowInZone = ZonedDateTime.now(timeZoneId);

        // 2. 해당 타임존의 현재 주 일요일의 시작 시각(자정) 찾기
        ZonedDateTime sundayStartInZone = nowInZone
            // 이전 또는 동일한 일요일로 이동
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) 
            // 시간 부분을 자정(00:00:00.000000000)으로 설정
            .truncatedTo(ChronoUnit.DAYS);

        // 3. Instant로 변환하여 반환
        return sundayStartInZone.toInstant();
    }

    // (이번주 전체 산책 리스트(구현))DateUtils.java - 아래 메서드 추가 
    public Instant getStartOfWeekInstant(ZoneId timeZoneId) {
        // 해당 타임존의 현재 시각
        ZonedDateTime nowInZone = ZonedDateTime.now(timeZoneId);

        // 이번 주 월요일 00:00:00 으로 이동
        ZonedDateTime mondayStartInZone = nowInZone
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS);

        return mondayStartInZone.toInstant();
    
    }
}