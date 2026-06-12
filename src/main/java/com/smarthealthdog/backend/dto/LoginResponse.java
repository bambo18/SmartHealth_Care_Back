package com.smarthealthdog.backend.dto;

public record LoginResponse(String accessToken, String refreshToken, String expiration) { 
}
