package com.smarthealthdog.backend.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JWTUtils {
    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.access-token.expiration.minutes}")
    private int jwtAccessExpirationInMinutes;
    @Value("${jwt.refresh-token.expiration.days}")
    private int jwtRefreshExpirationInDays;
    private SecretKey key;

    // Initializes the key after the class is instantiated and the jwtSecret is injected, 
    // preventing the repeated creation of the key and enhancing performance
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JWT Access Token 생성
     * @param id
     * @param issuedAt
     * @return 생성된 JWT 엑세스 토큰
     * @throws IllegalArgumentException id가 null이거나 비어있을 경우 발생
     */
    public String generateAccessToken(String id, Date issuedAt) throws IllegalArgumentException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID 값은 null이거나 비어있을 수 없습니다");
        }

        if (issuedAt == null) {
            issuedAt = new Date();
        }

        Date expiration = Date.from(issuedAt.toInstant().plusSeconds(jwtAccessExpirationInMinutes * 60));
        return Jwts.builder()
                   .subject(id)
                   .issuedAt(issuedAt)
                   .expiration(expiration)
                   .signWith(key)
                   .compact();
    }

    /**
     * JWT Refresh Token 생성
     * @param id 사용자 ID
     * @param uuid 고유 식별자
     * @param issuedAt 발급 시간
     * @return 생성된 JWT 리프레시 토큰
     * @throws IllegalArgumentException id가 null이거나 비어있거나, uuid가 null인 경우 발생
     */
    public String generateRefreshToken(String id, UUID uuid, Date issuedAt) throws IllegalArgumentException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID 값은 null이거나 비어있을 수 없습니다");
        }

        if (uuid == null) {
            throw new IllegalArgumentException("UUID 값은 null일 수 없습니다");
        }

        if (issuedAt == null) {
            issuedAt = new Date();
        }

        Date expiration = Date.from(issuedAt.toInstant().plusSeconds(jwtRefreshExpirationInDays * 24 * 60 * 60));
        return Jwts.builder()
                   .subject(id)
                   .id(uuid.toString())
                   .issuedAt(issuedAt)
                   .expiration(expiration)
                   .signWith(key)
                   .compact();
    }

    /**
     * 토큰에서 subject(유저 ID) 추출
     * @param token
     * @return 유저 ID
     * @throws JwtException 토큰이 파싱되지 않거나 유효하지 않을 경우 발생
     * @throws IllegalArgumentException token이 null이거나 비어있을 경우 발생
     * @throws UnsupportedJwtException 지원하지 않는 토큰일 경우 발생
     */
    public String getUserIdFromToken(String token) throws JwtException, IllegalArgumentException, UnsupportedJwtException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("토큰은 null이거나 비어있을 수 없습니다");
        }

        return Jwts.parser()
                   .verifyWith(key)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload()
                   .getSubject();
    }

    /**
     * 토큰에서 모든 클레임 추출
     * @param token
     * @return 모든 클레임
     * @throws JwtException 토큰이 파싱되지 않거나 유효하지 않을 경우 발생
     * @throws IllegalArgumentException token이 null이거나 비어있을 경우 발생
     * @throws UnsupportedJwtException 지원하지 않는 토큰일 경우 발생
     */
    public Jws<Claims> getAllClaimsFromToken(String token) throws JwtException, IllegalArgumentException, UnsupportedJwtException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("토큰은 null이거나 비어있을 수 없습니다");
        }

        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    /**
     * 토큰 유효성 검사
     * @param token 검사할 토큰
     * @throws JwtException 토큰이 파싱되지 않거나 유효하지 않을 경우 발생
     * @throws UnsupportedJwtException 지원하지 않는 토큰일 경우 발생
     * @throws IllegalArgumentException token이 null이거나 비어있을 경우 발생
     */
    public void validateJwtToken(String token) throws JwtException, UnsupportedJwtException, IllegalArgumentException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("토큰은 null이거나 비어있을 수 없습니다");
        }

        Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
