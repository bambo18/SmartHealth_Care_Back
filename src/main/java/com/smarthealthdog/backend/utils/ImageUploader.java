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
