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

        @Override
        public String getSecureImgUrl(String key, java.time.Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("서명 URL 유효 기간이 유효하지 않습니다.");
            }

            String url = buildUrl(localStorageUrlPrefix, key);
            if (url == null) {
                return null;
            }

            // 테스트에서 TTL 이 실제로 전달되는지 확인할 수 있게 쿼리로 노출한다.
            return url + "?expiresIn=" + ttl.toSeconds();
        }

        private String buildUrl(String prefix, String key) {
            if (key == null || key.isBlank()) {
                return null;
            }

            return prefix + key;
        }
    }
}
