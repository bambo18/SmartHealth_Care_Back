package com.smarthealthdog.backend.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.diagnosis.celery.CeleryMessage;
import com.smarthealthdog.backend.dto.diagnosis.celery.DeliveryInfo;
import com.smarthealthdog.backend.dto.diagnosis.celery.MessageHeaders;
import com.smarthealthdog.backend.dto.diagnosis.celery.MessageProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RespirationCeleryTaskSender {
    private static final String QUEUE = "respiration";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Value("${respiration.inference.task-name:smart_health_dog_disease_detection.predict_respiration_rate}")
    private String taskName;

    /**
     * Enqueue using the SAME Redis/Celery envelope as the existing eye/urine tasks.
     * Must be invoked only after the AI worker registers the agreed task name.
     */
    public void enqueue(String presignedGetUrl, UUID recordId, UUID taskId) {
        if (presignedGetUrl == null || presignedGetUrl.isBlank()) {
            throw new IllegalArgumentException("Video download URL is required");
        }
        try {
            Object[] args = {presignedGetUrl, recordId.toString()};
            Map<String, Object> options = new HashMap<>();
            options.put("callbacks", null);
            options.put("errbacks", null);
            options.put("chain", null);
            options.put("chord", null);
            Object[] payload = {args, Map.of(), options};
            String base64Body = Base64.getEncoder().encodeToString(
                    mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));

            DeliveryInfo delivery = DeliveryInfo.builder()
                    .exchange("")
                    .routingKey(QUEUE)
                    .build();

            MessageProperties properties = MessageProperties.builder()
                    .correlationId(taskId.toString())
                    .deliveryTag(taskName)
                    .deliveryInfo(delivery)
                    .deliveryMode(2)
                    .bodyEncoding("base64")
                    .priority(0)
                    .build();

            // argsrepr is diagnostic only; the Base64 body carries the real arguments.
            // Avoid putting a signed URL into logs.
            MessageHeaders headers = MessageHeaders.builder()
                    .lang("py")
                    .task(taskName)
                    .id(taskId.toString())
                    .rootId(taskId.toString())
                    .argsRepr("('<redacted-presigned-url>', '" + recordId + "')")
                    .kwargsRepr("{}")
                    .ignoreResult(false)
                    .retries(0)
                    .timeLimit(new Object[]{null, null})
                    .parentId(null)
                    .build();

            CeleryMessage message = CeleryMessage.builder()
                    .body(base64Body)
                    .contentEncoding("utf-8")
                    .contentType("application/json")
                    .headers(headers)
                    .properties(properties)
                    .build();

            Long queueLength = redis.opsForList().rightPush(QUEUE, mapper.writeValueAsString(message));
            if (queueLength == null) {  
                throw new IllegalStateException("Redis did not acknowledge task insertion");
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize respiration Celery task", ex);
        }
    }
}

