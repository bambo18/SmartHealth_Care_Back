package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class DateUtilsTest {
    // Test methods would go here
    @Autowired
    private DateUtils dateUtils;

    @Test
    void getStartOfWeekSundayInstant_shouldReturnCorrectInstant() {
        // Implement test logic here
        String timeZoneId = "Asia/Seoul";
        ZoneId zoneId = ZoneId.of(timeZoneId);
        Instant expected = ZonedDateTime.now(zoneId)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant();

        Instant actual = dateUtils.getStartOfWeekSundayInstant(zoneId);
        assertEquals(expected, actual);
    } 
}
