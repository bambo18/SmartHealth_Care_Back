package com.smarthealthdog.backend.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.celery.CeleryMessage;
import com.smarthealthdog.backend.dto.diagnosis.celery.DeliveryInfo;
import com.smarthealthdog.backend.dto.diagnosis.celery.MessageHeaders;
import com.smarthealthdog.backend.dto.diagnosis.celery.MessageProperties;
import com.smarthealthdog.backend.dto.diagnosis.create.RequestDiagnosisData;
import com.smarthealthdog.backend.repositories.SubmissionRepository;

@Component
public class CeleryTaskSender implements DiagnosisTaskRequestClient {
    private final StringRedisTemplate stringRedisTemplate;
    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Define constants for your task and queue
    private static final String DOG_DIAGNOSIS_TASK_NAME = "smart_health_dog_disease_detection.predict_dog_disease";
    private static final String CAT_DIAGNOSIS_TASK_NAME = "smart_health_dog_disease_detection.predict_cat_disease";
    private static final String URINE_DIAGNOSIS_TASK_NAME = "smart_health_dog_disease_detection.predict_urine_analysis";
    private static final String CELERY_QUEUE_NAME = "celery"; 

    @Autowired
    public CeleryTaskSender(StringRedisTemplate stringRedisTemplate, SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void sendDiagnosisTaskInBatch(List<RequestDiagnosisData> requestDataList) {
        try {
            List<String> celeryMessages = requestDataList.stream()
                .map(data -> {
                    try {
                        if (data.submission().getType() == SubmissionTypeEnum.EYE) {
                            return generateCeleryMessageForEyeTest(
                                data.imageUrl(),
                                data.submission().getId(),
                                data.submission().getPet().getSpecies()
                            );
                        } else {
                            return generateCeleryMessageForUrineTest(
                                data.imageUrl(),
                                data.submission().getId()
                            );
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

            // Save all generated messages to each submission
            List<Submission> submissions = new ArrayList<>();
            for (int i = 0; i < requestDataList.size(); i++) {
                Submission submission = requestDataList.get(i).submission();
                submission.setCeleryTaskString(celeryMessages.get(i));
                submissions.add(submission);
            }

            submissionRepository.saveAll(submissions);
            stringRedisTemplate.opsForList().rightPushAll(CELERY_QUEUE_NAME, celeryMessages);
        } catch (Exception e) {
            // TODO: Connect to Sentry or logging system and log the exception
            throw e;
        }
    }

    @Override
    public void removeDiagnosisTasksInBatch(List<String> celeryTaskStrings) {
        try {
            for (String taskString : celeryTaskStrings) {
                stringRedisTemplate.opsForList().remove(CELERY_QUEUE_NAME, 1, taskString);
            }
        } catch (Exception e) {
            // TODO: Connect to Sentry or logging system and log the exception
            throw e;
        }
    }

    /**
     * 파이썬 Celery 작업 메시지를 생성합니다.
     * @param imagePath CloudFront 이미지 경로
     * @param submissionId 서브미션 ID
     * @param species 반려동물 종
     * @return 직렬화된 Celery 메시지 문자열
     * @throws JsonProcessingException
     */
    private String generateCeleryMessageForEyeTest(
        String imagePath, UUID submissionId, PetSpecies species
    ) throws JsonProcessingException {
        try {
            String taskId = UUID.randomUUID().toString();
            String payloadBase64 = generateCeleryBody(imagePath, submissionId);
            String taskName = species == PetSpecies.DOG ? DOG_DIAGNOSIS_TASK_NAME : CAT_DIAGNOSIS_TASK_NAME;

            // 1. Create nested objects
            DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .exchange("") // Default exchange
                .routingKey(CELERY_QUEUE_NAME)
                .build();

            MessageProperties properties = MessageProperties.builder()
                .correlationId(taskId)
                .deliveryTag(taskName)
                .deliveryInfo(deliveryInfo)
                .deliveryMode(2)
                .bodyEncoding("base64")
                .priority(0)
                .build();
            
            // The imagePath needs to be formatted for 'argsrepr' (e.g., "('./input/cataract1.jpg',)")
            String argsRepr = String.format("('%s','%s')", imagePath, submissionId.toString());

            MessageHeaders headers = MessageHeaders.builder()
                .lang("py")
                .task(taskName)
                .id(taskId)
                .rootId(taskId)
                .argsRepr(argsRepr)
                .kwargsRepr("{}")
                .ignoreResult(false)
                .retries(0)
                .timeLimit(new Object[]{null, null})
                .parentId(null)
                .build();
            
            // 2. Create the Final Message Dictionary
            CeleryMessage finalMessage = CeleryMessage.builder()
                .body(payloadBase64)
                .contentEncoding("utf-8")
                .contentType("application/json")
                .headers(headers)
                .properties(properties)
                .build();

            // 3. Serialize the final dictionary to the string that goes into Redis
            return objectMapper.writeValueAsString(finalMessage);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 파이썬 Celery 작업 메시지를 생성합니다.
     * @param imagePath CloudFront 이미지 경로
     * @param submissionId 서브미션 ID
     * @return 직렬화된 Celery 메시지 문자열
     * @throws JsonProcessingException
     */
    private String generateCeleryMessageForUrineTest(
        String imagePath, UUID submissionId
    ) throws JsonProcessingException {
        try {
            String taskId = UUID.randomUUID().toString();
            String payloadBase64 = generateCeleryBody(imagePath, submissionId);
            String taskName = URINE_DIAGNOSIS_TASK_NAME;

            // 1. Create nested objects
            DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .exchange("") // Default exchange
                .routingKey(CELERY_QUEUE_NAME)
                .build();

            MessageProperties properties = MessageProperties.builder()
                .correlationId(taskId)
                .deliveryTag(taskName)
                .deliveryInfo(deliveryInfo)
                .deliveryMode(2)
                .bodyEncoding("base64")
                .priority(0)
                .build();
            
            // The imagePath needs to be formatted for 'argsrepr' (e.g., "('./input/cataract1.jpg',)")
            String argsRepr = String.format("('%s','%s')", imagePath, submissionId.toString());

            MessageHeaders headers = MessageHeaders.builder()
                .lang("py")
                .task(taskName)
                .id(taskId)
                .rootId(taskId)
                .argsRepr(argsRepr)
                .kwargsRepr("{}")
                .ignoreResult(false)
                .retries(0)
                .timeLimit(new Object[]{null, null})
                .parentId(null)
                .build();
            
            // 2. Create the Final Message Dictionary
            CeleryMessage finalMessage = CeleryMessage.builder()
                .body(payloadBase64)
                .contentEncoding("utf-8")
                .contentType("application/json")
                .headers(headers)
                .properties(properties)
                .build();

            // 3. Serialize the final dictionary to the string that goes into Redis
            return objectMapper.writeValueAsString(finalMessage);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 파이썬 Celery 작업의 바디를 생성합니다.
     * @param imagePath CloudFront 이미지 경로
     * @param submissionId 서브미션 ID
     * @return 직렬화된 Celery 메시지 문자열
     * @throws JsonProcessingException
     */
    private String generateCeleryBody(
        String imagePath, UUID submissionId
    ) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. Construct the Decoded Task Body (Payload)
        // Structure: [args, kwargs, embed_options]
        
        // Positional args: [imagePath]
        Object[] args = new Object[]{imagePath, submissionId.toString()};
        
        // Keyword args: {}
        Map<String, Object> kwargs = new HashMap<>();

        // Task options (Celery 4.x/5.x format)
        Map<String, Object> options = new HashMap<>();
        options.put("callbacks", null);
        options.put("errbacks", null);
        options.put("chain", null);
        options.put("chord", null);

        // The full payload array
        Object[] taskPayload = new Object[]{args, kwargs, options};

        // 2. Serialize and Base64-encode the body
        String payloadJson = objectMapper.writeValueAsString(taskPayload);
        
        // Encode the JSON string to a byte array using UTF-8, then Base64 encode it
        String payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return payloadBase64;
    }
}
