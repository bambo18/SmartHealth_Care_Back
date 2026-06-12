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

    @Value("${local-storage.url-prefix}")
    private String localStorageUrlPrefix;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public String getImgUrl(String key) {
        return localStorageUrlPrefix + "/uploads/" + key;
    }

    @Override
    public String getImgUrlForAIWorker(String key) {
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
