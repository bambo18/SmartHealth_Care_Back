package com.smarthealthdog.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;
import com.smarthealthdog.backend.utils.ImageUploader;

/**
 * test 프로파일용 {@link ImageUploader} 빈.
 *
 * S3Uploader 는 {@code @Profile({"prod","dev"})}, LocalImageUploader 는
 * {@code @Profile("local")} 이라 test 프로파일에는 구현체가 하나도 없다.
 * 그래서 ImageUploader 를 주입받는 빈(HealthCertificateService)이 생기면
 * 모든 {@code @SpringBootTest} 컨텍스트가 뜨지 않는다 — TestImgUtils 와 같은 처방이다.
 *
 * 실제 S3 를 타지 않고 키만 만들어 돌려준다.
 */
@Configuration
@Profile("test")
public class TestUploadConfig {

    @Bean
    public ImageUploader imageUploader() {
        return new InMemoryImageUploader();
    }

    /** 저장된 키와 삭제된 키를 기억해 통합 테스트가 확인할 수 있게 한다. */
    public static class InMemoryImageUploader implements ImageUploader {

        private final List<String> storedKeys = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();

        @Override
        public void uploadProfilePicture(UserProfilePictureUploadEvent event) throws IOException {
            storedKeys.add("profiles/" + UUID.randomUUID() + ".jpg");
        }

        @Override
        public void uploadPetImage(PetPictureUploadEvent event) throws IOException {
            storedKeys.add("pets/" + UUID.randomUUID() + ".jpg");
        }

        @Override
        public void uploadSubmissionImage(SubmissionImageUploadEvent event) throws IOException {
            storedKeys.add("diagnoses/" + UUID.randomUUID() + ".jpg");
        }

        @Override
        public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
            if (event == null || event.fileBytes() == null || event.fileBytes().length == 0) {
                throw new IllegalArgumentException("이미지 바이트가 비어 있습니다.");
            }

            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("S3 키 접두사가 비어 있습니다.");
            }

            String key = prefix + UUID.randomUUID() + ".jpg";
            storedKeys.add(key);

            return key;
        }

        @Override
        public void delete(String key) {
            if (key == null || key.isBlank()) {
                return;
            }

            deletedKeys.add(key);
        }

        public List<String> getStoredKeys() {
            return storedKeys;
        }

        public List<String> getDeletedKeys() {
            return deletedKeys;
        }
    }
}
