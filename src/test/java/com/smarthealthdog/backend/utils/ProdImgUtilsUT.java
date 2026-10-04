package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * ProdImgUtils 의 서명 URL 분기를 고정한다.
 *
 * 이 테스트의 핵심은 getSecureImgUrl 이 CloudFront 가 설정돼 있어도
 * 무서명 CloudFront URL 을 절대 반환하지 않는다는 것이다 (SPEC 9.2).
 * 진단서·구강 이미지에는 견주 성명 주소가 찍혀 있어, 이 분기가 한 번
 * 뚫리면 만료 없는 공개 링크가 된다.
 */
@ExtendWith(MockitoExtension.class)
public class ProdImgUtilsUT {

    private static final String CLOUDFRONT = "https://cdn.example.com";
    private static final String PRESIGNED = "https://bucket.s3.example.com/key?X-Amz-Signature=abc";

    private ProdImgUtils prodImgUtils;

    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedGetObjectRequest presignedRequest;

    @Captor private ArgumentCaptor<GetObjectPresignRequest> presignCaptor;

    @BeforeEach
    void setUp() {
        prodImgUtils = new ProdImgUtils(s3Presigner);
        ReflectionTestUtils.setField(prodImgUtils, "bucket", "test-bucket");
        ReflectionTestUtils.setField(prodImgUtils, "cloudFrontUrl", CLOUDFRONT);
    }

    private void givenPresignerReturnsUrl() throws Exception {
        when(presignedRequest.url()).thenReturn(new URI(PRESIGNED).toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedRequest);
    }

    @Test
    void getSecureImgUrl_CloudFront가_설정돼_있어도_서명_URL을_반환한다() throws Exception {
        givenPresignerReturnsUrl();

        String url = prodImgUtils.getSecureImgUrl("health-certificates/abc.jpg", Duration.ofMinutes(5));

        assertEquals(PRESIGNED, url);
        // CloudFront 도메인이 결과에 섞여 들어가면 무서명 공개 링크가 된다.
        org.junit.jupiter.api.Assertions.assertFalse(
            url.startsWith(CLOUDFRONT),
            "getSecureImgUrl 은 CloudFront 분기를 타면 안 된다: " + url
        );
    }

    @Test
    void getSecureImgUrl_전달한_TTL을_서명_기간으로_쓴다() throws Exception {
        givenPresignerReturnsUrl();

        prodImgUtils.getSecureImgUrl("health-certificates/abc.jpg", Duration.ofMinutes(5));

        verify(s3Presigner).presignGetObject(presignCaptor.capture());
        assertEquals(Duration.ofMinutes(5), presignCaptor.getValue().signatureDuration());
    }

    @Test
    void getSecureImgUrl_key가_비면_null이고_presigner를_부르지_않는다() {
        assertNull(prodImgUtils.getSecureImgUrl(null, Duration.ofMinutes(5)));
        assertNull(prodImgUtils.getSecureImgUrl("   ", Duration.ofMinutes(5)));

        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void getSecureImgUrl_TTL이_유효하지_않으면_IllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> prodImgUtils.getSecureImgUrl("k.jpg", null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> prodImgUtils.getSecureImgUrl("k.jpg", Duration.ZERO)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> prodImgUtils.getSecureImgUrl("k.jpg", Duration.ofMinutes(-1))
        );
    }

    @Test
    void getImgUrl_기존_CloudFront_분기는_그대로_유지된다() {
        // 눈·소변 진단이 이 동작에 의존한다. 바뀌면 안 된다.
        String url = prodImgUtils.getImgUrl("diagnoses/abc.jpg");

        assertEquals(CLOUDFRONT + "/diagnoses/abc.jpg", url);
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void getImgUrlForAIWorker_CloudFront가_있어도_서명_URL을_반환한다() throws Exception {
        givenPresignerReturnsUrl();

        URL expected = new URI(PRESIGNED).toURL();
        assertEquals(expected.toString(), prodImgUtils.getImgUrlForAIWorker("diagnoses/abc.jpg"));
    }
}
