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
        if (ioStream == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        String mimeType = tika.detect(ioStream);
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }

        return false;
    }
}
