package com.smarthealthdog.backend.utils;

import java.time.Duration;

public interface ImgUtils {
    String getImgUrl(String key);
    String getImgUrlForAIWorker(String key);

    /**
     * 지정한 기간 동안만 유효한 서명 URL을 반환한다.
     *
     * getImgUrl 과 달리 CloudFront 무서명 URL 분기를 절대 타지 않는다.
     * 개인정보가 찍힌 이미지(진단서·구강 사진)는 이 메서드만 사용한다.
     *
     * @param key S3 object key
     * @param ttl 서명 유효 기간
     * @return 서명 URL. key 가 비어 있으면 null
     * @throws IllegalArgumentException ttl 이 null 이거나 0 이하인 경우
     */
    String getSecureImgUrl(String key, Duration ttl);
}
