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
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Slf4j
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
     * S3 버킷에 프로필 사진 업로드
     * @param event 업로드할 이미지 정보
     * @throws IOException 이미지 업로드 중 오류 발생 시
     * @throws InvalidRequestDataException 이미지가 비었거나 허용 형식이 아닌 경우
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

        String key = putImage(event.fileBytes(), "profiles/");

        event.user().setProfilePic(key);
        userRepository.save(event.user());
    }

    /**
     * S3 버킷에 반려동물 이미지 업로드
     * @param event 업로드할 이미지 정보
     * @throws IOException 이미지 업로드 중 오류 발생 시
     * @throws InvalidRequestDataException 이미지가 비었거나 허용 형식이 아닌 경우
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void uploadPetImage(PetPictureUploadEvent event) throws IOException {
        if (event.pet() == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = putImage(event.fileBytes(), "pets/");

        event.pet().setProfileImage(key);
        petRepository.save(event.pet());
    }

    /**
     * S3 버킷에 AI 진단용 이미지 업로드 (비동기 경로)
     *
     * 커밋 이후에 동작하므로 인식 실패 시 미저장을 보장할 수 없다. 저장을 막아야 하는 동기 경로는
     * {@link #storeSubmissionImage(SubmissionImageUploadEvent, String)} 를 직접 호출한다.
     *
     * @param event 업로드할 이미지 정보
     * @throws IOException 이미지 업로드 중 오류 발생 시
     */
    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void uploadSubmissionImage(
        SubmissionImageUploadEvent event
    ) throws IOException {
        Submission submission = event.submission();

        String key;
        try {
            key = storeSubmissionImage(event, "diagnoses/");
        } catch (InvalidRequestDataException e) {
            // 이미지 자체가 유효하지 않은 경우는 기존과 동일하게 그대로 전파한다.
            throw e;
        } catch (Exception e) {
            // TODO: 업로드 실패 시, Sentry나 로그 시스템에 알림 전송 기능 필요
            submissionService.failSubmission(submission, SubmissionFailureReasonEnum.SERVICE_ERROR);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        submission.setPhotoUrl(key);
        submissionService.saveSubmission(submission);
    }

    /**
     * 제출 이미지를 동기로 저장하고 S3 object key 를 반환한다.
     *
     * 비동기 리스너도 같은 구현을 호출하므로 눈·소변 진단의 저장 동작과 완전히 동일하다.
     *
     * @param event 업로드할 이미지 정보
     * @param prefix S3 key 접두사
     * @return 저장된 S3 object key
     * @throws IOException 이미지 업로드 중 오류 발생 시
     * @throws InvalidRequestDataException 이미지가 비었거나 허용 형식이 아닌 경우
     */
    @Override
    public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) throws IOException {
        if (event == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        return putImage(event.fileBytes(), prefix);
    }

    /**
     * 보상 삭제. 존재하지 않는 키는 S3 가 성공으로 처리하므로 별도 분기가 없다.
     * @param key 삭제할 S3 object key
     */
    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );
    }

    /**
     * 이미지 바이트를 S3 에 올리고 key 를 반환한다.
     *
     * contentType 과 확장자는 모두 Tika 가 바이트를 보고 판정한 값에서 파생한다. 클라이언트가 보낸
     * 멀티파트 파트 헤더를 그대로 쓰면, 유효한 JPEG 안에 스크립트를 심은 폴리글롯 파일을
     * text/html 로 저장시켜 서명 URL 로도 막히지 않는 저장형 XSS 가 성립한다.
     * contentDisposition("attachment") 는 그 2차 방어다.
     *
     * @param fileBytes 이미지 바이트
     * @param prefix S3 key 접두사
     * @return 저장된 S3 object key
     * @throws IOException 바이트를 읽는 중 오류 발생 시
     * @throws InvalidRequestDataException 이미지가 비었거나 허용 형식이 아닌 경우
     */
    private String putImage(byte[] fileBytes, String prefix) throws IOException {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String detectedMimeType;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            detectedMimeType = FileUtils.detectImageMimeType(inputStream);
        }

        String ext = FileUtils.extensionForMimeType(detectedMimeType);
        if (ext == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = prefix + UUID.randomUUID() + ext;

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
