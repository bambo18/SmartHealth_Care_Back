package com.smarthealthdog.backend.utils;

import java.io.IOException;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;

public interface ImageUploader {

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
     * 제출 이미지를 동기적으로 저장하고 S3 object key 를 반환한다.
     *
     * 비동기 경로(uploadSubmissionImage)와 달리 호출 시점에 저장이 끝나므로,
     * "판별 실패 시 미저장" 보증이 필요한 동기 흐름에서 이 메서드를 쓴다.
     * 예외는 삼키지 않고 호출자에게 그대로 전파한다.
     *
     * @param event 저장할 이미지 바이트를 담은 값 객체 (이벤트로 발행하지 않는다)
     * @param prefix S3 키 접두사 (예: "health-certificates/")
     * @return 저장된 S3 object key
     */
    String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix);

    /**
     * S3 object 를 삭제한다. 보상 삭제용이다.
     * 존재하지 않는 키나 빈 키는 조용히 무시한다.
     *
     * @param key 삭제할 S3 object key
     */
    void delete(String key);
}