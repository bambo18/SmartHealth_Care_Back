package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class DateTextParserUT {

    @ParameterizedTest
    @CsvSource({
        "2020.10.28,      2020-10-28",
        "2020-10-28,      2020-10-28",
        "2020/10/28,      2020-10-28",
        "2020년 10월 28일, 2020-10-28",
        "2020년10월28일,   2020-10-28",
        "2020.1.5,        2020-01-05",
        "2020. 10. 28.,   2020-10-28",
        "  2020.10.28  ,  2020-10-28"
    })
    void 흔한_한국_진단서_표기를_파싱한다(String input, String expected) {
        Optional<LocalDate> parsed = DateTextParser.parse(input);

        assertTrue(parsed.isPresent(), "파싱되어야 한다: " + input);
        assertEquals(LocalDate.parse(expected), parsed.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "알 수 없음",
        "2020",            // 연도만 — 월 일이 없으면 날짜가 아니다
        "2020.13.01",      // 13월
        "2020.02.30",      // 2월 30일
        "20201028",        // 구분자 없는 8자리는 지원하지 않는다
        "28.10.2020"       // 일 월 연 순서는 지원하지 않는다 (국내 진단서 관행 아님)
    })
    void 파싱할_수_없으면_빈_Optional을_반환하고_예외를_던지지_않는다(String input) {
        // 파싱 실패가 등록을 막지 않는다는 정책을 타입으로 표현한다.
        assertTrue(DateTextParser.parse(input).isEmpty(), "빈 Optional 이어야 한다: " + input);
    }

    @Test
    void 두자리_연도는_지원하지_않는다() {
        // '20.10.28' 을 2020년으로 추측하면 1920년 2120년과 구분할 수 없다.
        // 추측하지 않고 원문만 남겨 수정 화면에서 보정하게 한다.
        assertTrue(DateTextParser.parse("20.10.28").isEmpty());
    }
}
