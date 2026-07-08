package com.smarthealthdog.backend.utils;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@Profile("dev")
public class DevImgUtils implements ImgUtils {

    private final S3Presigner s3Presigner;

    public DevImgUtils(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * dev 환경에서도 S3Uploader가 이미지를 OVH S3에 저장하므로,
     * 프론트에는 /uploads/... 로컬 주소가 아니라
     * S3 접근 가능한 presigned URL을 내려준다.
     */
    @Override
    public String getImgUrl(String key) {
        return createPresignedUrl(key);
    }

    /**
     * AI Worker용 이미지 URL도 presigned URL을 반환한다.
     */
    @Override
    public String getImgUrlForAIWorker(String key) {
        return createPresignedUrl(key);
    }

    /**
     * S3 key를 1시간 동안 접근 가능한 presigned URL로 변환한다.
     */
    private String createPresignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}