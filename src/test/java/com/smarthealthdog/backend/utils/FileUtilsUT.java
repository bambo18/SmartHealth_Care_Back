package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class FileUtilsUT {

    private static final byte[] PNG_MAGIC =
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    void detectImageMimeType_PNG_바이트를_image_png으로_판정한다() throws IOException {
        assertEquals(
            "image/png",
            FileUtils.detectImageMimeType(new ByteArrayInputStream(PNG_MAGIC))
        );
    }

    @Test
    void detectImageMimeType_JPEG_바이트를_image_jpeg으로_판정한다() throws IOException {
        assertEquals(
            "image/jpeg",
            FileUtils.detectImageMimeType(new ByteArrayInputStream(JPEG_MAGIC))
        );
    }

    @Test
    void detectImageMimeType_이미지가_아니면_null을_반환한다() throws IOException {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertNull(FileUtils.detectImageMimeType(new ByteArrayInputStream(html)));
    }

    @Test
    void extensionForMimeType_판정값에서_확장자를_만든다() {
        assertEquals(".jpg", FileUtils.extensionForMimeType("image/jpeg"));
        assertEquals(".png", FileUtils.extensionForMimeType("image/png"));
    }

    @Test
    void extensionForMimeType_허용목록_밖이면_IllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FileUtils.extensionForMimeType("text/html")
        );
    }
}
