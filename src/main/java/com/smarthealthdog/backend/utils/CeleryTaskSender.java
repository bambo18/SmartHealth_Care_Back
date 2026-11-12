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
    /**
     * 진단서 OCR 태스크 이름.
     *
     * ⚠️ AI 측과 아직 확정하지 않은 가칭이다 (Notion_AI연동협의.md §2-2).
     * 워커의 실제 태스크 이름과 한 글자라도 다르면 메시지는 큐에 정상적으로 들어가지만
     * 워커가 "모르는 작업"으로 버린다 — 양쪽 로그만 봐서는 원인을 찾기 어렵다.
     */
    private static final String HEALTH_CERTIFICATE_OCR_TASK_NAME = "smart_health_dog_disease_detection.extract_health_certificate_text";
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
                        // switch 로 두어 새 제출 유형이 조용히 소변 분기로 빠지지 않게 한다.
                        return generateCeleryMessage(
                            data.imageUrl(),
                            data.submission().getId(),
                            resolveTaskName(data.submission())
                        );
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
     * 제출 유형에 맞는 Celery 태스크 이름을 고른다.
     *
     * 봉투에는 제출 유형을 알리는 필드가 없다 — 워커는 이 이름 하나로 어느 함수를 부를지
     * 정한다. 따라서 여기서 틀리면 진단서가 소변 모델로 넘어간다.
     *
     * switch 를 쓰는 이유는 새 유형이 추가될 때 컴파일 단계에서 걸리게 하기 위해서다.
     */
    private String resolveTaskName(Submission submission) {
        return switch (submission.getType()) {
            case EYE -> submission.getPet().getSpecies() == PetSpecies.DOG
                ? DOG_DIAGNOSIS_TASK_NAME
                : CAT_DIAGNOSIS_TASK_NAME;
            case URINE -> URINE_DIAGNOSIS_TASK_NAME;
            case HEALTH_CERTIFICATE -> HEALTH_CERTIFICATE_OCR_TASK_NAME;
        };
    }

    /**
     * 파이썬 Celery 작업 메시지를 생성합니다.
     *
     * 봉투 구조는 모든 제출 유형이 동일하고 {@code headers.task} 만 다르다. 워커 입장에서는
     * 이 문자열이 유일한 라우팅 키이므로, 유형별 차이를 taskName 하나로 좁혀 둔다.
     *
     * @param imagePath 워커가 내려받을 이미지 URL
     * @param submissionId 서브미션 ID
     * @param taskName 파이썬 쪽 Celery 태스크 이름
     * @return 직렬화된 Celery 메시지 문자열
     * @throws JsonProcessingException 직렬화 실패 시
     */
    private String generateCeleryMessage(
        String imagePath, UUID submissionId, String taskName
    ) throws JsonProcessingException {
        String taskId = UUID.randomUUID().toString();
        String payloadBase64 = generateCeleryBody(imagePath, submissionId);

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

        // argsrepr 은 파이썬 튜플 표기를 흉내낸 문자열이다. 실제 인자는 body 에 들어 있고
        // 이 값은 Celery 의 로깅·모니터링용이다.
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

        CeleryMessage finalMessage = CeleryMessage.builder()
            .body(payloadBase64)
            .contentEncoding("utf-8")
            .contentType("application/json")
            .headers(headers)
            .properties(properties)
            .build();

        return objectMapper.writeValueAsString(finalMessage);
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
