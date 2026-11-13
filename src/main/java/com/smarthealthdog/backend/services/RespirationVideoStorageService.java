package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class RespirationVideoStorageService {

    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 영상 저장 경로 생성
    public String generateVideoKey(Long petId, UUID videoId) {
        return "respiration/" + petId + "/" + videoId + ".mp4";
    }

    // 프론트엔드 업로드용 Presigned PUT URL
    public String createUploadUrl(String key) {

        PutObjectRequest putObjectRequest =
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(VIDEO_CONTENT_TYPE)
                .build();

        PutObjectPresignRequest presignRequest =
            PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest)
                .url()
                .toString();
    }

    // S3 업로드 완료 여부 및 용량 확인
    public long validateUploadedVideo(String key) {

        HeadObjectResponse response = s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );

        long size = response.contentLength();

        if (size <= 0 || size > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException(
                "Video size must be between 1 byte and 200 MiB"
            );
        }

        if (!VIDEO_CONTENT_TYPE.equalsIgnoreCase(response.contentType())) {
            throw new IllegalArgumentException(
                "Unsupported video content type"
            );
        }

        return size;
    }

    // AI Worker 다운로드용 Presigned GET URL
    public String createDownloadUrl(String key) {

        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest =
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
}