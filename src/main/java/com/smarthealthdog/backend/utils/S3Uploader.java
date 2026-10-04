package com.smarthealthdog.backend.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.SubmissionService;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@RequiredArgsConstructor
@Component
@Profile({"prod", "dev"})
public class S3Uploader implements ImageUploader {
    private final S3Client s3Client;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final SubmissionService submissionService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * S3 버킷에 파일 업로드
     * @param filePrefix 파일 접두사 (예: "profiles/")
     * @param file 업로드할 파일
     * @return 파일 URL
     * @throws IOException
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void uploadProfilePicture(
        UserProfilePictureUploadEvent event
    ) throws IOException {
        if (event.user() == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (event.originalFilename() == null || event.originalFilename().isEmpty()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (event.contentType() == null || event.contentType().isEmpty()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (event.fileBytes() == null || event.fileBytes().length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = putImageObject("profiles/", event.fileBytes());

        event.user().setProfilePic(key);
        userRepository.save(event.user());
    }

    /**
     * S3 버킷에 반려동물 이미지 업로드
     * @param pet 반려동물 엔티티
     * @param file 업로드할 파일
     * @return 업로드된 파일의 S3 키
     * @throws IOException 이미지 업로드 중 오류 발생 시
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void uploadPetImage(PetPictureUploadEvent event) throws IOException {
        if (event.fileBytes() == null || event.originalFilename() == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = putImageObject("pets/", event.fileBytes());

        event.pet().setProfileImage(key);
        petRepository.save(event.pet());
    }

    /**
     * S3 버킷에 AI 진단용 이미지 업로드
     * @param submissionId 서브미션 ID
     * @param fileBytes 파일 바이트 배열
     * @param originalFilename 파일 원본 이름
     * @param contentType 파일 콘텐츠 타입
     * @throws IOException 이미지 업로드 중 오류 발생 시
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void uploadSubmissionImage(
        SubmissionImageUploadEvent event
    ) throws IOException {
        Submission submission = event.submission();

        try {
            String key = storeSubmissionImage(event, "diagnoses/");
            submission.setPhotoUrl(key);
            submissionService.saveSubmission(submission);
        } catch (Exception e) {
            // TODO: 업로드 실패 시, Sentry나 로그 시스템에 알림 전송 기능 필요
            submissionService.failSubmission(submission, SubmissionFailureReasonEnum.SERVICE_ERROR);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 제출 이미지를 동기적으로 S3에 저장한다.
     * 비동기 리스너(uploadSubmissionImage)와 완전히 같은 구현을 공유한다.
     *
     * @param event 저장할 이미지 바이트를 담은 값 객체
     * @param prefix S3 키 접두사 (예: "health-certificates/")
     * @return 저장된 S3 object key
     * @throws InvalidRequestDataException 바이트가 비었거나 허용 이미지가 아닌 경우
     *         ({@link ErrorCode#INVALID_IMAGE})
     */
    @Override
    public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
        if (event == null || event.fileBytes() == null || event.fileBytes().length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("S3 키 접두사가 비어 있습니다.");
        }

        return putImageObject(prefix, event.fileBytes());
    }

    /**
     * 보상 삭제. 이 저장소에서 처음 들어가는 S3 삭제 경로다.
     *
     * @param key 삭제할 S3 object key. null·공백이면 아무것도 하지 않는다.
     */
    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
            );
        } catch (NoSuchKeyException e) {
            // 이미 없는 객체는 삭제 성공과 같게 취급한다.
        }
    }

    /**
     * 바이트 내용을 Tika로 판정해 S3에 안전하게 저장한다.
     *
     * 클라이언트가 보낸 contentType·파일명 확장자는 쓰지 않는다.
     * Content-Disposition 을 attachment 로 고정해, 설령 판정이 뚫려도
     * 브라우저가 인라인 렌더링하지 않게 한다.
     *
     * @param prefix S3 키 접두사 (예: "profiles/")
     * @param fileBytes 업로드할 바이트
     * @return 저장된 S3 object key
     * @throws InvalidRequestDataException 허용 이미지가 아닌 경우
     */
    private String putImageObject(String prefix, byte[] fileBytes) {
        String detectedMimeType;
        try {
            detectedMimeType = FileUtils.detectImageMimeType(new ByteArrayInputStream(fileBytes));
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (detectedMimeType == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = prefix + UUID.randomUUID() + FileUtils.extensionForMimeType(detectedMimeType);

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(detectedMimeType)
                .contentDisposition("attachment")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build(),
            RequestBody.fromBytes(fileBytes)
        );

        return key;
    }
}
