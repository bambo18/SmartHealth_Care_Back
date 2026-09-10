package com.smarthealthdog.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.smarthealthdog.backend.utils.ImgUtils;

@Configuration
@Profile("test")
public class TestImageConfig {

    @Bean
    public ImgUtils imgUtils() {
        return new TestImgUtils();
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

        private String buildUrl(String prefix, String key) {
            if (key == null || key.isBlank()) {
                return null;
            }

            return prefix + key;
        }
    }
}
