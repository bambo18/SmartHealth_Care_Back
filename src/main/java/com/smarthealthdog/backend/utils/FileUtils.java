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
     * @throws IOException 스트림 읽기 실패 시
     * @throws IllegalArgumentException ioStream이 null인 경우 발생
     */
    public static boolean isMIMEImage(InputStream ioStream) throws IOException, IllegalArgumentException {
        return detectImageMimeType(ioStream) != null;
    }

    /**
     * Tika가 바이트 내용을 보고 판정한 MIME 타입을 반환한다.
     *
     * 클라이언트가 보낸 Content-Type 헤더는 신뢰하지 않는다.
     * 이 반환값이 S3 저장 시 contentType 과 확장자의 유일한 근거다.
     *
     * @param ioStream 파일의 InputStream
     * @return 허용 이미지면 판정된 MIME 타입, 허용 목록 밖이면 null
     * @throws IOException 스트림 읽기 실패 시
     * @throws IllegalArgumentException ioStream이 null인 경우
     */
    public static String detectImageMimeType(InputStream ioStream) throws IOException, IllegalArgumentException {
        if (ioStream == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        String mimeType = tika.detect(ioStream);
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equalsIgnoreCase(mimeType)) {
                return mimeType;
            }
        }

        return null;
    }

    /**
     * 판정된 MIME 타입에 대응하는 파일 확장자를 반환한다.
     *
     * 클라이언트가 보낸 파일명의 확장자를 쓰지 않는 이유는,
     * 파일명이 공격자 제어 입력이기 때문이다.
     *
     * @param mimeType 허용 이미지 MIME 타입
     * @return 점을 포함한 확장자 (예: ".jpg")
     * @throws IllegalArgumentException 허용 목록 밖의 MIME 타입인 경우
     */
    public static String extensionForMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("MIME type is null");
        }

        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("허용되지 않은 MIME 타입: " + mimeType);
        };
    }
}
