package com.smarthealthdog.backend;

import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.utils.ImgUtils;

@Configuration
@Profile("test")
public class TestImageConfig {

    @Bean
    public ImgUtils imgUtils() {
        return new TestImgUtils();
    }

    /**
     * test 프로파일에는 S3Uploader(prod/dev)도 LocalImageUploader(local)도 없다.
     * 동기 업로드 경로가 ImageUploader 를 직접 주입받으므로, 아무것도 저장하지 않는
     * 구현체를 하나 둔다. 실제 저장 동작을 검증하는 테스트는 @MockitoBean 으로 대체한다.
     */
    @Bean
    public ImageUploader imageUploader() {
        return new TestImageUploader();
    }

    static class TestImgUtils implements ImgUtils {
        private String localStorageUrlPrefix = "http://localhost:8080/uploads/";
        private String aiModelServiceUrlPrefix = "http://localhost:8081/uploads/";

        @Override
        public String getImgUrl(String key) {
            return buildUrl(localStorageUrlPrefix, key);
        }

        @Override
        public String getImgUrlForAIWorker(String key) {
            return buildUrl(aiModelServiceUrlPrefix, key);
        }

        @Override
        public String getSecureImgUrl(String key, Duration ttl) {
            String url = buildUrl(aiModelServiceUrlPrefix, key);
            if (url == null) {
                return null;
            }

            return url + "?expires=" + ttl.getSeconds();
        }

        private String buildUrl(String prefix, String key) {
            if (key == null || key.isBlank()) {
                return null;
            }

            return prefix + key;
        }
    }

    static class TestImageUploader implements ImageUploader {

        @Override
        public void uploadProfilePicture(UserProfilePictureUploadEvent event) {
            // 테스트 환경에서는 실제 저장을 하지 않는다.
        }

        @Override
        public void uploadPetImage(PetPictureUploadEvent event) {
            // 테스트 환경에서는 실제 저장을 하지 않는다.
        }

        @Override
        public void uploadSubmissionImage(SubmissionImageUploadEvent event) {
            // 테스트 환경에서는 실제 저장을 하지 않는다.
        }

        @Override
        public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
            return prefix + UUID.randomUUID() + ".jpg";
        }

        @Override
        public void delete(String key) {
            // 테스트 환경에서는 실제 삭제를 하지 않는다.
        }
    }
}
