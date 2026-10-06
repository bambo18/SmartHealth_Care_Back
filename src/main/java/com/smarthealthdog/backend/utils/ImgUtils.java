package com.smarthealthdog.backend.utils;

import java.time.Duration;

public interface ImgUtils {
    String getImgUrl(String key);

    String getImgUrlForAIWorker(String key);

    /**
     * 지정한 기간 동안만 유효한 서명 URL 을 반환한다.
     *
     * 진단서처럼 개인정보가 찍힌 이미지는 {@link #getImgUrl(String)} 대신 반드시 이 메서드를 쓴다 —
     * prod 에서 CloudFront 도메인이 설정돼 있으면 getImgUrl 은 무서명·무만료 URL 을 돌려준다.
     *
     * @param key S3 object key
     * @param ttl 서명 유효 기간
     * @return 서명 URL. key 가 비어 있으면 null
     */
    String getSecureImgUrl(String key, Duration ttl);
}
