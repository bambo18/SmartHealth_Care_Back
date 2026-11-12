package com.smarthealthdog.backend.utils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.Tika;

public class FileUtils {
    private static final Tika tika = new Tika();

    private static final String[] ALLOWED_IMAGE_TYPES = {
        "image/jpeg",
        "image/png",
        "image/jpg"
    };

    /**
     * MIME 타입을 기반으로 파일이 이미지인지 검증
     * @param ioStream 파일의 InputStream
     * @return 이미지 파일이면 true, 아니면 false
     * @throws IOException
     * @throws IllegalArgumentException ioStream이 null인 경우 발생
     */
    public static boolean isMIMEImage(InputStream ioStream) throws IOException, IllegalArgumentException {
        return detectImageMimeType(ioStream) != null;
    }

    /**
     * 허용 이미지면 Tika 가 바이트를 보고 판정한 MIME 을, 아니면 null 을 반환한다.
     *
     * 클라이언트가 보낸 Content-Type 헤더는 신뢰할 수 없다. 저장 시 지정할 contentType 과
     * 확장자는 모두 이 판정값에서 파생해야 폴리글롯 파일을 이용한 저장형 XSS 를 막을 수 있다.
     *
     * @param ioStream 파일의 InputStream
     * @return 허용 이미지의 MIME 문자열, 허용되지 않으면 null
     * @throws IOException 스트림을 읽는 중 오류 발생 시
     * @throws IllegalArgumentException ioStream이 null인 경우 발생
     */
    public static String detectImageMimeType(InputStream ioStream) throws IOException, IllegalArgumentException {
        if (ioStream == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        String mimeType = tika.detect(ioStream);
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equalsIgnoreCase(mimeType)) {
                return mimeType.toLowerCase();
            }
        }

        return null;
    }

    /**
     * Tika 판정 MIME 에 대응하는 파일 확장자를 반환한다.
     *
     * 클라이언트 파일명의 확장자는 신뢰하지 않는다.
     *
     * @param mimeType Tika 가 판정한 MIME 문자열
     * @return 확장자 (점 포함). 알 수 없는 MIME 이면 null
     */
    public static String extensionForMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }

        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            default -> null;
        };
    }
}
