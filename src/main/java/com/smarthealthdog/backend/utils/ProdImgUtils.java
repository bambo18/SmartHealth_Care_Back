package com.smarthealthdog.backend.utils;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@Profile("prod")
public class ProdImgUtils implements ImgUtils {

    private final S3Presigner s3Presigner;

    public ProdImgUtils(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @Value("${cloud.aws.cloudfront.domain:}")
    private String cloudFrontUrl;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * CloudFront URL이 있으면 CloudFront URL을 반환하고,
     * CloudFront URL이 없으면 S3 presigned URL을 반환한다.
     *
     * private bucket 또는 OVH S3 환경에서는
     * 단순 S3 URL 접근 시 403이 날 수 있으므로 presigned URL을 사용한다.
     */
    @Override
    public String getImgUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        if (hasCloudFrontUrl()) {
            return buildCloudFrontUrl(key);
        }

        return createPresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * AI Worker는 원본 S3 이미지 접근이 필요하므로 presigned URL을 반환한다.
     */
    @Override
    public String getImgUrlForAIWorker(String key) {
        return createPresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * 지정한 기간 동안만 유효한 서명 URL을 반환한다.
     *
     * CloudFront 분기를 절대 타지 않는다 — 개인정보가 찍힌 이미지를
     * 무서명·무만료 URL로 공개하는 것을 원천 차단한다 (SPEC 9.2).
     *
     * @param key S3 object key
     * @param ttl 서명 유효 기간
     * @return 서명 URL. key 가 비어 있으면 null
     * @throws IllegalArgumentException ttl 이 null 이거나 0 이하인 경우
     */
    @Override
    public String getSecureImgUrl(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("서명 URL 유효 기간이 유효하지 않습니다.");
        }

        return createPresignedUrl(key, ttl);
    }

    /**
     * CloudFront URL 설정 여부 확인.
     */
    private boolean hasCloudFrontUrl() {
        return cloudFrontUrl != null
                && !cloudFrontUrl.isBlank()
                && !"null".equalsIgnoreCase(cloudFrontUrl);
    }

    /**
     * CloudFront URL 생성.
     */
    private String buildCloudFrontUrl(String key) {
        String normalizedCloudFrontUrl = cloudFrontUrl.endsWith("/")
                ? cloudFrontUrl.substring(0, cloudFrontUrl.length() - 1)
                : cloudFrontUrl;

        return normalizedCloudFrontUrl + "/" + key;
    }

    /**
     * S3 key를 주어진 기간 동안 접근 가능한 presigned URL로 변환한다.
     */
    private String createPresignedUrl(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}