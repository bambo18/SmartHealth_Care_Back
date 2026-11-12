package com.smarthealthdog.backend.dto.respiration;

import java.util.UUID;

public record RespirationUploadUrlResponse(
        UUID videoId,
        String uploadUrl,
        String objectKey,
        int expiresInSeconds
) {}
