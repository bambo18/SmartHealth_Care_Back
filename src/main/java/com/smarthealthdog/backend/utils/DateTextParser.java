package com.smarthealthdog.backend.utils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 진단서에 적힌 날짜 원문을 LocalDate 로 파싱한다.
 *
 * OCR 이 구분자나 숫자를 틀릴 수 있어 파싱 실패가 등록을 막지 않는다.
 * 그래서 예외를 던지지 않고 빈 Optional 을 반환한다 —
 * 호출자는 원문을 그대로 저장하고 DATE 컬럼만 NULL 로 둔다.
 *
 * 추측하지 않는다: 두 자리 연도('20.10.28')는 1920 2020 2120 을 구분할 수 없고,
 * 일-월-연 순서('28.10.2020')는 국내 진단서 관행이 아니므로 둘 다 파싱하지 않는다.
 */
public final class DateTextParser {

    private DateTextParser() {
    }

    /**
     * 연(4자리) 월 일 순서를 점 하이픈 슬래시 또는 한글 단위로 구분한 표기를 받는다.
     * 예: 2020.10.28 / 2020-10-28 / 2020/10/28 / 2020년 10월 28일 / 2020. 10. 28.
     */
    private static final Pattern YEAR_FIRST = Pattern.compile(
        "^(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*[.일]?$"
    );

    /**
     * 날짜 원문을 파싱한다.
     *
     * @param text 진단서에서 추출한 날짜 원문 (null 허용)
     * @return 파싱된 날짜. 형식이 맞지 않거나 존재하지 않는 날짜면 빈 Optional
     */
    public static Optional<LocalDate> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = YEAR_FIRST.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));

            // LocalDate.of 가 2020-02-30 같은 존재하지 않는 날짜를 거부한다.
            return Optional.of(LocalDate.of(year, month, day));
        // DateTimeException 이 DateTimeParseException 을 포함한다 (하위 클래스).
        } catch (NumberFormatException | DateTimeException e) {
            return Optional.empty();
        }
    }
}
