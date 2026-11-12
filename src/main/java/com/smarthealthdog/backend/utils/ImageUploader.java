package com.smarthealthdog.backend.utils;

import java.io.IOException;

import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;

public interface ImageUploader {

    /** 눈·소변 진단 이미지 접두사. */
    String DIAGNOSIS_KEY_PREFIX = "diagnoses/";

    /**
     * 진단서 이미지 접두사.
     *
     * 접근 정책과 수명 주기 규칙을 따로 걸 수 있도록 진단 이미지와 섞지 않는다 —
     * 진단서에는 보호자 식별 정보가 담긴 원본이 들어간다.
     */
    String HEALTH_CERTIFICATE_KEY_PREFIX = "health-certificates/";

    /**
     * 제출 유형에 맞는 S3 key 접두사를 고른다.
     *
     * 비동기 업로드 리스너는 이벤트만 받으므로 접두사를 알 방법이 제출 유형뿐이다.
     */
    static String keyPrefixFor(SubmissionTypeEnum type) {
        return type == SubmissionTypeEnum.HEALTH_CERTIFICATE
            ? HEALTH_CERTIFICATE_KEY_PREFIX
            : DIAGNOSIS_KEY_PREFIX;
    }

    void uploadProfilePicture(
        UserProfilePictureUploadEvent event
    ) throws IOException;

    void uploadPetImage(
        PetPictureUploadEvent event
    ) throws IOException;

    void uploadSubmissionImage(
        SubmissionImageUploadEvent event
    ) throws IOException;

    /**
     * 제출 이미지를 동기로 저장하고 S3 object key 를 반환한다.
     *
     * 비동기 이벤트 리스너와 달리 호출자의 스레드에서 끝나므로, 저장에 성공했는지를
     * 호출자가 즉시 알 수 있다. 예외는 삼키지 않고 호출자에게 그대로 전파한다.
     *
     * @param event 업로드할 이미지 (이벤트로 발행하지 않고 값 객체로 전달한다)
     * @param prefix S3 key 접두사 (예: "health-certificates/")
     * @return 저장된 S3 object key
     * @throws IOException 저장 중 오류 발생 시
     */
    String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) throws IOException;

    /**
     * 보상 삭제용. 존재하지 않는 키는 무시한다.
     * @param key 삭제할 S3 object key
     */
    void delete(String key);
}
