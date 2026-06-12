package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;


@SpringBootTest
@ActiveProfiles("test")
public class JWTUtilsTest {
    @Autowired
    private JWTUtils jwtUtils;

    private SecretKey key;

    @BeforeEach
    void setup() {
        key = Jwts.SIG.HS256.key().build();
        ReflectionTestUtils.setField(
            jwtUtils, 
            "key",
            Keys.hmacShaKeyFor(key.getEncoded())
        );
    }

    @Test
    void generateAccessToken_ShouldThrowException_WhenIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.generateAccessToken(null, null);
        });
    }

    @Test
    void generateAccessToken_ShouldThrowException_WhenIdIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.generateAccessToken("", null);
        });
    }

    @Test
    void generateAccessToken_ShouldReturnToken_WhenIdIsValid() {
        String token = jwtUtils.generateAccessToken("valid-id", null);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void generateAccessToken_ShouldReturnToken_WhenIssuedAtIsNull() {
        String token = jwtUtils.generateAccessToken("valid-id", null);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void generateAccessToken_ShouldReturnToken_whenIssuedAtIsValid() {
        String token = jwtUtils.generateAccessToken("valid-id", new Date());
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void generateRefreshToken_ShouldThrowException_WhenIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.generateRefreshToken(null, java.util.UUID.randomUUID(), null);
        });
    }

    @Test
    void generateRefreshToken_ShouldThrowException_WhenIdIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.generateRefreshToken("", java.util.UUID.randomUUID(), null);
        });
    }

    @Test
    void generateRefreshToken_ShouldThrowException_WhenUUIDIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.generateRefreshToken("valid-id", null, null);
        });
    }

    @Test
    void generateRefreshToken_ShouldReturnToken_WhenIdAndUUIDAreValid() {
        String token = jwtUtils.generateRefreshToken("valid-id", java.util.UUID.randomUUID(), null);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void generateRefreshToken_ShouldReturnToken_WhenIssuedAtIsNull() {
        String token = jwtUtils.generateRefreshToken("valid-id", java.util.UUID.randomUUID(), null);
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void generateRefreshToken_ShouldReturnToken_whenIssuedAtIsValid() {
        String token = jwtUtils.generateRefreshToken("valid-id", java.util.UUID.randomUUID(), new Date());
        assertTrue(token != null && !token.isEmpty());
    }

    @Test
    void getUserIdFromToken_ShouldThrowException_WhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.getUserIdFromToken(null);
        });
    }

    @Test
    void getUserIdFromToken_ShouldThrowException_WhenTokenIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.getUserIdFromToken("");
        });
    }

    @Test
    void getUserIdFromToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(JwtException.class, () -> {
            jwtUtils.getUserIdFromToken("invalid.token.here");
        });
    }

    @Test
    void getUserIdFromToken_ShouldReturnUserId_WhenTokenIsValid() {
        String token = jwtUtils.generateAccessToken("valid-id", null);
        String userId = jwtUtils.getUserIdFromToken(token);
        assertTrue("valid-id".equals(userId));
    }

    @Test
    void getAllClaimsFromToken_ShouldThrowException_WhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.getAllClaimsFromToken(null);
        });
    }

    @Test
    void getAllClaimsFromToken_ShouldThrowException_WhenTokenIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.getAllClaimsFromToken("");
        });
    }

    @Test
    void getAllClaimsFromToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(JwtException.class, () -> {
            jwtUtils.getAllClaimsFromToken("invalid.token.here");
        });
    }

    @Test
    void getAllClaimsFromToken_ShouldReturnClaims_WhenTokenIsValid() {
        String token = jwtUtils.generateAccessToken("valid-id", null);
        assertTrue(jwtUtils.getAllClaimsFromToken(token) != null);
    }

    @Test
    void validateJwtToken_ShouldThrowException_WhenTokenIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.validateJwtToken(null);
        });
    }

    @Test
    void validateJwtToken_ShouldThrowException_WhenTokenIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtils.validateJwtToken("");
        });
    }

    @Test
    void validateJwtToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(JwtException.class, () -> {
            jwtUtils.validateJwtToken("invalid.token.here");
        });
    }

    @Test
    void validateJwtToken_ShouldNotThrowException_WhenTokenIsValid() {
        String token = jwtUtils.generateAccessToken("valid-id", null);
        jwtUtils.validateJwtToken(token);
    }
}
