package com.smarthealthdog.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdImgUtils implements ImgUtils {
    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudFrontUrl;

    /**
     * AWS CloudFront URL 생성
     * @param key 이미지 키
     * @return 이미지 URL
     */
    @Override
    public String getImgUrl(String key) {
        return cloudFrontUrl + "/" + key;
    }

    @Override
    public String getImgUrlForAIWorker(String key) {
        return cloudFrontUrl + "/" + key;
    }
}
