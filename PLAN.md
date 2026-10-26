# 건강검진표 OCR 구현 계획

> **에이전트 작업자용:** 이 계획은 **작업 단위로** 실행한다 — `superpowers:subagent-driven-development`(작업마다 새 서브에이전트가 구현하고 새 리뷰어가 검토) 또는 `superpowers:executing-plans`(현재 세션에서 직접 구현)를 쓴다. 각 단계는 체크박스(`- [ ]`)로 추적하며, 한 작업을 끝낼 때마다 테스트를 돌리고 커밋한 뒤 다음 작업으로 넘어간다.

**목표:** 진단서 사진을 OCR로 읽어 12개 항목이 채워진 건강검진 기록을 만든다. **인식 실패 시 DB 행도 S3 객체도 남기지 않는다.**

**접근:** 신규 저장·조회 스택을 만들지 않는다. 기존 진단 업로드 파이프라인(`Submission` 집합체, `ImageUploader`, `ImgUtils`, `SubmissionMapper`, `SubmissionController`)을 **그대로 재사용하고 실행 순서만 비동기 → 동기로 바꾼다.** 신규 컨트롤러·신규 목록·신규 삭제 엔드포인트는 없다.

**기술 스택:** Spring Boot 3.5.6-SNAPSHOT / Java 24, Spring Data JPA + Flyway(PostgreSQL 18), AWS SDK v2 S3, Apache Tika 3.2.3, `java.net.http.HttpClient`, JUnit 5 + Mockito + H2

**명세:** [`SPEC.md`](SPEC.md) — 이 계획은 명세를 근거로 논증한다. 실행자는 둘 다 읽는다.

---

## 전역 제약

- **프로파일 없이 뜨지 않는다.** `application.yml`이 없고 `application-{dev,prod}.yml`만 있다. 실행은 항상 `./gradlew bootRun --args='--spring.profiles.active=dev'`.
- **설정값에 기본값이 없다.** 신규 설정 키를 추가하면 `application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml` **세 곳 모두에** 넣는다. 테스트 yml에는 반드시 `${ENV:기본값}` 형태로 기본값을 준다.
- **DB는 마이그레이션 소유다.** dev/prod는 `ddl-auto: none`. 엔티티를 바꾸면 새 번호 마이그레이션이 필요하다. **적용된 마이그레이션은 절대 수정하지 않는다.** `V53`(빈도 제한)·`V54`(병원 검색 권한 제거)까지 작성됐으므로 신규는 `V55`.
- **권한은 역할이 아니라 퍼미션 기반.** `@PreAuthorize("hasAuthority('can_use_health_check')")` 처럼 `PermissionEnum`의 snake_case 문자열을 쓴다. `can_use_health_check` · `can_view_own_health_records`는 **이미 `V11`에서 USER 역할에 부여되어 있어 권한 마이그레이션이 불필요하다.**
- **컨트롤러는 얇다.** 원칙(principal)에서 `Long.parseLong(userDetails.getUsername())`로 내부 숫자 ID를 꺼내고, 서비스 메서드 하나를 호출하고, `ResponseEntity`를 돌려준다.
- **에러는 코드다.** 자유 문자열을 던지지 않는다. `validation/ErrorCode` 상수를 담은 `exceptions/*` 예외를 던지고 `GlobalExceptionHandler`가 `ErrorMessage(List<String> codes, List<String> messages)`로 변환한다.
- **타인의 리소스는 항상 404, 절대 403이 아니다.** (`WalkService.java:58-61` 주석의 기존 방침)
- **시간은 UTC `Instant`.** 날짜만 필요한 컬럼은 `LocalDate`.
- **테스트는 쌍으로 온다.** `XxxUT.java` = `@ExtendWith(MockitoExtension.class)` 순수 단위, `XxxTest.java` = `@SpringBootTest @ActiveProfiles("test")` + H2. 추가 커버리지는 기존 쌍을 따른다.
- **Lombok `@RequiredArgsConstructor` + `private final` 생성자 주입.** 신규 코드에서 `@Autowired` 필드 주입을 쓰지 않는다. `@Value` 설정 주입은 필드로 쓴다(`AIDiagnosisClientService:27` 선례).
- **Javadoc·주석은 한국어.** 서비스 메서드에는 `@throws`로 `ErrorCode`를 던지는 예외를 적는다.
- **외부 의존성은 인터페이스 뒤에 숨긴다.** (`ImgUtils`, `ImageUploader`, `EmailService`, `DiagnosisTaskRequestClient` 선례)
- **브랜치·커밋 규칙:** 브랜치는 `type/CODE-ID-description` (예: `feat/HEALTH-01-진단서-ocr`). `develop`이 통합 브랜치, `main`은 배포 전용 — **`main`에 직접 커밋·머지 금지.** 커밋 제목 타입은 `Start` `Feat` `Fix` `Design` `Refactor` `Settings` `Comment` `Dependency/Plugin` `Docs` `Merge` `Deploy` `Rename` `Remove` `Revert` 만 허용 — **`Test` 타입은 없다**(테스트는 `Feat`/`Refactor`에 함께 담는다).
- **lint/format 태스크가 없다.** 주변 코드의 들여쓰기(4 스페이스)와 import 순서를 눈으로 맞춘다.
- **현재 저장소 상태 (2026-10-06 `git fetch` 로 확인):**
    - 이 작업 사본은 `origin/main`(`f5504f6`)의 스냅샷이다. `origin/develop`은 main보다 **20커밋 뒤처져** 있고 main이 갖지 않은 커밋은 0개다 — **develop에서 분기하면 main의 작업이 빠진다.**
    - ⚠ **`origin/GOOGLE/(OCR-V2)` 브랜치에 이 계획의 Task 1~17이 이미 구현돼 있다**(main + 9커밋). 착수 전 반드시 이 브랜치를 확인한다 — 모르고 시작하면 중복 구현이 된다.

      | 커밋 | 해당 작업 |
      |---|---|
      | `fa470ac` 멀티파트 상한·이미지 상한 yml 단일화 | Task 1 |
      | `c00186d` S3 Content-Type Tika 고정 + 인라인 차단 | Task 2 |
      | `9dfbcee` S3 동기 업로드 추출 + 보상 삭제 | Task 3 |
      | `facf550` 기존 파이프라인 재사용 구현 | Task 4~12, 15 (`V53`·`V55`, `DiagnosisAttempt`, `DateTextParser`, `ServiceUnavailableException`) |
      | `8d7c1b0` 병원 검색·`can_view_hospitals` 제거 | `V54` |
      | `7c76019` OCR 등록 엔드포인트 + Google Vision 클라이언트 | Task 13, 14, 16 |
      | `ce82ea1` 진단서 상세조회 API | Task 8(`GET /{id}/image`), 9(`type` 필터), 17 |

    - **남은 작업은 Task 18(`PATCH /{id}/certificate`)과 Task 19(README·정리)뿐이다.** `UpdateHealthCertificateRequest`·`UpdateHealthCertificateRequestErrorCode`·`@PatchMapping("/{id}/certificate")`가 그 브랜치에 아직 없다.
    - 그 브랜치의 `SubmissionTypeEnum`에는 `ORAL`이, `ErrorCode`에는 `PERIODONTITIS_INFERENCE_FAILED`·`ORAL_HEALTH_RECORD_NOT_FOUND`가 **남아 있다.** 이 계획은 구강 범위를 제외했으므로, 해당 상수 제거를 Task 19에 포함하거나 별도 정리 커밋으로 분리한다.
    - 기준 브랜치는 팀과 합의한다 (`git log --oneline origin/develop..origin/main` 으로 격차 확인).

---

## 검토 집중 지점

명세가 요구하지만 어느 작업의 테스트도 직접 짚지 않는, **실제 사용자를 물 가능성이 높은 입력**들. 각 줄 뒤에 그 테스트를 소유하는 작업 번호를 달았다.

1. **`photo_url`이 빈 문자열(`""`)인 과거 행에 `GET /{id}/image` 호출** — `createSubmission`은 `photoUrl=""`로 행을 만든다. 업로드 전/실패 행에 이미지 엔드포인트를 호출하면 `createPresignedUrl`이 `null`을 반환해 `{"image_url": null}`이 나간다. 사용자는 깨진 이미지를 본다. 기대: `404 RESOURCE_NOT_FOUND`. → **Task 8**
2. **날짜 파싱 실패로 `diagnosed_date`가 NULL인 행의 조회** — 명세는 파싱 실패를 허용(§3 설계 판단 ②)하므로 `onset_date`·`diagnosed_date`가 NULL인 행이 정상적으로 존재한다. 응답 매퍼가 NULL을 `LocalDate`로 다루다 NPE를 내거나 원문(`*_date_text`)을 함께 내려주지 않으면 사용자는 자기가 입력한 날짜가 사라진 것으로 본다. 기대: `diagnosed_date: null` + `diagnosed_date_text: "2020.10.28"` 이 함께 나간다. → **Task 17**
3. **같은 `submission_id`로 진단서가 두 번 저장되는 경로** — `uq_pet_health_certificates_submission`이 걸려 있어 재시도·중복 요청이 `DataIntegrityViolationException` → `Exception` catch-all → **500**으로 노출된다. 기대: 저장 전에 중복을 확인하고 의미 있는 코드로 거부. → **Task 15**
4. **PATCH로 `disease_name`에 공백 문자열(`"   "`)을 전송** — DB는 `NOT NULL`만 보므로 공백은 통과한다. 명세 §4.5는 "빈 값으로 지우는 것은 거부"라고만 적어 공백을 다루지 않는다. 기대: `400 INVALID_INPUT`. → **Task 18**
5. **빈도 제한이 `pet` 단위라 여러 반려동물로 우회 가능** — 유료 OCR 비용은 사용자 단위로 발생한다. 반려동물 5마리를 등록하면 제한이 5배로 느슨해진다. 기대: 사용자 단위 상한이 함께 걸린다. → **Task 7**

---

## 파일 구조

### 신규 파일

| 경로 | 책임 |
|---|---|
| `src/main/resources/db/migration/V56__create_pet_health_certificates.sql` | 진단서 테이블 신설 (빈도 제한 테이블은 `V53`) |
| `domain/PetHealthCertificate.java` | 진단서 기록 엔티티 (12개 항목 + 신뢰도 + 수정 플래그) |
| `domain/DiagnosisAttempt.java` | (pet, type)별 마지막 시도 시각 — 빈도 제한 전용 |
| `repositories/PetHealthCertificateRepository.java` | 진단서 조회·존재 확인 |
| `repositories/DiagnosisAttemptRepository.java` | 빈도 제한 조회·갱신 |
| `services/DiagnosisAttemptLimiter.java` | **시도 기준** 빈도 제한. 성공/실패와 무관하게 기록 |
| `services/HealthCertificateService.java` | 진단서 동기 흐름 오케스트레이션 (**비트랜잭션**) |
| `services/HealthCertificateWriter.java` | 진단서 저장 전용 (**`@Transactional`**) — 자기호출 함정 회피 |
| `clients/ocr/HealthCertificateOcrClient.java` | OCR 추상화 |
| `clients/ocr/GoogleVisionOcrClient.java` | Google Cloud Vision `DOCUMENT_TEXT_DETECTION` 구현 |
| `clients/ocr/HealthCertificateFieldExtractor.java` | OCR 텍스트 → 12개 항목. 순수 함수, 테스트 가능 |
| `utils/DateTextParser.java` | `2020.10.28` / `2020년 10월 28일` 등 → `LocalDate` |
| `dto/health/HealthCertificateResult.java` | 진단서 결과 (응답 공통 조각) |
| `dto/health/HealthCertificateCreatedResponse.java` | `POST /certificate` 201 본문 |
| `dto/health/UpdateHealthCertificateRequest.java` | PATCH 요청 (부분 수정) |
| `dto/health/ImageUrlResponse.java` | 서명 URL 응답 |
| `dto/health/ocr/HealthCertificateOcrResult.java` · `OcrTextBlock.java` | 외부 응답 → 내부 값 객체 |
| `exceptions/ServiceUnavailableException.java` | 503 전용. 현재 503을 내는 경로가 없다 |
| `validation/UpdateHealthCertificateRequestErrorCode.java` | 필드명 → `ErrorCode` 매핑 |

### 수정 파일 — 재사용 지점

| 경로 | 무엇을 어떻게 |
|---|---|
| `utils/FileUtils.java:25` | `isMIMEImage`가 `boolean`만 반환해 Tika 판정값을 버린다 → `detectImageMimeType`을 추가해 MIME 문자열을 살린다 |
| `services/FileUploadService.java:117` | 하드코딩 `5 * 1024 * 1024`를 `@Value` 주입으로 전환 |
| `utils/ImageUploader.java` | `storeSubmissionImage(event, prefix)` · `delete(key)` 2개 추가 |
| `utils/S3Uploader.java:78,110,146` | `putObject` 본문을 동기 메서드로 추출해 **비동기 리스너가 그 메서드를 호출**하게 한다. 세 호출 전부에 Tika contentType + `attachment` + SSE 적용 |
| `utils/LocalImageUploader.java` | `@Profile("local")`로 사실상 미사용이지만 인터페이스 구현체이므로 신규 메서드 2개 구현 (미구현 시 **컴파일 실패**) |
| `utils/ImgUtils.java` | `getSecureImgUrl(key, ttl)` 추가 |
| `utils/DevImgUtils.java` · `ProdImgUtils.java` | `private createPresignedUrl(key)`를 TTL 파라미터화, `getImgUrlForAIWorker`를 위임으로 변경 |
| `src/test/.../TestImageConfig.java` | `TestImgUtils`에 신규 메서드 구현 (미구현 시 **테스트 컴파일 실패**) |
| `domain/SubmissionTypeEnum.java` | `HEALTH_CERTIFICATE` 추가 |
| `utils/CeleryTaskSender.java:51-62` | `if (EYE) {...} else {소변}` → `switch`. 신규 유형은 `IllegalStateException` |
| `services/SubmissionService.java` | `createCompletedSubmission` + 상세 조회 2종 추가 |
| `services/AIDiagnosisClientService.java:50-59,88-97` | 중복된 빈도 제한 블록 2개를 `DiagnosisAttemptLimiter` 호출로 교체 |
| `repositories/SubmissionSpecifications.java` | `filterUserSubmissions` · `filterPetSubmissions`에 `type` 선택 조건 추가 + `filterPetSubmissions`의 `DELETED` 비대칭 정리 |
| `dto/diagnosis/get/SubmissionMapper.java` | 매퍼 2종 추가 (`photoUrl`은 `null`) |
| `controllers/PetController.java:178` | 업로드 엔드포인트 1개 추가 |
| `controllers/SubmissionController.java` | 상세 1 · PATCH 1 · 이미지 1 추가, 목록 2곳에 `type` 파라미터 |
| `validation/ErrorCode.java` | 상수 5개 추가 |
| `validation/ValidErrorCodeFinder.java:17-24,41-56` | 필드 + `case` **둘 다** 추가 |
| `handlers/GlobalExceptionHandler.java` | 503 핸들러 + `InternalServerErrorException` 전용 핸들러 |
| `application-dev.yml` · `application-prod.yml` · `application-test.yml` · `README.md` | 설정 키 추가 |

### 작업 순서 의존성

```
Phase 0 (공유 기반) ── Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9
                                            │
Phase 1 (HEALTH-01) ────────────────────────┴─ 10 → 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18
Phase 2 (문서·정리) ──────────────────────────────── 19
```

> **범위는 `HEALTH-01`(진단서 OCR) 하나다.** 구강 사진 치주염 판정(`HEALTH-02`)은 Python 프로젝트가 동기 HTTP 추론 엔드포인트를 새로 열어야 해서 착수할 수 없고, 이 계획에서 **제외했다**. 별도 스펙 ID·별도 계획으로 다룬다. Phase 0 + Phase 1만으로 **배포 가능한 완성품**(진단서 등록·조회·수정)이 나온다.

---

# Phase 0 — 공유 기반

## Task 1: 이미지 상한을 설정 한 곳으로 모은다

> ✅ **이 작업의 yml 부분은 이미 완료됐다.** 세 yml 파일에 `spring.servlet.multipart`(7MB/9MB)와 `upload.image.max-size-bytes`(7340032)가 들어가 있고 `./gradlew test`가 환경변수 없이 통과한다. 남은 것은 아래 Step 1·4(`FileUploadService`의 하드코딩 5MB를 `@Value` 주입으로 전환)와 그 테스트다.

**왜 먼저인가:** 현재 `spring.servlet.multipart` 설정이 **어디에도 없어** Spring 기본값 **1MB**가 적용된다. 폰으로 찍은 진단서는 대부분 1MB를 넘으므로 **이 설정 없이는 기능 자체가 동작하지 않는다.** 동시에 `FileUploadService.java:117`이 5MB를 코드에 박아두고 있어, yml만 올리면 그 사이 크기의 파일이 컨테이너를 통과한 뒤 `INVALID_IMAGE`로 거부되는 모순이 생긴다.

**상한을 7MB로 정한 근거 (10MB가 아닌 이유):** Google Vision은 이미지를 **base64로 JSON 본문에 담아** 보내므로 크기가 약 1.33배로 불어난다. 10MB 사진은 요청 본문 약 13.4MB가 되어 Vision의 요청 크기 한도를 넘길 위험이 있고, 그러면 Vision이 4xx를 내고 Task 14의 매핑에 따라 **500**으로 노출된다 — 사용자는 원인을 알 수 없다. 7MB → base64 약 9.3MB로 안전하게 들어간다. 한편 5MB로 낮추면 압축이 강제돼 OCR 신뢰도가 떨어지므로(`OCR_RECOGNITION_FAILED` 거부율 상승), 7MB가 두 제약을 모두 만족하는 값이다.

> **착수 전 확인:** Google Cloud Vision `images:annotate`의 요청 크기 한도와 인라인 `content` 최대 크기를 공식 문서에서 확인한다. 가정과 다르면 **yml의 숫자만** 조정한다 — 상한의 단일 출처가 yml이므로 코드는 건드리지 않는다. 이것이 하드코딩 5MB를 `@Value` 주입으로 바꾸는 이유다.

**Files:**
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-prod.yml`
- Modify: `src/test/resources/application-test.yml`
- Modify: `src/main/java/com/smarthealthdog/backend/services/FileUploadService.java:112-139`
- Test: `src/test/java/com/smarthealthdog/backend/services/FileUploadServiceUT.java`

**Interfaces:**
- Consumes: 없음 (첫 작업)
- Produces: 설정 키 `upload.image.max-size-bytes` (long). 이후 모든 이미지 검증이 이 값을 쓴다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`FileUploadServiceUT.java`에 추가한다. `@Value` 필드는 Mockito가 주입하지 않으므로 `ReflectionTestUtils`로 심는다 — 기존 `AIDiagnosisClientServiceTest`가 `ReflectionTestUtils`를 쓰는 선례가 있다.

```java
// import org.springframework.test.util.ReflectionTestUtils;
// import org.springframework.mock.web.MockMultipartFile;
// import static org.junit.jupiter.api.Assertions.*;

@Test
void validateImageFile_주입된_상한을_사용한다() {
    ReflectionTestUtils.setField(fileUploadService, "maxImageSizeBytes", 7L * 1024 * 1024);

    // 6MB — 기존 하드코딩 5MB 상한이라면 거부되고, 주입된 7MB 상한이라면 통과한다.
    byte[] sixMegabytes = new byte[6 * 1024 * 1024];
    // PNG 매직 바이트를 앞에 둬 Tika가 image/png 로 판정하게 한다.
    System.arraycopy(
        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
        0, sixMegabytes, 0, 8
    );

    MockMultipartFile file = new MockMultipartFile(
        "image", "cert.png", "image/png", sixMegabytes
    );

    assertDoesNotThrow(() -> fileUploadService.validateImageFile(file));
}

@Test
void validateImageFile_상한을_넘기면_INVALID_IMAGE를_던진다() {
    ReflectionTestUtils.setField(fileUploadService, "maxImageSizeBytes", 1024L);

    byte[] tooBig = new byte[2048];
    System.arraycopy(
        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
        0, tooBig, 0, 8
    );

    MockMultipartFile file = new MockMultipartFile("image", "cert.png", "image/png", tooBig);

    InvalidRequestDataException e = assertThrows(
        InvalidRequestDataException.class,
        () -> fileUploadService.validateImageFile(file)
    );
    assertEquals(ErrorCode.INVALID_IMAGE, e.getErrorCode());
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'FileUploadServiceUT'
```

기대: 첫 테스트가 `InvalidRequestDataException`으로 FAIL (하드코딩 5MB가 6MB를 거부). 두 번째도 FAIL 또는 `maxImageSizeBytes` 필드가 없다는 `IllegalArgumentException`.

- [x] **Step 3: 설정 키를 세 yml에 넣는다 — 완료됨**

`application-dev.yml` · `application-prod.yml` — `spring:` 블록 안 `security:` 뒤에 `servlet`을 넣고, 최상위에 `upload`를 추가했다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 7MB
      max-request-size: 9MB

upload:
  image:
    # spring.servlet.multipart.max-file-size 와 반드시 같은 값을 유지한다.
    # 컨테이너 상한과 코드 상한이 다르면, 둘 사이 크기의 파일이
    # 컨테이너를 통과한 뒤 INVALID_IMAGE 로 거부된다.
    max-size-bytes: ${UPLOAD_IMAGE_MAX_SIZE_BYTES:7340032} # 7MB
```

`src/test/resources/application-test.yml` — 같은 내용을 기본값과 함께 넣었고, **`naver.*` 4개 키의 기본값도 함께 추가했다**(`clients/naver/*`가 기본값 없이 주입해 `@SpringBootTest`가 환경변수 없이 뜨지 않던 기존 문제). 환경변수를 하나도 설정하지 않은 상태에서 `./gradlew test`가 통과하는 것으로 검증했다.

> **2026-10-06 갱신:** 병원 검색 기능과 `clients/naver/*`를 삭제했으므로 이 `naver.*` 블록도 세 yml에서 함께 제거했다. 이제 그 키에 의존하는 코드가 없다.

- [ ] **Step 4: `FileUploadService`가 주입값을 쓰게 바꾼다**

`services/FileUploadService.java` — 클래스 상단 필드를 추가하고 `validateImageFile`의 상한 블록을 교체한다.

```java
// 추가 import
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
@Service
public class FileUploadService {
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 업로드 허용 최대 이미지 크기(바이트).
     * spring.servlet.multipart.max-file-size 와 같은 값이어야 한다.
     */
    @Value("${upload.image.max-size-bytes}")
    private long maxImageSizeBytes;
```

`validateImageFile` 안의 기존 두 줄을 지우고 주입값을 쓴다.

```java
        // 파일 크기 검사 — 상한은 yml(upload.image.max-size-bytes)이 단일 출처다.
        if (file.getSize() > maxImageSizeBytes) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
```

> 삭제 대상: `long maxFileSize = 5 * 1024 * 1024; // 5MB` 와 그 아래 `if (file.getSize() > maxFileSize)` 블록.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'FileUploadServiceUT' --tests 'FileUploadServiceTest'
```

기대: PASS. `FileUploadServiceTest`(`@SpringBootTest`)는 `application-test.yml`에서 새 키를 읽어야 뜨므로, 여기서 세 yml 중 하나를 빠뜨렸다면 컨텍스트 로딩 실패로 드러난다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/resources/application-dev.yml src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml \
        src/main/java/com/smarthealthdog/backend/services/FileUploadService.java \
        src/test/java/com/smarthealthdog/backend/services/FileUploadServiceUT.java
git commit -m "Settings: 멀티파트 상한 설정 추가 및 이미지 크기 상한을 yml로 단일화"
```

---

## Task 2: 저장 Content-Type을 Tika 판정값으로 고정한다

**왜:** `S3Uploader`는 세 곳(`:78` 프로필, `:110` 반려동물, `:146` 진단)에서 `.contentType(event.contentType())`를 쓰는데, 이 값은 **멀티파트 파트 헤더 그대로**다(`MultipartFile.getContentType()`). `FileUtils.isMIMEImage`는 Tika로 바이트를 검사해 놓고 `boolean`만 반환해 판정값을 버린다. 그래서 **유효한 JPEG의 EXIF에 스크립트를 넣고 파트 헤더만 `text/html`로 바꾸면** 확장자 검사와 Tika 검사를 모두 통과하면서 S3에 `text/html`로 저장되고, 서명 URL을 열면 브라우저가 HTML로 렌더링한다. 서명 URL의 5분 만료는 이 경로를 막지 못한다(SPEC §9.3).

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/utils/FileUtils.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/S3Uploader.java:76-83, 107-114, 138-150`
- Test: `src/test/java/com/smarthealthdog/backend/utils/FileUtilsUT.java` (신규)

**Interfaces:**
- Consumes: Task 1의 상한 설정 (`validateImageFile`이 이미 Tika를 호출하는 흐름)
- Produces:
    - `FileUtils.detectImageMimeType(InputStream) → String` — 허용 이미지면 Tika 판정 MIME, 아니면 `null`
    - `FileUtils.extensionForMimeType(String mime) → String` — `"image/jpeg"` → `".jpg"`, `"image/png"` → `".png"`
    - `S3Uploader`의 세 `putObject`가 모두 Tika 판정값 + `contentDisposition("attachment")` + SSE를 쓴다. Task 3이 이 본문을 동기 메서드로 추출한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/utils/FileUtilsUT.java` 신규 생성.

```java
package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class FileUtilsUT {

    private static final byte[] PNG_MAGIC =
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    void detectImageMimeType_PNG_바이트를_image_png으로_판정한다() throws IOException {
        assertEquals(
            "image/png",
            FileUtils.detectImageMimeType(new ByteArrayInputStream(PNG_MAGIC))
        );
    }

    @Test
    void detectImageMimeType_JPEG_바이트를_image_jpeg으로_판정한다() throws IOException {
        assertEquals(
            "image/jpeg",
            FileUtils.detectImageMimeType(new ByteArrayInputStream(JPEG_MAGIC))
        );
    }

    @Test
    void detectImageMimeType_이미지가_아니면_null을_반환한다() throws IOException {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertNull(FileUtils.detectImageMimeType(new ByteArrayInputStream(html)));
    }

    @Test
    void extensionForMimeType_판정값에서_확장자를_만든다() {
        assertEquals(".jpg", FileUtils.extensionForMimeType("image/jpeg"));
        assertEquals(".png", FileUtils.extensionForMimeType("image/png"));
    }

    @Test
    void extensionForMimeType_허용목록_밖이면_IllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> FileUtils.extensionForMimeType("text/html")
        );
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'FileUtilsUT'
```

기대: 컴파일 실패 — `detectImageMimeType` / `extensionForMimeType` 메서드가 없다.

- [ ] **Step 3: `FileUtils`에 판정값을 살리는 메서드를 넣는다**

`utils/FileUtils.java` 전체를 아래로 교체한다. **기존 `isMIMEImage`는 호출부(`FileUploadService.java:130`)를 건드리지 않도록 남기고, 내부에서 새 메서드를 호출하게 바꾼다.**

```java
package com.smarthealthdog.backend.utils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.Tika;

public class FileUtils {
    private static final Tika tika = new Tika();

    private static final String[] ALLOWED_IMAGE_TYPES = {
        "image/jpeg",
        "image/png",
        "image/jpg"
    };

    /**
     * MIME 타입을 기반으로 파일이 이미지인지 검증
     * @param ioStream 파일의 InputStream
     * @return 이미지 파일이면 true, 아니면 false
     * @throws IOException
     * @throws IllegalArgumentException ioStream이 null인 경우 발생
     */
    public static boolean isMIMEImage(InputStream ioStream) throws IOException, IllegalArgumentException {
        return detectImageMimeType(ioStream) != null;
    }

    /**
     * Tika가 바이트 내용을 보고 판정한 MIME 타입을 반환한다.
     *
     * 클라이언트가 보낸 Content-Type 헤더는 신뢰하지 않는다.
     * 이 반환값이 S3 저장 시 contentType 과 확장자의 유일한 근거다.
     *
     * @param ioStream 파일의 InputStream
     * @return 허용 이미지면 판정된 MIME 타입, 허용 목록 밖이면 null
     * @throws IOException 스트림 읽기 실패 시
     * @throws IllegalArgumentException ioStream이 null인 경우
     */
    public static String detectImageMimeType(InputStream ioStream) throws IOException, IllegalArgumentException {
        if (ioStream == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        String mimeType = tika.detect(ioStream);
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equalsIgnoreCase(mimeType)) {
                return mimeType;
            }
        }

        return null;
    }

    /**
     * 판정된 MIME 타입에 대응하는 파일 확장자를 반환한다.
     *
     * 클라이언트가 보낸 파일명의 확장자를 쓰지 않는 이유는,
     * 파일명이 공격자 제어 입력이기 때문이다.
     *
     * @param mimeType 허용 이미지 MIME 타입
     * @return 점을 포함한 확장자 (예: ".jpg")
     * @throws IllegalArgumentException 허용 목록 밖의 MIME 타입인 경우
     */
    public static String extensionForMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("MIME type is null");
        }

        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("허용되지 않은 MIME 타입: " + mimeType);
        };
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'FileUtilsUT' --tests 'FileUploadServiceUT'
```

기대: PASS. `isMIMEImage`의 동작이 그대로이므로 `FileUploadServiceUT`도 깨지지 않는다.

- [ ] **Step 5: `S3Uploader`의 세 `putObject`를 모두 고친다**

`utils/S3Uploader.java` — import를 추가한다.

```java
import java.io.ByteArrayInputStream;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
```

클래스 하단에 공통 헬퍼를 추가한다.

```java
    /**
     * 바이트 내용을 Tika로 판정해 S3에 안전하게 저장한다.
     *
     * 클라이언트가 보낸 contentType·파일명 확장자는 쓰지 않는다.
     * Content-Disposition 을 attachment 로 고정해, 설령 판정이 뚫려도
     * 브라우저가 인라인 렌더링하지 않게 한다.
     *
     * @param prefix S3 키 접두사 (예: "profiles/")
     * @param fileBytes 업로드할 바이트
     * @return 저장된 S3 object key
     * @throws InvalidRequestDataException 허용 이미지가 아닌 경우
     */
    private String putImageObject(String prefix, byte[] fileBytes) {
        String detectedMimeType;
        try {
            detectedMimeType = FileUtils.detectImageMimeType(new ByteArrayInputStream(fileBytes));
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (detectedMimeType == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String key = prefix + UUID.randomUUID() + FileUtils.extensionForMimeType(detectedMimeType);

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(detectedMimeType)
                .contentDisposition("attachment")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build(),
            RequestBody.fromBytes(fileBytes)
        );

        return key;
    }
```

`uploadProfilePicture`에서 `ext`·`key` 계산과 `putObject` 블록을 지우고 호출로 바꾼다.

```java
        // 삭제: String ext = event.originalFilename().substring(...);
        // 삭제: String key = "profiles/" + UUID.randomUUID() + ext;
        // 삭제: s3Client.putObject(...);
        String key = putImageObject("profiles/", event.fileBytes());

        event.user().setProfilePic(key);
        userRepository.save(event.user());
```

`uploadPetImage`도 같게 바꾼다.

```java
        String key = putImageObject("pets/", event.fileBytes());

        event.pet().setProfileImage(key);
        petRepository.save(event.pet());
```

`uploadSubmissionImage`의 `try` 블록 안을 바꾼다.

```java
        try {
            String key = putImageObject("diagnoses/", event.fileBytes());
            // ... 기존의 photoUrl 설정·저장 로직은 그대로 둔다
        } catch (Exception e) {
            // TODO: 업로드 실패 시, Sentry나 로그 시스템에 알림 전송 기능 필요
            submissionService.failSubmission(submission, SubmissionFailureReasonEnum.SERVICE_ERROR);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
```

> **부수 효과(의도한 것):** 이 변경으로 `.JPG` 대문자 확장자가 더 이상 거부되지 않는다. 확장자를 파일명에서 뽑지 않고 판정값에서 만들기 때문이다. SPEC §2.3이 "확장자 비교를 소문자 변환 후 수행"으로 적은 개선이 자동 달성된다.

- [ ] **Step 6: 기존 테스트가 깨지지 않는지 확인한다**

```bash
./gradlew test --tests 'FileUploadService*' --tests 'AIDiagnosisClientService*'
```

기대: PASS. `S3Uploader`는 `@Profile({"prod","dev"})`라 `test` 프로파일에서 빈으로 뜨지 않으므로 통합 테스트에 영향이 없다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/utils/FileUtils.java \
        src/main/java/com/smarthealthdog/backend/utils/S3Uploader.java \
        src/test/java/com/smarthealthdog/backend/utils/FileUtilsUT.java
git commit -m "Fix: S3 저장 Content-Type을 Tika 판정값으로 고정하고 인라인 렌더링 차단"
```

---

## Task 3: 동기 업로드·삭제 경로를 추출한다

**왜:** 현재 `S3Uploader.uploadSubmissionImage`는 `@Async @TransactionalEventListener(AFTER_COMMIT)`이라 **DB 커밋과 HTTP 응답이 S3 업로드보다 먼저 끝난다.** 이 경로로는 "판별 실패 시 미저장"이 성립할 수 없다. 그렇다고 S3 업로드 코드를 새로 쓰지는 않는다 — Task 2에서 만든 `putImageObject`를 **인터페이스에 노출된 동기 메서드로 감싸고, 기존 비동기 리스너가 그 메서드를 호출**하게 한다. 보상 삭제용 `delete`도 함께 넣는다. **현재 저장소에 S3 삭제 코드가 전혀 없다**(`deleteObject` 검색 결과 0건) — 이 기능에서 처음 들어간다.

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/utils/ImageUploader.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/S3Uploader.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/LocalImageUploader.java`
- Test: `src/test/java/com/smarthealthdog/backend/utils/S3UploaderUT.java` (신규)

**Interfaces:**
- Consumes: Task 2의 `putImageObject`, `FileUtils.detectImageMimeType`
- Produces:
    - `ImageUploader.storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) → String` (S3 key 반환, 예외는 호출자에게 전파)
    - `ImageUploader.delete(String key) → void` (존재하지 않는 키는 무시)
    - Task 15·21이 이 두 메서드를 호출한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/utils/S3UploaderUT.java` 신규 생성. `S3Client`를 목으로 두고 `ArgumentCaptor`로 **실제로 보낸 `PutObjectRequest`**를 검증한다 — Task 2의 보안 변경을 회귀로 묶는 유일한 방법이다.

```java
package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.services.SubmissionService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
public class S3UploaderUT {

    @InjectMocks
    private S3Uploader s3Uploader;

    @Mock private S3Client s3Client;
    @Mock private UserRepository userRepository;
    @Mock private PetRepository petRepository;
    @Mock private SubmissionService submissionService;
    @Mock private Submission submission;

    @Captor private ArgumentCaptor<PutObjectRequest> putCaptor;
    @Captor private ArgumentCaptor<DeleteObjectRequest> deleteCaptor;

    private static final byte[] JPEG_BYTES =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};

    private void givenBucket() {
        ReflectionTestUtils.setField(s3Uploader, "bucket", "test-bucket");
    }

    @Test
    void storeSubmissionImage_클라이언트가_보낸_contentType을_무시하고_Tika_판정값을_저장한다() {
        givenBucket();

        // 실제 바이트는 JPEG 인데 클라이언트는 text/html 이라고 주장한다.
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, JPEG_BYTES, "evil.jpg", "text/html"
        );

        String key = s3Uploader.storeSubmissionImage(event, "health-certificates/");

        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        PutObjectRequest sent = putCaptor.getValue();

        assertEquals("image/jpeg", sent.contentType());
        assertEquals("attachment", sent.contentDisposition());
        assertTrue(key.startsWith("health-certificates/"), "접두사가 적용되어야 한다");
        assertTrue(key.endsWith(".jpg"), "확장자는 Tika 판정값에서 나와야 한다: " + key);
        assertEquals(key, sent.key());
    }

    @Test
    void storeSubmissionImage_파일명_확장자가_달라도_Tika_판정값을_따른다() {
        givenBucket();

        // 파일명은 .png 지만 바이트는 JPEG 다.
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, JPEG_BYTES, "mislabeled.png", "image/png"
        );

        String key = s3Uploader.storeSubmissionImage(event, "health-certificates/");

        assertTrue(key.endsWith(".jpg"), "파일명이 아니라 바이트 판정이 이겨야 한다: " + key);
    }

    @Test
    void storeSubmissionImage_이미지가_아니면_INVALID_IMAGE를_던지고_putObject를_호출하지_않는다() {
        SubmissionImageUploadEvent event = new SubmissionImageUploadEvent(
            submission, "<html>x</html>".getBytes(), "x.jpg", "image/jpeg"
        );

        assertThrows(
            InvalidRequestDataException.class,
            () -> s3Uploader.storeSubmissionImage(event, "health-certificates/")
        );

        verify(s3Client, org.mockito.Mockito.never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void delete_버킷과_키로_deleteObject를_호출한다() {
        givenBucket();

        s3Uploader.delete("health-certificates/abc.jpg");

        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertEquals("test-bucket", deleteCaptor.getValue().bucket());
        assertEquals("health-certificates/abc.jpg", deleteCaptor.getValue().key());
    }

    @Test
    void delete_키가_비어있으면_아무것도_하지_않는다() {
        s3Uploader.delete(null);
        s3Uploader.delete("");

        verify(s3Client, org.mockito.Mockito.never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'S3UploaderUT'
```

기대: 컴파일 실패 — `storeSubmissionImage` / `delete` 가 없다.

- [ ] **Step 3: 인터페이스에 두 메서드를 추가한다**

`utils/ImageUploader.java` 전체를 교체한다.

```java
package com.smarthealthdog.backend.utils;

import java.io.IOException;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;

public interface ImageUploader {

    void uploadProfilePicture(
        UserProfilePictureUploadEvent event
    ) throws IOException;

    void uploadPetImage(
        PetPictureUploadEvent event
    ) throws IOException;

    void uploadSubmissionImage(
        SubmissionImageUploadEvent event
    ) throws IOException;

    /**
     * 제출 이미지를 동기적으로 저장하고 S3 object key 를 반환한다.
     *
     * 비동기 경로(uploadSubmissionImage)와 달리 호출 시점에 저장이 끝나므로,
     * "판별 실패 시 미저장" 보증이 필요한 동기 흐름에서 이 메서드를 쓴다.
     * 예외는 삼키지 않고 호출자에게 그대로 전파한다.
     *
     * @param event 저장할 이미지 바이트를 담은 값 객체 (이벤트로 발행하지 않는다)
     * @param prefix S3 키 접두사 (예: "health-certificates/")
     * @return 저장된 S3 object key
     */
    String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix);

    /**
     * S3 object 를 삭제한다. 보상 삭제용이다.
     * 존재하지 않는 키나 빈 키는 조용히 무시한다.
     *
     * @param key 삭제할 S3 object key
     */
    void delete(String key);
}
```

- [ ] **Step 4: `S3Uploader`에 구현하고 비동기 리스너가 같은 구현을 쓰게 한다**

`utils/S3Uploader.java` — import를 추가한다.

```java
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
```

Task 2에서 만든 `putImageObject` 바로 위에 두 메서드를 추가한다.

```java
    /**
     * 제출 이미지를 동기적으로 S3에 저장한다.
     * 비동기 리스너(uploadSubmissionImage)와 완전히 같은 구현을 공유한다.
     */
    @Override
    public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
        if (event == null || event.fileBytes() == null || event.fileBytes().length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("S3 키 접두사가 비어 있습니다.");
        }

        return putImageObject(prefix, event.fileBytes());
    }

    /**
     * 보상 삭제. 이 저장소에서 처음 들어가는 S3 삭제 경로다.
     */
    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
            );
        } catch (NoSuchKeyException e) {
            // 이미 없는 객체는 삭제 성공과 같게 취급한다.
        }
    }
```

`uploadSubmissionImage`의 `try` 블록이 `putImageObject`를 직접 부르던 것을 `storeSubmissionImage` 경유로 바꾼다 — **같은 구현을 공유한다는 사실을 코드로 못박는다.**

```java
        try {
            String key = storeSubmissionImage(event, "diagnoses/");
            submission.setPhotoUrl(key);
            submissionService.saveSubmission(submission);
        } catch (Exception e) {
            // TODO: 업로드 실패 시, Sentry나 로그 시스템에 알림 전송 기능 필요
            submissionService.failSubmission(submission, SubmissionFailureReasonEnum.SERVICE_ERROR);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
```

> 기존 `uploadSubmissionImage` 본문의 null 검사 3종(`fileBytes`/`originalFilename`/`contentType`)은 `storeSubmissionImage`가 바이트만 검사하도록 바뀌었으니 **`originalFilename`·`contentType` 검사는 지운다.** 이제 둘 다 저장에 쓰이지 않으므로 검사할 이유가 없다.

- [ ] **Step 5: `LocalImageUploader`에도 구현한다 (안 하면 컴파일 실패)**

`utils/LocalImageUploader.java` 클래스 하단에 추가한다. `@Profile("local")`로 사실상 미사용이지만 인터페이스 구현체다.

```java
    @Override
    public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
        if (event == null || event.fileBytes() == null || event.fileBytes().length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        try {
            String detectedMimeType = FileUtils.detectImageMimeType(
                new java.io.ByteArrayInputStream(event.fileBytes())
            );
            if (detectedMimeType == null) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
            }

            String key = prefix + UUID.randomUUID() + FileUtils.extensionForMimeType(detectedMimeType);
            Path target = uploadDir.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, event.fileBytes());

            return key;
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(uploadDir.resolve(key));
        } catch (IOException e) {
            // 보상 삭제 실패는 요청을 막지 않는다.
        }
    }
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'S3UploaderUT'
./gradlew build -x test
```

기대: `S3UploaderUT` PASS, 전체 컴파일 성공(두 구현체 모두 신규 메서드를 가졌는지 확인).

- [ ] **Step 7: 기존 비동기 경로 회귀를 확인한다**

```bash
./gradlew test --tests 'AIDiagnosisClientService*' --tests 'FileUploadService*' --tests 'SubmissionService*'
```

기대: PASS. 눈·소변 비동기 업로드 동작이 바뀌지 않아야 한다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/utils/ImageUploader.java \
        src/main/java/com/smarthealthdog/backend/utils/S3Uploader.java \
        src/main/java/com/smarthealthdog/backend/utils/LocalImageUploader.java \
        src/test/java/com/smarthealthdog/backend/utils/S3UploaderUT.java
git commit -m "Refactor: S3 업로드를 동기 메서드로 추출하고 보상 삭제 기능 추가"
```

---

## Task 4: 서명 URL에 TTL을 파라미터로 받는다

**왜:** 서명 URL 생성 로직은 **이미 있다.** `DevImgUtils.createPresignedUrl` · `ProdImgUtils.createPresignedUrl`이 둘 다 1시간 고정으로 presigned URL을 만든다. 중복 작성할 필요가 없고 TTL만 열어주면 된다. 그리고 진단서 이미지에는 **`getImgUrl`을 절대 쓰지 않는다** — `ProdImgUtils.getImgUrl`은 CloudFront 도메인이 설정돼 있으면 **무서명·무만료 URL**을 반환하므로, 견주 성명·주소가 찍힌 이미지에 쓰면 그대로 공개된다(SPEC §2.5, §9.2).

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/utils/ImgUtils.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/DevImgUtils.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/ProdImgUtils.java`
- Modify: `src/test/java/com/smarthealthdog/backend/TestImageConfig.java`

**Interfaces:**
- Consumes: 없음
- Produces: `ImgUtils.getSecureImgUrl(String key, Duration ttl) → String` — 항상 서명 URL. `key`가 null/blank면 `null`. Task 8·17이 쓴다.

- [ ] **Step 1: 인터페이스를 넓힌다**

`utils/ImgUtils.java`:

```java
package com.smarthealthdog.backend.utils;

import java.time.Duration;

public interface ImgUtils {
    String getImgUrl(String key);
    String getImgUrlForAIWorker(String key);

    /**
     * 지정한 기간 동안만 유효한 서명 URL을 반환한다.
     *
     * getImgUrl 과 달리 CloudFront 무서명 URL 분기를 절대 타지 않는다.
     * 개인정보가 찍힌 이미지(진단서 사진)는 이 메서드만 사용한다.
     *
     * @param key S3 object key
     * @param ttl 서명 유효 기간
     * @return 서명 URL. key 가 비어 있으면 null
     */
    String getSecureImgUrl(String key, Duration ttl);
}
```

- [ ] **Step 2: 컴파일 실패를 확인한다**

```bash
./gradlew build -x test
```

기대: `DevImgUtils` · `ProdImgUtils`가 추상 메서드를 구현하지 않았다는 컴파일 에러 2건.

- [ ] **Step 3: `DevImgUtils`를 고친다**

`createPresignedUrl`에 TTL 인자를 받게 바꾸고, 기존 호출부 두 곳이 1시간을 넘기게 한다. **기존 동작은 바뀌지 않는다.**

```java
    @Override
    public String getImgUrl(String key) {
        return createPresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * AI Worker용 이미지 URL도 presigned URL을 반환한다.
     */
    @Override
    public String getImgUrlForAIWorker(String key) {
        return createPresignedUrl(key, Duration.ofHours(1));
    }

    @Override
    public String getSecureImgUrl(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("서명 URL 유효 기간이 유효하지 않습니다.");
        }

        return createPresignedUrl(key, ttl);
    }

    /**
     * S3 key를 주어진 기간 동안 접근 가능한 presigned URL로 변환한다.
     */
    private String createPresignedUrl(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
```

- [ ] **Step 4: `ProdImgUtils`를 고친다**

`getImgUrl`의 **CloudFront 분기는 그대로 둔다**(기존 눈·소변 진단이 쓰고 있다). `getSecureImgUrl`은 그 분기를 타지 않는 것이 요점이다.

```java
    @Override
    public String getImgUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        if (hasCloudFrontUrl()) {
            return buildCloudFrontUrl(key);
        }

        return createPresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * AI Worker는 원본 S3 이미지 접근이 필요하므로 presigned URL을 반환한다.
     */
    @Override
    public String getImgUrlForAIWorker(String key) {
        return createPresignedUrl(key, Duration.ofHours(1));
    }

    /**
     * CloudFront 분기를 절대 타지 않는다.
     * 개인정보가 찍힌 이미지를 무서명 URL로 공개하는 것을 원천 차단한다.
     */
    @Override
    public String getSecureImgUrl(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("서명 URL 유효 기간이 유효하지 않습니다.");
        }

        return createPresignedUrl(key, ttl);
    }

    private String createPresignedUrl(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }
```

- [ ] **Step 5: `TestImgUtils`에도 구현한다 (안 하면 테스트 컴파일 실패)**

`src/test/java/com/smarthealthdog/backend/TestImageConfig.java` 의 `TestImgUtils`에 추가한다.

```java
        @Override
        public String getSecureImgUrl(String key, java.time.Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("서명 URL 유효 기간이 유효하지 않습니다.");
            }

            String url = buildUrl(localStorageUrlPrefix, key);
            if (url == null) {
                return null;
            }

            // 테스트에서 TTL 이 실제로 전달되는지 확인할 수 있게 쿼리로 노출한다.
            return url + "?expiresIn=" + ttl.toSeconds();
        }
```

- [ ] **Step 6: 컴파일과 기존 테스트를 확인한다**

```bash
./gradlew build -x test
./gradlew test --tests 'SubmissionService*' --tests 'DiagnosisService*'
```

기대: 컴파일 성공, 기존 테스트 PASS (`SubmissionMapper`가 `getImgUrl`을 쓰는 동작이 그대로여야 한다).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/utils/ImgUtils.java \
        src/main/java/com/smarthealthdog/backend/utils/DevImgUtils.java \
        src/main/java/com/smarthealthdog/backend/utils/ProdImgUtils.java \
        src/test/java/com/smarthealthdog/backend/TestImageConfig.java
git commit -m "Feat: TTL을 지정하는 서명 URL 메서드 getSecureImgUrl 추가"
```

---

## Task 5: 제출 유형을 확장하고 Celery 분기를 막는다

**왜:** `SubmissionTypeEnum`에 두 상수를 추가한다. `submissions.type` 컬럼에는 CHECK 제약이 없으므로(`V42`가 `VARCHAR(255) NOT NULL DEFAULT 'EYE'`로만 추가) **마이그레이션이 필요 없다.** 그런데 `CeleryTaskSender.java:51-62`가 `if (type == EYE) {...} else {소변}` 구조라 **새 상수가 조용히 소변 분기로 빠진다.** 동기 경로는 Celery를 타지 않지만 방어적으로 막는다.

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/domain/SubmissionTypeEnum.java`
- Modify: `src/main/java/com/smarthealthdog/backend/utils/CeleryTaskSender.java:51-62`
- Test: `src/test/java/com/smarthealthdog/backend/utils/CeleryTaskSenderUT.java` (신규)

**Interfaces:**
- Consumes: 없음
- Produces: `SubmissionTypeEnum.HEALTH_CERTIFICATE`. Task 7·11·15·16이 쓴다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/utils/CeleryTaskSenderUT.java` 신규 생성.

```java
package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.RequestDiagnosisData;
import com.smarthealthdog.backend.repositories.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
public class CeleryTaskSenderUT {

    @InjectMocks
    private CeleryTaskSender celeryTaskSender;

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SubmissionRepository submissionRepository;

    @Test
    void sendDiagnosisTaskInBatch_진단서_유형은_큐로_보내지_않고_예외를_던진다() {
        Submission submission = mock(Submission.class);
        when(submission.getType()).thenReturn(SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // RequestDiagnosisData 의 순서는 (imageUrl, submission) 이다 — 뒤집지 않도록 주의한다.
        RequestDiagnosisData data = new RequestDiagnosisData("https://example.com/x.jpg", submission);

        assertThrows(
            RuntimeException.class,
            () -> celeryTaskSender.sendDiagnosisTaskInBatch(List.of(data))
        );
    }
}
```

> `CeleryTaskSender`는 `@Autowired` 생성자로 `StringRedisTemplate` · `SubmissionRepository` 둘만 받는다(`CeleryTaskSender.java:39-43`). `objectMapper`는 필드 초기화라 목이 필요 없다. `sendDiagnosisTaskInBatch`는 내부에서 `JsonProcessingException`을 `RuntimeException`으로 감싸므로, `IllegalStateException`도 `RuntimeException`으로 잡힌다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'CeleryTaskSenderUT'
```

기대: 컴파일 실패 — `HEALTH_CERTIFICATE` 상수가 없다.

- [ ] **Step 3: enum을 확장한다**

`domain/SubmissionTypeEnum.java`:

```java
package com.smarthealthdog.backend.domain;

public enum SubmissionTypeEnum {
    EYE,
    URINE,
    /** 건강검진표(진단서) OCR — 동기 처리. Celery 큐를 타지 않는다. */
    HEALTH_CERTIFICATE
}
```

- [ ] **Step 4: Celery 분기를 `switch`로 바꾼다**

`utils/CeleryTaskSender.java:51-62` 의 `if/else`를 교체한다.

```java
                        return switch (data.submission().getType()) {
                            case EYE -> generateCeleryMessageForEyeTest(
                                data.imageUrl(),
                                data.submission().getId(),
                                data.submission().getPet().getSpecies()
                            );
                            case URINE -> generateCeleryMessageForUrineTest(
                                data.imageUrl(),
                                data.submission().getId()
                            );
                            // 동기 처리 유형은 Celery 큐로 흘러갈 경로가 없어야 한다.
                            // 여기까지 왔다면 배치 쿼리나 상태 전이에 버그가 있다.
                            case HEALTH_CERTIFICATE -> throw new IllegalStateException(
                                "동기 처리 유형은 Celery 큐로 보낼 수 없습니다: "
                                    + data.submission().getType()
                            );
                        };
```

> `switch`를 **`default` 없이** 작성한다. 유형이 또 늘어나면 컴파일 에러로 드러나게 하는 것이 목적이다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'CeleryTaskSenderUT'
./gradlew build -x test
```

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/domain/SubmissionTypeEnum.java \
        src/main/java/com/smarthealthdog/backend/utils/CeleryTaskSender.java \
        src/test/java/com/smarthealthdog/backend/utils/CeleryTaskSenderUT.java
git commit -m "Feat: 제출 유형에 HEALTH_CERTIFICATE 추가하고 Celery 분기를 switch로 전환"
```

---

## Task 6: 에러 코드와 503 경로를 만든다

**왜:** `validation/ErrorCode`가 코드 단일 출처다. 그리고 **현재 503을 내는 경로가 아예 없고**, `InternalServerErrorException`은 선언만 되어 있어 전용 핸들러 없이 맨 아래 `Exception` catch-all로 떨어진다.

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/validation/ErrorCode.java`
- Create: `src/main/java/com/smarthealthdog/backend/exceptions/ServiceUnavailableException.java`
- Modify: `src/main/java/com/smarthealthdog/backend/handlers/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: 없음
- Produces:
    - `ErrorCode.OCR_RECOGNITION_FAILED` · `OCR_REQUIRED_FIELD_MISSING` · `NOT_A_HEALTH_CERTIFICATE` · `OCR_SERVICE_UNAVAILABLE` · `HEALTH_CERTIFICATE_NOT_FOUND`
    - `ServiceUnavailableException(ErrorCode)` → HTTP 503
    - `InternalServerErrorException` → HTTP 500 (전용 핸들러)
    - Task 13·14·15·17·18이 쓴다.

- [ ] **Step 1: `ErrorCode`에 상수 5개를 추가한다**

`validation/ErrorCode.java` — 마지막 상수 `INVALID_DATE_RANGE(...)` 의 세미콜론을 콤마로 바꾸고 아래를 붙인다.

```java
    INVALID_DATE_RANGE("날짜 범위가 올바르지 않습니다. 시작 날짜는 종료 날짜 이전이어야 합니다."),

    // 건강검진표 OCR 관련 오류
    OCR_RECOGNITION_FAILED("진단서를 인식하지 못했습니다. 더 밝고 선명한 사진으로 다시 시도해주세요."),
    OCR_REQUIRED_FIELD_MISSING("진단서에서 병명 또는 진단 연원일을 찾지 못했습니다."),
    NOT_A_HEALTH_CERTIFICATE("진단서 양식으로 보이지 않는 이미지입니다."),
    OCR_SERVICE_UNAVAILABLE("인식 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."),
    HEALTH_CERTIFICATE_NOT_FOUND("건강검진 기록을 찾을 수 없습니다.");
```

> 이미지 형식·용량 위반은 **기존 `INVALID_IMAGE`를 재사용**한다. 새 코드를 만들지 않는다.

- [ ] **Step 2: `ServiceUnavailableException`을 만든다**

`exceptions/ServiceUnavailableException.java` 신규 생성. 기존 `exceptions/*`와 같은 모양이다 — `CustomException`을 상속하고 `ErrorCode`만 나른다.

```java
package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * 외부 서비스(OCR·추론)에 일시적으로 연결할 수 없을 때 던진다.
 * GlobalExceptionHandler 가 503 으로 변환한다.
 */
public class ServiceUnavailableException extends CustomException {
    public ServiceUnavailableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

- [ ] **Step 3: 핸들러 2개를 추가한다**

`handlers/GlobalExceptionHandler.java` — `AuthorizationDeniedException` 핸들러와 맨 아래 `Exception` catch-all **사이에** 넣는다. catch-all보다 위에 있어야 한다.

```java
    // 503 에러 처리 — 외부 OCR·추론 서비스 장애
    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorMessage> handleServiceUnavailableException(ServiceUnavailableException e) {
        log.error("외부 서비스 연결 실패: code={}", e.getErrorCode().name(), e);

        ErrorMessage errorResponseBody = new ErrorMessage(
            List.of(e.getErrorCode().name()),
            List.of(e.getErrorCode().getMessage())
        );

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorResponseBody);
    }

    // 500 에러 처리 — 지금까지는 전용 핸들러가 없어 맨 아래 catch-all 로 떨어졌다.
    @ExceptionHandler(InternalServerErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorMessage> handleInternalServerErrorException(InternalServerErrorException e) {
        log.error("내부 서버 오류: code={}", e.getErrorCode().name(), e);

        ErrorMessage errorResponseBody = new ErrorMessage(
            List.of(e.getErrorCode().name()),
            List.of(e.getErrorCode().getMessage())
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponseBody);
    }
```

필요한 import를 추가한다.

```java
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
```

- [ ] **Step 4: 컴파일과 기존 테스트를 확인한다**

```bash
./gradlew build -x test
./gradlew test --tests 'ValidErrorCodeFinder*'
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/validation/ErrorCode.java \
        src/main/java/com/smarthealthdog/backend/exceptions/ServiceUnavailableException.java \
        src/main/java/com/smarthealthdog/backend/handlers/GlobalExceptionHandler.java
git commit -m "Feat: OCR 에러 코드 추가 및 503·500 전용 예외 핸들러 배선"
```

---

> **Phase 0 중간 지점.** Task 1~6까지가 "기존 코드를 손보는" 작업이고, Task 7부터는 새 구성요소가 들어간다. 여기서 전체 테스트를 한 번 돌려 회귀가 없는지 확인한다.
>
> ```bash
> ./gradlew test
> ```
>
> 기대: 기존 테스트 전부 PASS. (`naver.*` 환경변수 의존은 2026-10-06 `clients/naver/*` 삭제로 사라졌다.)

---

## Task 7: 시도 기준 빈도 제한을 만든다

**왜 — 세 문제를 한 메커니즘으로 푼다:**

1. **(a) 교차 간섭.** 기존 제한은 `getMostRecentSubmissionByPet(pet)` → `findTopByPetOrderBySubmittedAtDesc`를 쓰는데 **유형을 가리지 않는다.** `Submission`을 재사용하면 진단서를 올린 직후 눈 진단이 "요청이 너무 잦다"며 거부된다.
2. **(b) 비용.** 동기 엔드포인트는 요청 1건 = 유료 OCR 호출 1건 + 최대 15초 요청 스레드 점유 + `getBytes()`로 파일 전체를 힙에 유지다. 제한이 없으면 사용자 1명이 반복 호출만으로 비용과 스레드를 동시에 고갈시킨다.
3. **(c) 제한이 작동하지 않는 함정.** 기존 제한은 **`Submission` 행**의 `submittedAt`을 기준으로 판정한다. 그런데 "판별 실패 시 미저장" 정책상 **실패한 요청은 행을 만들지 않는다.** 즉 비용을 발생시키는 요청만 정확히 제한을 빠져나간다.

**명세와의 의도적 차이 — 반드시 읽는다.** SPEC §5.5(a)는 `SubmissionRepository.findTopByPetAndTypeOrderBySubmittedAtDesc(Pet, SubmissionTypeEnum)` 추가를 지시한다. **이 계획은 그 메서드를 추가하지 않는다.** 그 메서드는 (a)만 고치고 (c)는 그대로 남기기 때문이다 — 여전히 `Submission` 행을 세므로 실패 요청은 세지 않는다. 대신 **(pet, type)별 마지막 시도 시각을 담는 전용 테이블**을 두어 (a)(b)(c)를 한 번에 해결한다. SPEC §5.5(c)가 제시한 세 선택지 중 "별도 시도 기록 테이블"을 택한 것이며, Redis 안을 피한 이유는 **`RedisConfig`가 `@Profile("dev")` 전용**이라 prod에 Redis 빈을 노출하는 선행 작업이 필요하고, 그 작업이 이 기능의 범위를 넘기 때문이다.

**마이그레이션 번호 차이.** SPEC은 모든 DDL을 하나에 담지만, 이 계획은 **작업당 마이그레이션 하나**로 쪼갠다 — `V53`은 빈도 제한 테이블, `V55`는 진단서 테이블. (`V54`는 2026-10-06 병원 검색 권한 제거가 선점했다.) 작업 단위로 되돌릴 수 있고, Task 7만 먼저 배포해 기존 눈·소변의 교차 간섭 버그를 선행 수정할 수 있다.

**Files:**
- Create: `src/main/resources/db/migration/V53__create_diagnosis_attempts.sql`
- Create: `src/main/java/com/smarthealthdog/backend/domain/DiagnosisAttempt.java`
- Create: `src/main/java/com/smarthealthdog/backend/repositories/DiagnosisAttemptRepository.java`
- Create: `src/main/java/com/smarthealthdog/backend/services/DiagnosisAttemptLimiter.java`
- Modify: `src/main/java/com/smarthealthdog/backend/services/AIDiagnosisClientService.java:50-59, 88-97`
- Modify: `src/main/resources/application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml`
- Test: `src/test/java/com/smarthealthdog/backend/services/DiagnosisAttemptLimiterUT.java` (신규)
- Test: `src/test/java/com/smarthealthdog/backend/services/AIDiagnosisClientServiceUT.java:75-90` (기존 수정)

**Interfaces:**
- Consumes: `SubmissionTypeEnum.HEALTH_CERTIFICATE` (Task 5), `ErrorCode.REQUEST_TOO_FREQUENT` (기존)
- Produces:
    - `DiagnosisAttemptLimiter.checkAndRecordAttempt(Pet pet, SubmissionTypeEnum type) → void` — 위반 시 `InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT)`. 통과하면 **호출 즉시** 시도 시각을 기록한다.
    - 설정 키 `inference-service.interval.seconds`(기존, pet+type 단위), `ocr.user-interval.seconds`(신규, 사용자 단위)
    - Task 15·16·21이 동기 흐름 3단계에서 이 메서드를 호출한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/services/DiagnosisAttemptLimiterUT.java` 신규 생성.

```java
package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class DiagnosisAttemptLimiterUT {

    @InjectMocks
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Mock private DiagnosisAttemptRepository diagnosisAttemptRepository;
    @Captor private ArgumentCaptor<DiagnosisAttempt> attemptCaptor;

    private Pet pet;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "petIntervalSeconds", 60);
        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "userIntervalSeconds", 10);

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);

        pet = mock(Pet.class);
        when(pet.getId()).thenReturn(3L);
        when(pet.getOwner()).thenReturn(owner);
    }

    @Test
    void 기록이_없으면_통과하고_시도를_기록한다() {
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.empty());
        when(diagnosisAttemptRepository.findLatestAttemptForUser(anyLong(), anyList()))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        verify(diagnosisAttemptRepository).save(attemptCaptor.capture());
        assertEquals(3L, attemptCaptor.getValue().getPetId());
        assertEquals(7L, attemptCaptor.getValue().getUserId());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, attemptCaptor.getValue().getType());
    }

    @Test
    void 같은_유형을_간격_내에_다시_요청하면_REQUEST_TOO_FREQUENT를_던지고_기록하지_않는다() {
        DiagnosisAttempt recent = DiagnosisAttempt.builder()
            .petId(3L)
            .userId(7L)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .lastAttemptedAt(Instant.now().minusSeconds(5))
            .build();

        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.of(recent));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(diagnosisAttemptRepository, never()).save(any(DiagnosisAttempt.class));
    }

    @Test
    void 다른_유형의_최근_요청은_간섭하지_않는다() {
        // 5.5(a): 진단서를 올린 직후 눈 진단이 거부되던 교차 간섭이 사라져야 한다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.EYE))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.EYE)
        );
    }

    @Test
    void 유료_유형은_반려동물을_바꿔도_사용자_단위_간격에_걸린다() {
        // 검토 집중 지점 5: pet 단위 제한만 있으면 반려동물을 여러 마리 등록해 우회할 수 있다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.empty());
        when(diagnosisAttemptRepository.findLatestAttemptForUser(
                7L,
                List.of(SubmissionTypeEnum.HEALTH_CERTIFICATE)
        )).thenReturn(Optional.of(Instant.now().minusSeconds(3)));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(diagnosisAttemptRepository, never()).save(any(DiagnosisAttempt.class));
    }

    @Test
    void 무료_유형에는_사용자_단위_간격을_적용하지_않는다() {
        // 눈·소변은 큐에 적재만 하므로 유료 호출이 아니다. 사용자 단위 제한을 걸지 않는다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.URINE))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.URINE)
        );

        verify(diagnosisAttemptRepository, never()).findLatestAttemptForUser(anyLong(), anyList());
    }

    @Test
    void 간격이_지난_기록은_새_행을_만들지_않고_갱신한다() {
        DiagnosisAttempt old = DiagnosisAttempt.builder()
            .petId(3L)
            .userId(7L)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .lastAttemptedAt(Instant.now().minusSeconds(600))
            .build();

        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.of(old));
        when(diagnosisAttemptRepository.findLatestAttemptForUser(anyLong(), anyList()))
            .thenReturn(Optional.of(Instant.now().minusSeconds(600)));

        diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        verify(diagnosisAttemptRepository).save(attemptCaptor.capture());
        // 테이블이 무한히 자라지 않도록 기존 행 인스턴스를 그대로 저장해야 한다.
        assertEquals(old, attemptCaptor.getValue());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'DiagnosisAttemptLimiterUT'
```

기대: 컴파일 실패 — `DiagnosisAttempt`, `DiagnosisAttemptRepository`, `DiagnosisAttemptLimiter`가 없다.

- [ ] **Step 3: 마이그레이션을 쓴다**

`src/main/resources/db/migration/V53__create_diagnosis_attempts.sql` 신규 생성. DDL 스타일은 `V51`을 따른다 — 대문자 키워드, `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, `fk_<table>_<target>` 명명, `TIMESTAMP WITH TIME ZONE`, 명시적 `CREATE INDEX idx_<table>_<col>`.

```sql
-- 진단 요청 빈도 제한 전용 테이블.
--
-- Submission 행을 기준으로 빈도를 세면, "판별 실패 시 미저장" 정책 때문에
-- 실패한 요청이 행을 남기지 않아 제한을 전부 빠져나간다.
-- 비용을 발생시키는 요청만 정확히 제한을 통과하는 셈이 되므로,
-- 성공 실패와 무관하게 남는 "시도" 를 별도로 기록한다.
--
-- (pet_id, type) 당 한 행만 유지한다. 갱신만 하므로 테이블이 자라지 않는다.
CREATE TABLE diagnosis_attempts (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    last_attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_diagnosis_attempts_pet
        FOREIGN KEY (pet_id)
        REFERENCES pets(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_diagnosis_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_diagnosis_attempts_pet_type UNIQUE (pet_id, type)
);

CREATE INDEX idx_diagnosis_attempts_user_id
    ON diagnosis_attempts (user_id);

CREATE INDEX idx_diagnosis_attempts_last_attempted_at
    ON diagnosis_attempts (last_attempted_at);
```

> **실행 전 확인:** `users` 테이블명이 맞는지 확인한다 — `grep -rn "users" src/main/resources/db/migration/V1__*.sql | head`. `User` 엔티티의 `@Table(name = ...)`을 봐도 된다. 다르면 FK 참조 대상을 맞춘다.

- [ ] **Step 4: 엔티티와 리포지토리를 만든다**

`domain/DiagnosisAttempt.java` — `Pet`·`User` 연관을 걸지 않고 **ID만** 보관한다. 빈도 검사는 ID만 필요하고, 연관을 걸면 제한 검사가 불필요한 조인을 끌고 온다. 엔티티 스타일은 `UrineMeasurement`를 따른다.

```java
package com.smarthealthdog.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 진단 요청 빈도 제한을 위한 "시도" 기록.
 *
 * Submission 과 달리 판별 성공 여부와 무관하게 남는다.
 * (petId, type) 당 한 행만 유지하며 갱신만 한다.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "diagnosis_attempts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_diagnosis_attempts_pet_type",
        columnNames = {"pet_id", "type"}
    )
)
public class DiagnosisAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    private SubmissionTypeEnum type;

    @Column(name = "last_attempted_at", nullable = false)
    private Instant lastAttemptedAt;
}
```

`repositories/DiagnosisAttemptRepository.java`:

```java
package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;

@Repository
public interface DiagnosisAttemptRepository extends JpaRepository<DiagnosisAttempt, Long> {

    Optional<DiagnosisAttempt> findByPetIdAndType(Long petId, SubmissionTypeEnum type);

    /**
     * 사용자가 지정한 유형들에 대해 가장 최근에 시도한 시각을 반환한다.
     * 반려동물을 여러 마리 등록해 pet 단위 제한을 우회하는 것을 막는다.
     */
    @Query("SELECT MAX(a.lastAttemptedAt) FROM DiagnosisAttempt a WHERE a.userId = :userId AND a.type IN :types")
    Optional<Instant> findLatestAttemptForUser(
        @Param("userId") Long userId,
        @Param("types") List<SubmissionTypeEnum> types
    );
}
```

- [ ] **Step 5: 제한기를 만든다**

`services/DiagnosisAttemptLimiter.java`:

```java
package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 진단 요청 빈도를 "시도" 기준으로 제한한다.
 *
 * Submission 행 기준이 아닌 이유:
 * 판별 실패 요청은 Submission 행을 만들지 않으므로,
 * 행을 세면 비용을 발생시키는 요청만 제한을 빠져나간다.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisAttemptLimiter {

    private final DiagnosisAttemptRepository diagnosisAttemptRepository;

    /** (반려동물, 유형) 단위 최소 간격. 기존 눈 소변 제한과 같은 설정 키를 쓴다. */
    @Value("${inference-service.interval.seconds}")
    private int petIntervalSeconds;

    /** 사용자 단위 최소 간격. 유료 외부 API를 호출하는 유형에만 적용한다. */
    @Value("${ocr.user-interval.seconds}")
    private int userIntervalSeconds;

    /**
     * 외부 유료 API를 요청당 1회 호출하는 유형.
     * 눈 소변은 큐에 적재만 하므로 여기에 넣지 않는다.
     */
    private static final List<SubmissionTypeEnum> PAID_SYNC_TYPES = List.of(
        SubmissionTypeEnum.HEALTH_CERTIFICATE
    );

    /**
     * 빈도 제한을 검사하고, 통과하면 즉시 시도를 기록한다.
     *
     * 반드시 외부 API 호출보다 앞에서 불러야 한다.
     * 검증 호출 뒤에 두면 제한이 비용을 막지 못한다.
     *
     * @param pet 대상 반려동물 (owner 가 로드되어 있어야 한다)
     * @param type 제출 유형
     * @throws InvalidRequestDataException 간격 내 재요청인 경우 {@code REQUEST_TOO_FREQUENT}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecordAttempt(Pet pet, SubmissionTypeEnum type) {
        if (pet == null || type == null) {
            throw new IllegalArgumentException("Pet 과 type 은 null 일 수 없습니다.");
        }

        Long petId = pet.getId();
        Long userId = pet.getOwner().getId();
        Instant now = Instant.now();

        // 1. (반려동물, 유형) 단위 간격
        Optional<DiagnosisAttempt> existing =
            diagnosisAttemptRepository.findByPetIdAndType(petId, type);

        if (existing.isPresent() && isWithin(existing.get().getLastAttemptedAt(), now, petIntervalSeconds)) {
            throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
        }

        // 2. 사용자 단위 간격 — 유료 유형만. 반려동물을 바꿔 우회하는 것을 막는다.
        if (PAID_SYNC_TYPES.contains(type)) {
            Optional<Instant> latestForUser =
                diagnosisAttemptRepository.findLatestAttemptForUser(userId, PAID_SYNC_TYPES);

            if (latestForUser.isPresent() && isWithin(latestForUser.get(), now, userIntervalSeconds)) {
                throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
            }
        }

        // 3. 시도 기록 — 판별 결과를 보기 전에 기록한다.
        //    새 행을 만들지 않고 기존 행을 갱신해 테이블이 자라지 않게 한다.
        DiagnosisAttempt attempt = existing.orElseGet(() ->
            DiagnosisAttempt.builder()
                .petId(petId)
                .userId(userId)
                .type(type)
                .build()
        );
        attempt.setLastAttemptedAt(now);

        diagnosisAttemptRepository.save(attempt);
    }

    private boolean isWithin(Instant last, Instant now, int intervalSeconds) {
        if (last == null) {
            return false;
        }

        return Duration.between(last, now).getSeconds() < intervalSeconds;
    }
}
```

> **`REQUIRES_NEW`를 쓰는 이유.** 동기 흐름의 진입 메서드는 비트랜잭션이고(Task 15), 이후 OCR이 실패해 요청이 400으로 끝나도 **시도 기록은 남아야 한다.** 호출자 트랜잭션에 참여하면 롤백에 함께 휩쓸려 (c) 함정이 되살아난다.
>
> **동시 요청.** 같은 (pet, type)으로 두 요청이 동시에 들어오면 둘 다 통과할 수 있고, 두 번째 `save`가 `uq_diagnosis_attempts_pet_type` 위반으로 실패할 수 있다. 목적이 반복 남용 억제이므로 정확히-한-번은 필요하지 않다. 비관적 락은 넣지 않는다 — 제한 검사가 요청 경로의 맨 앞이라 락 경합이 그대로 응답 지연이 된다.

- [ ] **Step 6: 설정 키를 세 yml에 넣는다**

`application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml` 모두에 추가한다. Task 13 이후 `ocr:` 블록이 더 커지므로 같은 블록에 둔다.

```yaml
ocr:
  # 사용자 단위 최소 요청 간격(초). 유료 OCR 추론 호출에만 적용된다.
  # 반려동물 단위 제한(inference-service.interval.seconds)만으로는
  # 반려동물을 여러 마리 등록해 우회할 수 있다.
  user-interval:
    seconds: ${OCR_USER_INTERVAL_SECONDS:30}
```

- [ ] **Step 7: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'DiagnosisAttemptLimiterUT'
```

기대: 6개 테스트 PASS.

- [ ] **Step 8: `AIDiagnosisClientService`의 중복 블록 2개를 교체한다**

`services/AIDiagnosisClientService.java` — `performEyeDiagnosis`·`performUrineDiagnosis`에 **똑같이 복사된** 빈도 제한 블록(`:50-59`, `:88-97`)을 제한기 호출 한 줄로 바꾼다.

```java
// 추가
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;   // 이미 있다면 생략

// 삭제 가능 (더 이상 쓰지 않는다)
// import java.time.Duration;
// import java.time.Instant;
// import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class AIDiagnosisClientService {
    private final FileUploadService fileUploadService;
    private final PetService petService;
    private final SubmissionService submissionService;
    private final DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    // 삭제: @Value("${inference-service.interval.seconds}") private int inferenceIntervalSeconds;
```

두 메서드에서 `try { ... getMostRecentSubmissionByPet ... } catch (ResourceNotFoundException e) { ... }` 블록 전체를 지우고 한 줄로 바꾼다.

```java
        // 빈도 제한 — 유형별로 분리되어 있어 진단서 업로드가 눈 진단을 막지 않는다.
        diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.EYE);
```

`performUrineDiagnosis`는 `SubmissionTypeEnum.URINE`을 넘긴다.

> **동작 변화 2가지 — PR 설명에 적는다.**
> 1. 유형별로 분리되어 눈 직후 소변 요청이 더 이상 거부되지 않는다. (SPEC §5.5a 수정)
> 2. 업로드가 나중에 실패해도 시도 기록이 남는다. 기존에는 `Submission` 행 기준이라 실패 시 제한이 걸리지 않았다.

- [ ] **Step 9: 깨진 기존 테스트를 고친다**

`AIDiagnosisClientServiceUT.java:80`이 `submissionService.getMostRecentSubmissionByPet(mockPet)`을 목으로 둔다. 그 경로가 사라졌으므로 제한기 목으로 바꾼다.

```java
    @Mock
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Test
    void performEyeDiagnosis_ShouldThrowInvalidRequestDataException_WhenRequestedTooFrequently() {
        User mockOwner = mock(User.class);
        when(mockOwner.getId()).thenReturn(1L);

        Pet mockPet = mock(Pet.class);
        when(mockPet.getOwner()).thenReturn(mockOwner);
        when(petService.get(1L)).thenReturn(mockPet);

        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(mockPet, SubmissionTypeEnum.EYE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 1L)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(fileUploadService, never()).updateDiagnosisImage(any(), any());
    }
```

> `grep -rn "getMostRecentSubmissionByPet" src/test src/main` 으로 전부 찾아 같은 방식으로 바꾼다. **`getMostRecentSubmissionByPet` 자체는 `SubmissionService`에 남겨둔다** — `src/main`에 다른 호출부가 없다면 Task 19에서 제거를 검토한다. 이 작업에서 지우지 않는 이유는, 메서드 제거가 이 작업의 테스트 범위를 넘어 다른 회귀를 만들 수 있기 때문이다.

- [ ] **Step 10: 관련 테스트를 돌린다**

```bash
./gradlew test --tests 'DiagnosisAttemptLimiterUT' --tests 'AIDiagnosisClientService*' --tests 'SubmissionService*'
```

기대: PASS.

- [ ] **Step 11: 커밋**

```bash
git add src/main/resources/db/migration/V53__create_diagnosis_attempts.sql \
        src/main/java/com/smarthealthdog/backend/domain/DiagnosisAttempt.java \
        src/main/java/com/smarthealthdog/backend/repositories/DiagnosisAttemptRepository.java \
        src/main/java/com/smarthealthdog/backend/services/DiagnosisAttemptLimiter.java \
        src/main/java/com/smarthealthdog/backend/services/AIDiagnosisClientService.java \
        src/main/resources/application-dev.yml src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml \
        src/test/java/com/smarthealthdog/backend/services/DiagnosisAttemptLimiterUT.java \
        src/test/java/com/smarthealthdog/backend/services/AIDiagnosisClientServiceUT.java
git commit -m "Feat: 시도 기준 진단 빈도 제한 도입 및 유형별 교차 간섭 제거"
```

---
## Task 8: `GET /api/submissions/{id}/image` — 모든 제출 유형 공통 이미지 엔드포인트

**왜 지금:** 진단서 이미지는 **`photoUrl`을 응답에 담지 않는다**(SPEC §2.6, §9.2). 무서명 URL이 새어나갈 경로를 원천 차단하기 위해서다. 그래서 이미지를 받는 길이 이 엔드포인트뿐이고, 조회 매퍼(Task 17)보다 먼저 존재해야 한다. 기존 눈·소변 제출에도 공통으로 동작한다.

**설계 결정:** 리다이렉트(`302`)가 아니라 JSON으로 돌려준다. 프론트가 만료 시각을 알고 재요청 시점을 판단해야 하기 때문이다(SPEC §4.6).

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/ImageUrlResponse.java`
- Modify: `src/main/java/com/smarthealthdog/backend/services/SubmissionService.java`
- Modify: `src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java`
- Modify: `src/main/resources/application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml`
- Test: `src/test/java/com/smarthealthdog/backend/services/SubmissionServiceUT.java` (기존에 추가)

**Interfaces:**
- Consumes: `ImgUtils.getSecureImgUrl(key, ttl)` (Task 4), `SubmissionService.getSubmissionByIdAndOwnerId(UUID, Long)` (기존 — **소유권 검증이 내장**되어 있어 별도 헬퍼가 필요 없다)
- Produces:
    - `ImageUrlResponse(String imageUrl, long expiresIn)` — JSON은 `image_url` / `expires_in`
    - `SubmissionService.getSecureImageUrl(UUID submissionId, Long userId) → ImageUrlResponse`
    - 설정 키 `health-certificate.image.presigned-url-expiration-seconds`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SubmissionServiceUT.java`에 추가한다. **검토 집중 지점 1**(`photoUrl`이 빈 문자열인 행)을 여기서 못박는다.

```java
// 필요한 import
// import java.time.Duration;
// import com.smarthealthdog.backend.utils.ImgUtils;
// import com.smarthealthdog.backend.dto.health.ImageUrlResponse;
// import org.springframework.test.util.ReflectionTestUtils;

@Mock
private ImgUtils imgUtils;

@Test
void getSecureImageUrl_서명URL과_만료초를_반환한다() {
    ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

    UUID id = UUID.randomUUID();
    Submission submission = mock(Submission.class);
    when(submission.getPhotoUrl()).thenReturn("health-certificates/abc.jpg");
    when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
        .thenReturn(Optional.of(submission));
    when(imgUtils.getSecureImgUrl("health-certificates/abc.jpg", Duration.ofSeconds(300)))
        .thenReturn("https://s3.example.com/health-certificates/abc.jpg?X-Amz-Expires=300");

    ImageUrlResponse response = submissionService.getSecureImageUrl(id, 7L);

    assertEquals("https://s3.example.com/health-certificates/abc.jpg?X-Amz-Expires=300",
                 response.imageUrl());
    assertEquals(300L, response.expiresIn());
}

@Test
void getSecureImageUrl_이미지가_아직_없는_행이면_404를_낸다() {
    // 검토 집중 지점 1: createSubmission 은 photoUrl="" 로 행을 만든다.
    // 업로드 전/실패 행에 이 엔드포인트를 호출하면 null 서명 URL 이 나가면 안 된다.
    ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

    UUID id = UUID.randomUUID();
    Submission submission = mock(Submission.class);
    when(submission.getPhotoUrl()).thenReturn("");
    when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
        .thenReturn(Optional.of(submission));

    ResourceNotFoundException e = assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSecureImageUrl(id, 7L)
    );

    assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    verify(imgUtils, never()).getSecureImgUrl(any(), any());
}

@Test
void getSecureImageUrl_삭제된_제출이면_404를_낸다() {
    ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

    UUID id = UUID.randomUUID();
    Submission submission = mock(Submission.class);
    when(submission.getStatus()).thenReturn(SubmissionStatus.DELETED);
    when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
        .thenReturn(Optional.of(submission));

    assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSecureImageUrl(id, 7L)
    );
}

@Test
void getSecureImageUrl_타인의_제출이면_404를_낸다() {
    // findByIdWithPetAndUserAndOwnerId 가 userId 조건을 포함하므로 빈 Optional 이 온다.
    UUID id = UUID.randomUUID();
    when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 99L))
        .thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSecureImageUrl(id, 99L)
    );
}
```

> **주의:** `SubmissionServiceUT`의 기존 `@Mock` 목록에 `imgUtils`를 넣으면 `@InjectMocks`가 `SubmissionService`의 생성자 인자 수와 맞아야 한다. Step 3에서 의존성을 추가한 뒤에야 주입된다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceUT'
```

기대: 컴파일 실패 — `getSecureImageUrl` 과 `ImageUrlResponse` 가 없다.

- [ ] **Step 3: 응답 DTO를 만든다**

`dto/health/ImageUrlResponse.java` 신규 생성. 이 저장소에는 전역 네이밍 전략이 없어 기본 camelCase가 적용되므로, 명세의 snake_case를 맞추려면 `@JsonProperty`를 붙인다 (`dto/diagnosis/celery/*`, `dto/KakaoTokenResponse`가 같은 방식이다).

```java
package com.smarthealthdog.backend.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 단기 서명 URL 응답.
 *
 * 302 리다이렉트가 아니라 JSON 으로 돌려준다.
 * 프론트가 만료 시점을 알고 재요청 타이밍을 판단해야 하기 때문이다.
 */
public record ImageUrlResponse(
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("expires_in") long expiresIn
) {}
```

- [ ] **Step 4: `SubmissionService`에 메서드를 추가한다**

`services/SubmissionService.java` — 의존성과 설정을 추가한다.

```java
// 추가 import
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import com.smarthealthdog.backend.dto.health.ImageUrlResponse;
import com.smarthealthdog.backend.utils.ImgUtils;

@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final LanguageRepository languageRepository;
    private final SubmissionRepository submissionRepository;
    private final ConditionService conditionService;
    private final DiagnosisService diagnosisService;
    private final UrineMeasurementService urineMeasurementService;
    private final SubmissionMapper submissionMapper;
    private final ImgUtils imgUtils;

    /** 이미지 서명 URL 유효 기간(초). */
    @Value("${health-certificate.image.presigned-url-expiration-seconds}")
    private int imageUrlExpirationSeconds;
```

클래스 하단에 메서드를 추가한다.

```java
    /**
     * 제출 이미지에 접근할 단기 서명 URL을 발급한다.
     *
     * 모든 제출 유형에 공통으로 동작한다.
     * 진단서 이미지는 이 경로 외에 접근할 방법이 없다 —
     * getImgUrl 은 CloudFront 무서명 URL 을 반환할 수 있어 절대 쓰지 않는다.
     *
     * @param submissionId 제출 ID
     * @param userId 요청자(소유자) 내부 ID
     * @return 서명 URL과 만료 초
     * @throws ResourceNotFoundException 제출이 없거나 타인 소유이거나 삭제됐거나
     *                                  아직 이미지가 업로드되지 않은 경우 {@code RESOURCE_NOT_FOUND}
     */
    public ImageUrlResponse getSecureImageUrl(UUID submissionId, Long userId) {
        if (submissionId == null || userId == null) {
            throw new IllegalArgumentException("Submission ID 와 User ID 는 null 일 수 없습니다.");
        }

        // 소유권 검증이 내장된 조회를 재사용한다. 타인 소유면 빈 Optional 이다.
        Submission submission = getSubmissionByIdAndOwnerId(submissionId, userId);

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // createSubmission 은 photoUrl="" 로 행을 만든다.
        // 업로드 전이거나 업로드가 실패한 행에 서명 URL 을 발급하면
        // null 또는 깨진 URL 이 프론트로 나간다.
        String photoKey = submission.getPhotoUrl();
        if (photoKey == null || photoKey.isBlank()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Duration ttl = Duration.ofSeconds(imageUrlExpirationSeconds);

        return new ImageUrlResponse(
            imgUtils.getSecureImgUrl(photoKey, ttl),
            imageUrlExpirationSeconds
        );
    }
```

- [ ] **Step 5: 설정 키를 세 yml에 넣는다**

```yaml
health-certificate:
  image:
    presigned-url-expiration-seconds: ${HEALTH_CERTIFICATE_IMAGE_URL_EXPIRATION_SECONDS:300}
```

- [ ] **Step 6: 컨트롤러에 엔드포인트를 추가한다**

`controllers/SubmissionController.java` — 기존 `@GetMapping("/{id}/eye")`와 같은 모양으로 추가한다. 컨트롤러는 얇게 유지한다.

```java
    /** 제출 이미지 단기 서명 URL. 모든 제출 유형에 공통으로 동작한다. */
    @GetMapping("/{id}/image")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<ImageUrlResponse> getSubmissionImageUrl(
            @PathVariable("id") UUID submissionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(submissionService.getSecureImageUrl(submissionId, userId));
    }
```

import를 추가한다.

```java
import com.smarthealthdog.backend.dto.health.ImageUrlResponse;
```

- [ ] **Step 7: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceUT' --tests 'SubmissionServiceTest'
```

기대: PASS. `SubmissionService`의 생성자가 바뀌었으므로 `@SpringBootTest` 쪽에서 `ImgUtils` 빈이 주입되는지(테스트 프로파일은 `TestImageConfig.TestImgUtils`) 함께 확인된다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/dto/health/ImageUrlResponse.java \
        src/main/java/com/smarthealthdog/backend/services/SubmissionService.java \
        src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java \
        src/main/resources/application-dev.yml src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml \
        src/test/java/com/smarthealthdog/backend/services/SubmissionServiceUT.java
git commit -m "Feat: 제출 이미지 단기 서명 URL 엔드포인트 추가"
```

---

## Task 9: 목록 조회에 `type` 필터를 더하고 `DELETED` 비대칭을 고친다

**왜:** 목록·삭제 엔드포인트를 **새로 만들지 않는다.** `getSubmissionsByPetId`·`getSubmissionsByUserId`가 페이징 상한(15), 정렬 속성 화이트리스트, 날짜 범위 검증을 이미 갖추고 있다. 두 `Specification`에 선택적 `type` 동등 조건만 더하면 유형별 목록이 공짜로 생긴다.

함께 고칠 기존 버그: `filterPetSubmissions`는 `filterUserSubmissions`와 달리 **`DELETED`를 제외하지 않는다.** 즉 반려동물별 목록에는 소프트 삭제한 제출이 그대로 보인다. 같은 파일 안에서 같은 목적의 두 Specification이 다르게 동작하는 비대칭이다.

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/repositories/SubmissionSpecifications.java:76-128, 139-196`
- Modify: `src/main/java/com/smarthealthdog/backend/services/SubmissionService.java` (목록 2개 메서드 시그니처)
- Modify: `src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java` (목록 2개 엔드포인트)
- Test: `src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java` (기존에 추가 — Specification은 H2 통합 테스트로 검증한다)

**Interfaces:**
- Consumes: `SubmissionTypeEnum.HEALTH_CERTIFICATE` (Task 5)
- Produces:
    - `SubmissionSpecifications.filterUserSubmissions(Long userId, SubmissionTypeEnum type, Instant submittedFrom, Instant submittedTo, Instant completedFrom, Instant completedTo)`
    - `SubmissionSpecifications.filterPetSubmissions(Long userId, Long petId, SubmissionTypeEnum type, Instant submittedFrom, Instant submittedTo, Instant completedFrom, Instant completedTo)`
    - `SubmissionService.getSubmissionsByUserId(...)` / `getSubmissionsByPetId(...)` 에 `SubmissionTypeEnum type` 인자 추가 (`petId`/`userId` 바로 뒤)
    - 쿼리 파라미터 `?type=HEALTH_CERTIFICATE`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SubmissionServiceTest.java`에 추가한다. H2에 실제 행을 넣고 Specification이 걸러내는지 본다 — `Specification`은 단위 테스트로 검증할 수 없다.

```java
@Test
void getSubmissionsByPetId_type_필터가_해당_유형만_반환한다() {
    // given: 같은 반려동물에 눈 1건, 진단서 1건
    Submission eye = submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.EYE)
    );
    Submission cert = submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
    );

    // when
    SubmissionPage certOnly = submissionService.getSubmissionsByPetId(
        testPet.getId(), testOwner.getId(),
        SubmissionTypeEnum.HEALTH_CERTIFICATE,
        null, null, null, null,
        PageRequest.of(0, 15)
    );

    // then
    assertEquals(1L, certOnly.getTotalElements());
    assertEquals(cert.getId().toString(), certOnly.getSubmissions().get(0).getId());
}

@Test
void getSubmissionsByPetId_type이_null이면_모든_유형을_반환한다() {
    submissionRepository.save(submissionService.createSubmission(testPet, SubmissionTypeEnum.EYE));
    submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
    );

    SubmissionPage all = submissionService.getSubmissionsByPetId(
        testPet.getId(), testOwner.getId(),
        null,
        null, null, null, null,
        PageRequest.of(0, 15)
    );

    assertEquals(2L, all.getTotalElements());
}

@Test
void getSubmissionsByPetId_삭제된_제출을_제외한다() {
    // 기존 버그: filterPetSubmissions 는 DELETED 를 제외하지 않았다.
    Submission kept = submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.EYE)
    );
    Submission removed = submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.EYE)
    );
    submissionService.deleteSubmissionById(removed.getId(), testOwner.getId());

    SubmissionPage page = submissionService.getSubmissionsByPetId(
        testPet.getId(), testOwner.getId(),
        null, null, null, null, null,
        PageRequest.of(0, 15)
    );

    assertEquals(1L, page.getTotalElements());
    assertEquals(kept.getId().toString(), page.getSubmissions().get(0).getId());
}
```

> **실행 전 확인:** `SubmissionServiceTest`의 기존 픽스처 이름(`testPet`, `testOwner`)과 `SubmissionPage`의 접근자 이름(`getTotalElements`, `getSubmissions`)을 `cat`으로 확인하고 맞춘다. `SubmissionPage`는 `@Value` 레코드형일 수 있으니 생성자·게터 모양을 그대로 따른다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest'
```

기대: 컴파일 실패 — `getSubmissionsByPetId`가 `type` 인자를 받지 않는다.

- [ ] **Step 3: 두 Specification에 `type` 조건을 더하고 `DELETED`를 맞춘다**

`repositories/SubmissionSpecifications.java` — import를 추가한다.

```java
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
```

`filterUserSubmissions`의 시그니처에 `type`을 넣고, 예측 블록에 조건을 추가한다.

```java
    public static Specification<Submission> filterUserSubmissions(
        Long userId,
        SubmissionTypeEnum type,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("pet").get("owner").get("id"), userId));

            // 2. Dynamic Filter: 제출 유형 (선택)
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // ... 기존 submittedAt / completedAt 범위 블록 그대로 ...
```

`filterPetSubmissions`도 같은 위치에 `type` 조건을 넣고, **빠져 있던 `DELETED` 제외 조건을 추가한다.**

```java
    public static Specification<Submission> filterPetSubmissions(
        Long userId,
        Long petId,
        SubmissionTypeEnum type,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("pet").get("owner").get("id"), userId));
            predicates.add(cb.equal(root.get("pet").get("id"), petId));

            // 3. Dynamic Filter: 제출 유형 (선택)
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // ... 기존 submittedAt / completedAt 범위 블록 그대로 ...

            // filterUserSubmissions 와 동일하게 소프트 삭제된 제출을 제외한다.
            // 이 조건이 빠져 있어 반려동물별 목록에만 삭제된 제출이 보이는 버그가 있었다.
            predicates.add(cb.notEqual(root.get("status"), SubmissionStatus.DELETED));

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                 root.fetch("pet").fetch("owner");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
```

> **`filterSubmissions`(첫 번째 메서드)는 건드리지 않는다.** `grep -rn "filterSubmissions(" src/main`으로 호출부를 확인하고, 0건이면 Task 19에서 제거를 검토한다. 이 작업에서 지우지 않는 이유는 삭제가 이 작업의 테스트 범위를 넘기 때문이다.

- [ ] **Step 4: 서비스 메서드 시그니처를 넓힌다**

`services/SubmissionService.java` — `getSubmissionsByPetId`에 `type`을 추가한다(위치는 `userId` 바로 뒤). Javadoc에도 한 줄 넣는다.

```java
    /**
     * ...
     * @param type 제출 유형 필터 (null 이면 모든 유형)
     * ...
     */
    public SubmissionPage getSubmissionsByPetId(
        Long petId,
        Long userId,
        SubmissionTypeEnum type,
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo,
        Pageable pageable
    ) {
        // ... 기존 검증 블록(페이지 크기 정렬 날짜 범위 소유권) 전부 그대로 ...

        Page<Submission> page = submissionRepository.findAll(
            SubmissionSpecifications.filterPetSubmissions(
                userId,
                petId,
                type,
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo
            ),
            pageable
        );

        return submissionMapper.toSubmissionPage(page);
    }
```

`getSubmissionsByUserId`도 같게 `type`을 `userId` 바로 뒤에 넣고 `filterUserSubmissions` 호출에 넘긴다.

- [ ] **Step 5: 컨트롤러 두 곳에 파라미터를 더한다**

`controllers/SubmissionController.java` — `getSubmissionList`와 `getSubmissionsByPetId`에 추가한다. `SubmissionTypeEnum`으로 바인딩하므로 잘못된 값은 `MethodArgumentTypeMismatchException` → 기존 핸들러가 400 `INVALID_PARAMETER_TYPE`으로 변환한다. 별도 검증이 필요 없다.

```java
            @RequestParam(value = "type", required = false) SubmissionTypeEnum type,
```

서비스 호출에 `type`을 넘긴다.

```java
        return ResponseEntity.ok(
            submissionService.getSubmissionsByUserId(
                userId,
                type,
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo,
                pageable
            )
        );
```

import를 추가한다.

```java
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest' --tests 'SubmissionServiceUT'
```

기대: PASS. **`type` 없이 호출하면 기존과 동일한 결과**여야 한다(회귀 확인).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/repositories/SubmissionSpecifications.java \
        src/main/java/com/smarthealthdog/backend/services/SubmissionService.java \
        src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java \
        src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java
git commit -m "Feat: 제출 목록에 type 필터 추가 및 반려동물별 목록의 DELETED 제외 누락 수정"
```

---

> **Phase 0 완료 지점.** 여기까지가 두 기능이 공유하는 기반이다. 전체 테스트를 돌리고 PR을 올린다.
>
> ```bash
> ./gradlew test
> ./gradlew build -x test
> ```
>
> Phase 0만으로도 **기존 기능에 대한 개선이 배포 가능하다** — 멀티파트 상한(기능 복구), Content-Type 스푸핑 차단(보안), S3 삭제 기능(신규), 빈도 제한 교차 간섭 제거(버그), 반려동물별 목록의 삭제 제출 노출(버그). 진단서 기능을 기다리지 않고 먼저 머지하는 것을 권한다.
>
> 브랜치 예: `refactor/HEALTH-00-업로드-기반-정비`

---
# Phase 1 — HEALTH-01 건강검진표 OCR

## Task 10: `V55` 마이그레이션 — `pet_health_certificates`

**설계 판단 3가지를 코드로 고정한다** (SPEC §3.1):

1. **종류·성별·연령은 enum이 아니라 원문 텍스트.** 샘플 진단서는 종류를 `Canine`, 성별을 `Castrated Male`, 연령을 `16년 3개월`로 적는다. 기존 `PetSpecies(DOG/CAT)`·`PetGender(MALE/FEMALE/UNKNOWN)`로는 **중성화 여부와 개월 단위 나이가 손실된다.** 진단서는 발급 시점 문서이므로 원문 보존이 맞다.
2. **날짜는 파싱값과 원문을 함께 저장.** 정렬·필터는 `DATE`로, 화면에는 원문을 보여준다. **파싱 실패가 등록을 막지 않는다** — 원문이 있으면 등록하고 `DATE`는 NULL로 둔 뒤 수정 화면에서 보정한다.
3. **`raw_ocr_json` 컬럼을 두지 않는다.** OCR 원응답에는 견주 성명·주소가 들어 있다. 디버깅 편의로 통째로 저장하면 그 순간 개인정보가 DB에 들어간다. 화이트리스트한 12개 항목만 꺼내 쓰고 원응답은 메모리에서만 다룬다.

**이미지 키 컬럼이 없다.** 원본 이미지는 `submissions.photo_url`에 저장된다 — `Submission`을 재사용했기 때문에 중복 컬럼이 불필요하다. `oral_health_records`가 이미 `submission_id`를 가진 것과 같은 구조다.

**번호 선점:** `V53`은 Task 7(빈도 제한 테이블)이, `V54`는 2026-10-06 병원 검색 권한 제거가 썼으므로 진단서 테이블은 **`V55`**를 받는다. 이 마이그레이션은 진단서 테이블만 담는다.

**Files:**
- Create: `src/main/resources/db/migration/V56__create_pet_health_certificates.sql`

**Interfaces:**
- Consumes: 없음 (DDL)
- Produces: 테이블 `pet_health_certificates`. Task 11의 엔티티가 이 컬럼 이름·타입과 정확히 맞아야 한다.

- [ ] **Step 1: 마이그레이션 번호가 비어 있는지 확인한다**

```bash
ls src/main/resources/db/migration/ | sort -V | tail -5
```

기대: `V54__remove_hospital_search_permission.sql`까지만 있고 `V55`는 없다. 이미 있다면 다음 빈 번호로 올린다. **적용된 마이그레이션은 절대 수정하지 않는다.**

- [ ] **Step 2: 마이그레이션을 쓴다**

`src/main/resources/db/migration/V56__create_pet_health_certificates.sql` 신규 생성. DDL 스타일은 `V51`을 따른다.

```sql
-- 건강검진표(진단서) OCR 결과 기록.
--
-- 이미지 키 컬럼이 없다. 원본 이미지는 submissions.photo_url 에 있다.
-- raw_ocr_json 컬럼도 두지 않는다 — OCR 원응답에는 견주 성명 주소가 포함되며,
-- 화이트리스트한 12개 항목만 꺼내 저장한다.
CREATE TABLE pet_health_certificates (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pet_id BIGINT NOT NULL,
    submission_id UUID NOT NULL,

    -- 진단서 12개 항목.
    -- 종류 성별 연령을 enum 이 아니라 원문으로 두는 이유:
    -- 진단서는 'Canine' / 'Castrated Male' / '16년 3개월' 처럼 적으며,
    -- PetSpecies PetGender 로 변환하면 중성화 여부와 개월 단위 나이가 손실된다.
    animal_name         VARCHAR(255),   -- 동물명
    species_text        VARCHAR(255),   -- 종류 (원문)
    breed               VARCHAR(255),   -- 품종
    gender_text         VARCHAR(255),   -- 성별 (원문)
    coat_color          VARCHAR(255),   -- 모색
    age_text            VARCHAR(255),   -- 연령 (원문)
    features            TEXT,           -- 특징
    disease_name        TEXT NOT NULL,  -- 병명 (필수)
    onset_date          DATE,           -- 발병 연원일 (파싱 성공 시)
    onset_date_text     VARCHAR(64),    -- 발병 연원일 (원문)
    diagnosed_date      DATE,           -- 진단 연원일 (파싱 성공 시)
    diagnosed_date_text VARCHAR(64) NOT NULL,  -- 진단 연원일 (원문, 필수)
    prognosis           TEXT,           -- 예후 소견
    remarks             TEXT,           -- 그외의 사항 (비고)

    ocr_confidence NUMERIC(5,4) NOT NULL,
    is_manually_edited BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pet_health_certificates_pet
        FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE,

    CONSTRAINT fk_pet_health_certificates_submission
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,

    -- 제출 1건당 진단서 1건을 보장한다.
    CONSTRAINT uq_pet_health_certificates_submission UNIQUE (submission_id),

    CONSTRAINT chk_pet_health_certificates_ocr_confidence
        CHECK (ocr_confidence >= 0 AND ocr_confidence <= 1)
);

CREATE INDEX idx_pet_health_certificates_pet_id
    ON pet_health_certificates (pet_id);

CREATE INDEX idx_pet_health_certificates_diagnosed_date
    ON pet_health_certificates (diagnosed_date);
```

- [ ] **Step 3: 마이그레이션이 적용되는지 확인한다**

dev 인프라를 띄우고 애플리케이션을 기동해 Flyway가 `V55`를 적용하는지 본다.

```bash
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

기대: 기동 로그에 `Migrating schema "public" to version "54 - create pet health certificates"` 가 나오고 에러가 없다. 확인 후 `Ctrl+C`.

> **테스트 환경에서는 Flyway가 비활성이다**(`application-test.yml`: `flyway.enabled: false`, `ddl-auto: create-drop`). 따라서 H2 테이블은 Task 11의 **엔티티로부터** 생성된다. 그래서 **엔티티와 이 DDL이 어긋나면 테스트는 통과하고 운영만 깨진다.** Step 3을 건너뛰지 않는다.

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/db/migration/V56__create_pet_health_certificates.sql
git commit -m "Feat: 건강검진표 기록 테이블 pet_health_certificates 추가"
```

---

## Task 11: 진단서 엔티티·리포지토리와 `createCompletedSubmission`

**왜:** `SubmissionService`를 **기록의 뼈대로 그대로 재사용**한다. `createSubmission`은 `photoUrl=""` + `PENDING`으로 행을 만드는 비동기용이라 동기 경로에 맞지 않는다. 동기 경로는 OCR이 이미 성공한 뒤에 호출되므로 **처음부터 `COMPLETED`로** 만들어야 한다.

**이것이 배치 간섭을 구조적으로 막는 지점이다.** `PushAIInferenceTasks`는 `status = PENDING AND photoUrl <> ''`인 행만 집고 `RetryAIInferenceTasks`는 `PROCESSING`만 건드린다. 동기 경로가 처음부터 `COMPLETED`로 만들면 **두 배치 중 어느 것도 이 행을 집지 않는다.** Celery 큐로 잘못 흘러갈 경로가 없다.

> ⚠️ **`failSubmission`은 쓰지 않는다.** 이 메서드는 `status != PROCESSING`이면 예외를 던지는데, 기존 업로드 실패 경로가 `PENDING` 상태에서 호출하는 기존 버그가 있다. 동기 경로는 실패 시 아무것도 저장하지 않으므로 실패 상태 전환이 애초에 필요 없다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/domain/PetHealthCertificate.java`
- Create: `src/main/java/com/smarthealthdog/backend/repositories/PetHealthCertificateRepository.java`
- Modify: `src/main/java/com/smarthealthdog/backend/services/SubmissionService.java`
- Test: `src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java` (기존에 추가)

**Interfaces:**
- Consumes: `V55` 컬럼 정의 (Task 10), `SubmissionTypeEnum.HEALTH_CERTIFICATE` (Task 5)
- Produces:
    - `PetHealthCertificate` 엔티티 — 빌더 필드명: `pet`, `submission`, `animalName`, `speciesText`, `breed`, `genderText`, `coatColor`, `ageText`, `features`, `diseaseName`, `onsetDate`, `onsetDateText`, `diagnosedDate`, `diagnosedDateText`, `prognosis`, `remarks`, `ocrConfidence`, `manuallyEdited`
    - `PetHealthCertificateRepository.findBySubmissionId(UUID) → Optional<PetHealthCertificate>`
    - `PetHealthCertificateRepository.existsBySubmissionId(UUID) → boolean`
    - `SubmissionService.createCompletedSubmission(Pet pet, SubmissionTypeEnum type, String photoKey) → Submission`
    - Task 15·17·18이 쓴다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SubmissionServiceTest.java`에 추가한다. H2 통합 테스트로 쓰는 이유는 `COMPLETED` 상태와 `completedAt` 설정이 실제 저장까지 가야 확인되기 때문이다.

```java
@Test
void createCompletedSubmission_처음부터_COMPLETED_상태로_만든다() {
    Submission submission = submissionService.createCompletedSubmission(
        testPet,
        SubmissionTypeEnum.HEALTH_CERTIFICATE,
        "health-certificates/abc.jpg"
    );

    assertNotNull(submission.getId());
    assertEquals(SubmissionStatus.COMPLETED, submission.getStatus());
    assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, submission.getType());
    assertEquals("health-certificates/abc.jpg", submission.getPhotoUrl());
    assertNotNull(submission.getSubmittedAt());
    assertNotNull(submission.getCompletedAt());
    assertNull(submission.getFailureReason());

    // 저장까지 끝났는지 확인한다 (createSubmission 과 달리 저장 전 상태를 돌려주지 않는다).
    assertTrue(submissionRepository.findById(submission.getId()).isPresent());
}

@Test
void createCompletedSubmission_배치가_집어가지_않는_상태여야_한다() {
    // PushAIInferenceTasks 는 status=PENDING AND photoUrl<>'' 인 행만 집는다.
    // 동기 경로의 행이 Celery 큐로 흘러가면 안 된다.
    submissionService.createCompletedSubmission(
        testPet, SubmissionTypeEnum.HEALTH_CERTIFICATE, "health-certificates/abc.jpg"
    );

    List<Submission> waiting = submissionRepository.findSubmissionsWaitingToBeProcessedByAmount(
        SubmissionStatus.PENDING,
        PageRequest.of(0, 100)
    );

    assertTrue(waiting.isEmpty(), "동기 경로 행이 Celery 대기 목록에 들어가면 안 된다");
}

@Test
void createCompletedSubmission_photoKey가_비면_예외를_던진다() {
    // 동기 경로는 S3 업로드가 끝난 뒤에만 호출되므로 빈 키는 호출 순서 버그다.
    assertThrows(IllegalArgumentException.class, () ->
        submissionService.createCompletedSubmission(
            testPet, SubmissionTypeEnum.HEALTH_CERTIFICATE, ""
        )
    );
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest'
```

기대: 컴파일 실패 — `createCompletedSubmission`이 없다.

- [ ] **Step 3: 엔티티를 만든다**

`domain/PetHealthCertificate.java` 신규 생성. 타임스탬프는 `Pet` 엔티티와 동일하게 `@CreationTimestamp`/`@UpdateTimestamp`를 쓴다.

```java
package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 건강검진표(진단서) OCR 결과 기록.
 *
 * 종류 성별 연령을 enum 이 아니라 원문 텍스트로 보관한다.
 * 진단서는 'Canine' / 'Castrated Male' / '16년 3개월' 처럼 적으며,
 * PetSpecies PetGender 로 변환하면 중성화 여부와 개월 단위 나이가 손실된다.
 *
 * 날짜는 파싱값(LocalDate)과 원문(text)을 함께 보관한다.
 * OCR 이 구분자나 숫자를 틀릴 수 있어 파싱 실패가 등록을 막지 않는다.
 *
 * 원본 이미지 키는 이 엔티티에 없다 — submission.photoUrl 을 쓴다.
 * OCR 원응답(raw json)도 저장하지 않는다 — 견주 성명 주소가 포함된다.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pet_health_certificates")
public class PetHealthCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    // ── 진단서 12개 항목 ──

    @Column(name = "animal_name", length = 255)
    private String animalName;

    @Column(name = "species_text", length = 255)
    private String speciesText;

    @Column(name = "breed", length = 255)
    private String breed;

    @Column(name = "gender_text", length = 255)
    private String genderText;

    @Column(name = "coat_color", length = 255)
    private String coatColor;

    @Column(name = "age_text", length = 255)
    private String ageText;

    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** 병명 — 필수. 비면 등록 자체가 성립하지 않는다. */
    @Column(name = "disease_name", nullable = false, columnDefinition = "TEXT")
    private String diseaseName;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "onset_date_text", length = 64)
    private String onsetDateText;

    @Column(name = "diagnosed_date")
    private LocalDate diagnosedDate;

    /** 진단 연원일 원문 — 필수. 파싱 실패해도 원문은 반드시 있다. */
    @Column(name = "diagnosed_date_text", nullable = false, length = 64)
    private String diagnosedDateText;

    @Column(name = "prognosis", columnDefinition = "TEXT")
    private String prognosis;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // ── 메타 ──

    @Column(name = "ocr_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    /** 한 번이라도 사용자가 보정하면 true 가 되고 되돌릴 수 없다. */
    @Column(name = "is_manually_edited", nullable = false)
    @Builder.Default
    private boolean manuallyEdited = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

> **`is_manually_edited` 컬럼과 `manuallyEdited` 필드의 이름이 다르다.** Lombok이 `boolean manuallyEdited`에 대해 `isManuallyEdited()` 게터를 만들고, `@Column(name = "is_manually_edited")`가 매핑을 고정한다. Jackson 직렬화 이름은 Task 17의 DTO에서 `@JsonProperty("is_manually_edited")`로 명시하므로 혼동이 생기지 않는다.

- [ ] **Step 4: 리포지토리를 만든다**

`repositories/PetHealthCertificateRepository.java`:

```java
package com.smarthealthdog.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.PetHealthCertificate;

@Repository
public interface PetHealthCertificateRepository extends JpaRepository<PetHealthCertificate, Long> {

    /**
     * 제출 ID로 진단서를 조회한다.
     * 상세 응답에 pet 정보가 필요하므로 함께 가져온다.
     */
    @Query("""
        SELECT c FROM PetHealthCertificate c
        JOIN FETCH c.pet p
        JOIN FETCH p.owner u
        WHERE c.submission.id = :submissionId
    """)
    Optional<PetHealthCertificate> findBySubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * 중복 저장 방지용. uq_pet_health_certificates_submission 위반이
     * 500 으로 노출되기 전에 확인한다.
     */
    boolean existsBySubmissionId(UUID submissionId);
}
```

> `existsBySubmissionId`는 Spring Data가 메서드 이름으로 파생한다. `submission`이 연관 필드이므로 `submission.id`를 뜻하는 `BySubmissionId`가 올바른 파생 경로다.

- [ ] **Step 5: `createCompletedSubmission`을 추가한다**

`services/SubmissionService.java` — `createSubmission` 바로 아래에 추가한다. 기존 메서드는 건드리지 않는다.

```java
    /**
     * 이미 완료된 제출 기록을 만들어 저장한다. 동기 경로 전용이다.
     *
     * createSubmission 과 달리 photoUrl 과 COMPLETED 상태를 처음부터 채운다.
     * PushAIInferenceTasks 는 status=PENDING AND photoUrl&lt;&gt;'' 인 행만,
     * RetryAIInferenceTasks 는 PROCESSING 인 행만 집으므로,
     * 이 상태로 만든 행은 두 배치 중 어느 것도 집지 않는다.
     * Celery 큐로 잘못 흘러갈 경로가 구조적으로 없다.
     *
     * @param pet 반려동물
     * @param type 제출 유형 (HEALTH_CERTIFICATE)
     * @param photoKey S3 업로드가 끝난 object key
     * @return 저장된 제출
     * @throws IllegalArgumentException 인자가 비어 있는 경우
     */
    @Transactional
    public Submission createCompletedSubmission(Pet pet, SubmissionTypeEnum type, String photoKey) {
        if (pet == null || type == null) {
            throw new IllegalArgumentException("Pet 과 type 은 null 일 수 없습니다.");
        }

        // 동기 경로는 S3 업로드가 끝난 뒤에만 이 메서드를 호출한다.
        // 빈 키가 들어왔다면 호출 순서 버그다.
        if (photoKey == null || photoKey.isBlank()) {
            throw new IllegalArgumentException("photoKey 가 비어 있습니다. S3 업로드가 선행되어야 합니다.");
        }

        Instant now = Instant.now();

        Submission submission = Submission.builder()
            .id(UuidCreator.getTimeOrderedEpoch())
            .type(type)
            .pet(pet)
            .photoUrl(photoKey)
            .status(SubmissionStatus.COMPLETED)
            .submittedAt(now)
            .completedAt(now)
            .build();

        return submissionRepository.save(submission);
    }
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest'
```

기대: PASS. H2 테이블이 엔티티로부터 생성되므로(`ddl-auto: create-drop`), 엔티티 매핑 오류가 있으면 컨텍스트 로딩 단계에서 드러난다.

- [ ] **Step 7: 엔티티와 DDL이 어긋나지 않는지 확인한다**

H2는 Flyway를 쓰지 않으므로 **테스트가 통과해도 운영 DDL과 맞는다는 보장이 없다.** dev로 한 번 띄워 Hibernate가 불평하지 않는지 본다.

```bash
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

기대: 기동 성공. dev는 `ddl-auto: none`이라 Hibernate가 스키마를 만들지 않지만, 엔티티가 참조하는 컬럼이 없으면 첫 쿼리에서 터진다. 기동 후 Swagger(`/swagger-ui/index.html`)가 뜨는지 확인하고 `Ctrl+C`.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/domain/PetHealthCertificate.java \
        src/main/java/com/smarthealthdog/backend/repositories/PetHealthCertificateRepository.java \
        src/main/java/com/smarthealthdog/backend/services/SubmissionService.java \
        src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java
git commit -m "Feat: 진단서 엔티티 리포지토리 추가 및 완료 상태 제출 생성 메서드 구현"
```

---

## Task 12: 날짜 원문 파서

**왜 별도 작업인가:** 한국 진단서는 `2020.10.28` 표기가 흔하고 OCR이 구분자나 숫자를 틀릴 수 있다. 이 파서는 **순수 함수**라 외부 의존 없이 전부 단위 테스트로 고정할 수 있고, Task 15(동기 흐름)와 Task 18(PATCH 재파싱) 양쪽에서 쓰인다. 먼저 만들어 두면 두 작업이 파싱 규칙을 각자 구현하는 일이 없다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/utils/DateTextParser.java`
- Test: `src/test/java/com/smarthealthdog/backend/utils/DateTextParserUT.java`

**Interfaces:**
- Consumes: 없음
- Produces: `DateTextParser.parse(String text) → Optional<LocalDate>` — 파싱 불가면 `Optional.empty()`. **예외를 던지지 않는다** (파싱 실패가 등록을 막지 않는다는 정책을 타입으로 표현).

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/utils/DateTextParserUT.java` 신규 생성.

```java
package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class DateTextParserUT {

    @ParameterizedTest
    @CsvSource({
        "2020.10.28,      2020-10-28",
        "2020-10-28,      2020-10-28",
        "2020/10/28,      2020-10-28",
        "2020년 10월 28일, 2020-10-28",
        "2020년10월28일,   2020-10-28",
        "2020.1.5,        2020-01-05",
        "2020. 10. 28.,   2020-10-28",
        "  2020.10.28  ,  2020-10-28"
    })
    void 흔한_한국_진단서_표기를_파싱한다(String input, String expected) {
        Optional<LocalDate> parsed = DateTextParser.parse(input);

        assertTrue(parsed.isPresent(), "파싱되어야 한다: " + input);
        assertEquals(LocalDate.parse(expected), parsed.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "알 수 없음",
        "2020",            // 연도만 — 월 일이 없으면 날짜가 아니다
        "2020.13.01",      // 13월
        "2020.02.30",      // 2월 30일
        "20201028",        // 구분자 없는 8자리는 지원하지 않는다
        "28.10.2020"       // 일 월 연 순서는 지원하지 않는다 (국내 진단서 관행 아님)
    })
    void 파싱할_수_없으면_빈_Optional을_반환하고_예외를_던지지_않는다(String input) {
        // 파싱 실패가 등록을 막지 않는다는 정책을 타입으로 표현한다.
        assertTrue(DateTextParser.parse(input).isEmpty(), "빈 Optional 이어야 한다: " + input);
    }

    @Test
    void 두자리_연도는_지원하지_않는다() {
        // '20.10.28' 을 2020년으로 추측하면 1920년 2120년과 구분할 수 없다.
        // 추측하지 않고 원문만 남겨 수정 화면에서 보정하게 한다.
        assertTrue(DateTextParser.parse("20.10.28").isEmpty());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'DateTextParserUT'
```

기대: 컴파일 실패 — `DateTextParser`가 없다.

- [ ] **Step 3: 파서를 만든다**

`utils/DateTextParser.java`:

```java
package com.smarthealthdog.backend.utils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 진단서에 적힌 날짜 원문을 LocalDate 로 파싱한다.
 *
 * OCR 이 구분자나 숫자를 틀릴 수 있어 파싱 실패가 등록을 막지 않는다.
 * 그래서 예외를 던지지 않고 빈 Optional 을 반환한다 —
 * 호출자는 원문을 그대로 저장하고 DATE 컬럼만 NULL 로 둔다.
 *
 * 추측하지 않는다: 두 자리 연도('20.10.28')는 1920 2020 2120 을 구분할 수 없고,
 * 일-월-연 순서('28.10.2020')는 국내 진단서 관행이 아니므로 둘 다 파싱하지 않는다.
 */
public final class DateTextParser {

    private DateTextParser() {
    }

    /**
     * 연(4자리) 월 일 순서를 점 하이픈 슬래시 또는 한글 단위로 구분한 표기를 받는다.
     * 예: 2020.10.28 / 2020-10-28 / 2020/10/28 / 2020년 10월 28일 / 2020. 10. 28.
     */
    private static final Pattern YEAR_FIRST = Pattern.compile(
        "^(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*[.일]?$"
    );

    /**
     * 날짜 원문을 파싱한다.
     *
     * @param text 진단서에서 추출한 날짜 원문 (null 허용)
     * @return 파싱된 날짜. 형식이 맞지 않거나 존재하지 않는 날짜면 빈 Optional
     */
    public static Optional<LocalDate> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = YEAR_FIRST.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));

            // LocalDate.of 가 2020-02-30 같은 존재하지 않는 날짜를 거부한다.
            return Optional.of(LocalDate.of(year, month, day));
        } catch (NumberFormatException | DateTimeParseException | java.time.DateTimeException e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'DateTextParserUT'
```

기대: 전부 PASS. 실패하는 케이스가 있으면 **정규식을 넓히기 전에 그 입력이 정말 지원 대상인지** 판단한다. 추측 파싱을 늘리면 틀린 날짜가 조용히 저장된다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/utils/DateTextParser.java \
        src/test/java/com/smarthealthdog/backend/utils/DateTextParserUT.java
git commit -m "Feat: 진단서 날짜 원문 파서 추가"
```

---
## Task 13: OCR 경계 — 인터페이스, 결과 DTO, 필드 추출기

**왜 인터페이스를 먼저:** 외부 의존성을 인터페이스로 가리는 것은 이 저장소의 확립된 관례다(`ImgUtils`, `ImageUploader`, `EmailService`, `DiagnosisTaskRequestClient`). 이 작업에서 **인터페이스와 순수 함수인 필드 추출기만** 만들고 실제 HTTP 호출은 Task 14로 분리한다. 그러면 Task 15(동기 흐름)가 벤더 응답 없이도 가짜 구현으로 전부 테스트된다.

**벤더 선택 — Google Cloud Vision (`DOCUMENT_TEXT_DETECTION`).** 명세 §8.1은 NCP CLOVA OCR을 권장하지만 **Google Cloud Vision을 쓴다 — 사용자 결정이다.** 선택의 실무적 근거:

- `DOCUMENT_TEXT_DETECTION` + `imageContext.languageHints: ["ko"]`로 한국어 밀집 문서(인쇄된 진단서)를 읽는다. `TEXT_DETECTION`이 아니라 `DOCUMENT_` 쪽을 쓰는 이유는 진단서가 표·라벨이 빽빽한 문서이기 때문이다.
- **인증은 API 키 하나.** REST `images:annotate`가 `?key=...` 쿼리 파라미터 인증을 지원하므로 환경변수 `GOOGLE_VISION_API_KEY` **1개**로 끝난다. CLOVA는 `invoke-url` + `secret-key` 2개가 필요했다. 서비스 계정 JSON 방식을 쓰지 않으므로 `docker-compose`와 CI/CD 배포 스크립트를 건드릴 일이 없다.
- **Gradle 의존성 추가 없음.** `google-cloud-vision` SDK 대신 REST를 기존 `java.net.http.HttpClient` + Jackson으로 호출한다(`utils/OAuthClient`와 같은 방식).

**서식 고정 문서 최적화는 후속 과제다.** Vision에는 CLOVA의 Template OCR에 대응하는 개념이 없다. 진단서 서식이 실제로 고정적이어서 라벨 매칭 정확도를 더 올려야 한다면 **Google Document AI의 양식(Form) 파서**가 다음 선택지이며, 그때 바꿀 곳은 `GoogleVisionOcrClient` **한 파일**이다 — 인터페이스와 추출기는 그대로다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/ocr/OcrTextBlock.java`
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/ocr/HealthCertificateOcrResult.java`
- Create: `src/main/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateOcrClient.java`
- Create: `src/main/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateFieldExtractor.java`
- Test: `src/test/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateFieldExtractorUT.java`

**Interfaces:**
- Consumes: `DateTextParser.parse(String)` (Task 12)
- Produces:
    - `OcrTextBlock(String text, double confidence)` — OCR이 읽은 텍스트 조각 하나
    - `HealthCertificateOcrResult` — 12개 항목 + `averageConfidence` + `documentKeywordFound`. **`LocalDate` 파싱까지 끝난 상태**로 나온다.
    - `HealthCertificateOcrClient.extract(byte[] imageBytes, String contentType) → HealthCertificateOcrResult`
    - `HealthCertificateFieldExtractor.extract(List<OcrTextBlock> blocks) → HealthCertificateOcrResult`
    - Task 14가 인터페이스를 구현하고, Task 15가 결과를 판별한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateFieldExtractorUT.java` 신규 생성. 추출기는 순수 함수이므로 모든 규칙을 여기서 고정한다.

```java
package com.smarthealthdog.backend.clients.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;

public class HealthCertificateFieldExtractorUT {

    private final HealthCertificateFieldExtractor extractor = new HealthCertificateFieldExtractor();

    private static OcrTextBlock block(String text) {
        return new OcrTextBlock(text, 0.95);
    }

    private List<OcrTextBlock> 정상_진단서() {
        return List.of(
            block("진단서"),
            block("동물의 표시"),
            block("동물명: 초코"),
            block("종류: Canine"),
            block("품종: Pug"),
            block("성별: Castrated Male"),
            block("모색: 흰색"),
            block("연령: 16년 3개월"),
            block("특징: 없음"),
            block("병명: 심장비대, 폐침윤"),
            block("발병 연원일: 2020.10.28"),
            block("진단 연원일: 2020.10.28"),
            block("예후 소견: 엑스레이 검사상 폐침윤이 확인됩니다."),
            block("그외의 사항: 10.28일 진료에 대한 진단서 발급 원합니다.")
        );
    }

    @Test
    void 라벨_뒤의_값을_12개_항목으로_추출한다() {
        HealthCertificateOcrResult result = extractor.extract(정상_진단서());

        assertEquals("초코", result.animalName());
        assertEquals("Canine", result.speciesText());
        assertEquals("Pug", result.breed());
        assertEquals("Castrated Male", result.genderText());
        assertEquals("흰색", result.coatColor());
        assertEquals("16년 3개월", result.ageText());
        assertEquals("없음", result.features());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals("2020.10.28", result.onsetDateText());
        assertEquals("2020.10.28", result.diagnosedDateText());
        assertEquals("엑스레이 검사상 폐침윤이 확인됩니다.", result.prognosis());
        assertEquals("10.28일 진료에 대한 진단서 발급 원합니다.", result.remarks());
    }

    @Test
    void 날짜는_원문과_파싱값을_함께_담는다() {
        HealthCertificateOcrResult result = extractor.extract(정상_진단서());

        assertEquals(LocalDate.of(2020, 10, 28), result.onsetDate());
        assertEquals(LocalDate.of(2020, 10, 28), result.diagnosedDate());
    }

    @Test
    void 날짜_파싱에_실패해도_원문은_보존하고_파싱값만_null로_둔다() {
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("병명: 심장비대"),
            block("진단 연원일: 20Z0.1O.28")   // OCR 이 0 을 O 로 틀린 경우
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("20Z0.1O.28", result.diagnosedDateText());
        assertNull(result.diagnosedDate());
    }

    @Test
    void 콜론_없이_라벨과_값이_떨어져_있어도_추출한다() {
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("병명"),
            block("심장비대"),
            block("진단 연원일"),
            block("2020.10.28")
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("심장비대", result.diseaseName());
        assertEquals("2020.10.28", result.diagnosedDateText());
    }

    @Test
    void 문서_고유_키워드가_하나라도_있으면_documentKeywordFound가_true다() {
        assertTrue(extractor.extract(정상_진단서()).documentKeywordFound());

        assertTrue(extractor.extract(List.of(block("병명: 심장비대"))).documentKeywordFound());
    }

    @Test
    void 진단서와_무관한_텍스트는_documentKeywordFound가_false다() {
        List<OcrTextBlock> blocks = List.of(
            block("영수증"),
            block("합계 12,000원"),
            block("카드 승인")
        );

        assertFalse(extractor.extract(blocks).documentKeywordFound());
    }

    @Test
    void averageConfidence는_블록_신뢰도의_평균이다() {
        List<OcrTextBlock> blocks = List.of(
            new OcrTextBlock("진단서", 1.0),
            new OcrTextBlock("병명: 심장비대", 0.8),
            new OcrTextBlock("진단 연원일: 2020.10.28", 0.6)
        );

        assertEquals(0.8, extractor.extract(blocks).averageConfidence(), 0.0001);
    }

    @Test
    void 블록이_비면_신뢰도는_0이고_모든_항목이_null이다() {
        HealthCertificateOcrResult result = extractor.extract(List.of());

        assertEquals(0.0, result.averageConfidence(), 0.0001);
        assertNull(result.diseaseName());
        assertNull(result.diagnosedDateText());
        assertFalse(result.documentKeywordFound());
    }

    @Test
    void 견주_성명과_주소는_추출하지_않는다() {
        // SPEC 1.5 범위 밖: 견주 성명 주소는 추출하지도 저장하지도 않는다.
        // 결과 레코드에 해당 필드가 없어야 하며, 소유자 라벨이 다른 항목으로
        // 잘못 흘러들어가지도 않아야 한다.
        List<OcrTextBlock> blocks = List.of(
            block("진단서"),
            block("소유자 성명: 홍길동"),
            block("주소: 서울시 강남구"),
            block("병명: 심장비대"),
            block("진단 연원일: 2020.10.28")
        );

        HealthCertificateOcrResult result = extractor.extract(blocks);

        assertEquals("심장비대", result.diseaseName());
        assertNull(result.animalName());
        assertNull(result.features());
        assertNull(result.remarks());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'HealthCertificateFieldExtractorUT'
```

기대: 컴파일 실패 — 타입들이 없다.

- [ ] **Step 3: 값 객체 2개를 만든다**

`dto/health/ocr/OcrTextBlock.java`:

```java
package com.smarthealthdog.backend.dto.health.ocr;

/**
 * OCR 이 읽어낸 텍스트 조각 하나와 그 신뢰도.
 * 벤더 응답 구조를 내부로 들이지 않기 위한 중간 표현이다.
 */
public record OcrTextBlock(String text, double confidence) {}
```

`dto/health/ocr/HealthCertificateOcrResult.java`:

```java
package com.smarthealthdog.backend.dto.health.ocr;

import java.time.LocalDate;

/**
 * 진단서 OCR 추출 결과.
 *
 * 견주 성명 주소 발급 병원 수의사 서명 면허번호 필드는 의도적으로 없다 —
 * 추출하지도 저장하지도 않는다 (SPEC 1.5 범위 밖).
 *
 * 날짜는 원문과 파싱값을 함께 담는다. 파싱 실패 시 LocalDate 쪽만 null 이다.
 */
public record HealthCertificateOcrResult(
    String animalName,
    String speciesText,
    String breed,
    String genderText,
    String coatColor,
    String ageText,
    String features,
    String diseaseName,
    LocalDate onsetDate,
    String onsetDateText,
    LocalDate diagnosedDate,
    String diagnosedDateText,
    String prognosis,
    String remarks,

    /** 전체 블록 신뢰도의 평균. 0.0 ~ 1.0 */
    double averageConfidence,

    /** 진단서 고유 키워드가 하나라도 검출됐는지. false 면 진단서가 아니다. */
    boolean documentKeywordFound
) {}
```

- [ ] **Step 4: 클라이언트 인터페이스를 만든다**

`clients/ocr/HealthCertificateOcrClient.java`:

```java
package com.smarthealthdog.backend.clients.ocr;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;

/**
 * 진단서 OCR 추상화.
 *
 * 구현(GoogleVisionOcrClient)은 이 인터페이스 뒤에 숨긴다.
 * 테스트에서 가짜 구현으로 갈아끼우기 위한 것이기도 하다.
 */
public interface HealthCertificateOcrClient {

    /**
     * 진단서 이미지에서 12개 항목을 추출한다.
     *
     * @param imageBytes 이미지 바이트
     * @param contentType Tika 가 판정한 MIME 타입
     * @return 추출 결과 (판별은 호출자가 한다)
     * @throws ServiceUnavailableException 연결 실패 타임아웃 5xx — {@code OCR_SERVICE_UNAVAILABLE}
     * @throws InternalServerErrorException 4xx (잘못된 요청 키) — 사용자 잘못이 아니다
     * @throws InvalidRequestDataException 200 이지만 본문 파싱 실패 — {@code OCR_RECOGNITION_FAILED}
     */
    HealthCertificateOcrResult extract(byte[] imageBytes, String contentType);
}
```

- [ ] **Step 5: 필드 추출기를 만든다**

`clients/ocr/HealthCertificateFieldExtractor.java`:

```java
package com.smarthealthdog.backend.clients.ocr;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;
import com.smarthealthdog.backend.utils.DateTextParser;

/**
 * OCR 텍스트 블록에서 진단서 12개 항목을 뽑는다.
 *
 * 순수 함수다 — 외부 호출이 없어 전부 단위 테스트로 고정된다.
 * General OCR 응답을 전제하며, Template OCR 로 바꿀 때도 이 클래스는 그대로 쓴다.
 *
 * 화이트리스트 방식이다: 아래 라벨에 해당하는 값만 꺼내고 나머지는 버린다.
 * 견주 성명 주소가 결과에 섞여 들어갈 경로를 원천 차단한다.
 */
@Component
public class HealthCertificateFieldExtractor {

    /** 이 문서가 진단서인지 판단하는 고유 키워드. 하나라도 있으면 진단서로 본다. */
    private static final List<String> DOCUMENT_KEYWORDS = List.of(
        "진단서", "병명", "동물의 표시", "진단 연원일", "예후 소견"
    );

    /**
     * 라벨 → 결과 키. 순서가 중요하다 —
     * '발병 연원일' 이 '연원일' 보다 먼저 와야 '진단 연원일' 과 섞이지 않는다.
     */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("동물명", "animalName");
        LABELS.put("종류", "speciesText");
        LABELS.put("품종", "breed");
        LABELS.put("성별", "genderText");
        LABELS.put("모색", "coatColor");
        LABELS.put("연령", "ageText");
        LABELS.put("특징", "features");
        LABELS.put("병명", "diseaseName");
        LABELS.put("발병 연원일", "onsetDateText");
        LABELS.put("발병연원일", "onsetDateText");
        LABELS.put("진단 연원일", "diagnosedDateText");
        LABELS.put("진단연원일", "diagnosedDateText");
        LABELS.put("예후 소견", "prognosis");
        LABELS.put("예후소견", "prognosis");
        LABELS.put("그외의 사항", "remarks");
        LABELS.put("비고", "remarks");
    }

    /**
     * 텍스트 블록에서 12개 항목을 추출한다.
     *
     * @param blocks OCR 이 읽은 텍스트 조각 목록 (null 이면 빈 목록으로 취급)
     * @return 추출 결과. 판별(신뢰도 필수 항목 문서 종류)은 호출자가 한다.
     */
    public HealthCertificateOcrResult extract(List<OcrTextBlock> blocks) {
        List<OcrTextBlock> safeBlocks = blocks == null ? List.of() : blocks;

        Map<String, String> values = new LinkedHashMap<>();
        boolean keywordFound = false;

        for (int i = 0; i < safeBlocks.size(); i++) {
            String text = normalize(safeBlocks.get(i).text());
            if (text.isEmpty()) {
                continue;
            }

            if (!keywordFound && containsDocumentKeyword(text)) {
                keywordFound = true;
            }

            // 라벨을 가장 긴 것부터 맞춰 '발병 연원일' 이 '연원일' 로 잘리지 않게 한다.
            String matchedLabel = findLabel(text);
            if (matchedLabel == null) {
                continue;
            }

            String key = LABELS.get(matchedLabel);
            if (values.containsKey(key)) {
                // 같은 라벨이 두 번 나오면 첫 번째를 신뢰한다 (표 머리글 반복 대비).
                continue;
            }

            String value = valueAfterLabel(text, matchedLabel);

            // 라벨만 있고 값이 다음 블록에 있는 레이아웃을 처리한다.
            if (value.isEmpty() && i + 1 < safeBlocks.size()) {
                String next = normalize(safeBlocks.get(i + 1).text());
                if (findLabel(next) == null) {
                    value = next;
                }
            }

            if (!value.isEmpty()) {
                values.put(key, value);
            }
        }

        String onsetDateText = values.get("onsetDateText");
        String diagnosedDateText = values.get("diagnosedDateText");

        return new HealthCertificateOcrResult(
            values.get("animalName"),
            values.get("speciesText"),
            values.get("breed"),
            values.get("genderText"),
            values.get("coatColor"),
            values.get("ageText"),
            values.get("features"),
            values.get("diseaseName"),
            parseDateOrNull(onsetDateText),
            onsetDateText,
            parseDateOrNull(diagnosedDateText),
            diagnosedDateText,
            values.get("prognosis"),
            values.get("remarks"),
            averageConfidence(safeBlocks),
            keywordFound
        );
    }

    private LocalDate parseDateOrNull(String text) {
        return DateTextParser.parse(text).orElse(null);
    }

    private double averageConfidence(List<OcrTextBlock> blocks) {
        if (blocks.isEmpty()) {
            return 0.0;
        }

        return blocks.stream()
            .mapToDouble(OcrTextBlock::confidence)
            .average()
            .orElse(0.0);
    }

    private boolean containsDocumentKeyword(String text) {
        return DOCUMENT_KEYWORDS.stream().anyMatch(text::contains);
    }

    /**
     * 텍스트가 어떤 라벨로 시작하는지 찾는다.
     * 긴 라벨을 먼저 시도해 '발병 연원일' 이 '연령' 등과 충돌하지 않게 한다.
     */
    private String findLabel(String text) {
        return LABELS.keySet().stream()
            .filter(text::startsWith)
            .max(java.util.Comparator.comparingInt(String::length))
            .orElse(null);
    }

    /** 라벨 뒤의 콜론 공백을 떼고 값만 남긴다. */
    private String valueAfterLabel(String text, String label) {
        String rest = text.substring(label.length());
        return rest.replaceFirst("^[\\s:：]+", "").trim();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        // OCR 이 내는 전각 공백과 연속 공백을 하나로 모은다.
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'HealthCertificateFieldExtractorUT'
```

기대: 전부 PASS. 라벨 매칭이 실패하면 **실제 진단서 OCR 응답 샘플을 하나 확보해 테스트에 추가**한 뒤 라벨 목록을 늘린다. 추측으로 정규식을 넓히지 않는다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/dto/health/ocr/ \
        src/main/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateOcrClient.java \
        src/main/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateFieldExtractor.java \
        src/test/java/com/smarthealthdog/backend/clients/ocr/HealthCertificateFieldExtractorUT.java
git commit -m "Feat: 진단서 OCR 인터페이스와 필드 추출기 추가"
```

---

## Task 14: `GoogleVisionOcrClient` — 실제 HTTP 호출

**벤더는 Google Cloud Vision이다** (SPEC §8.1, Task 13의 벤더 선택). `images:annotate`에 `DOCUMENT_TEXT_DETECTION` 1개 feature로 요청하고, `imageContext.languageHints: ["ko"]`로 한국어를 명시한다. `google-cloud-vision` SDK를 넣지 않고 `java.net.http.HttpClient` + Jackson으로 REST를 직접 호출한다 — Gradle 의존성 추가가 없다.

> ⚠ **벤더 미확정(SPEC §8.1).** 바뀌면 이 작업 하나와 `ocr.google.*` 설정 키만 교체된다. Task 13의 인터페이스·추출기와 Task 15의 동기 흐름은 영향이 없다.

**템플릿은 `utils/OAuthClient.java`다.** 저장소에서 유일하게 제대로 된 외부 클라이언트다 — `connectTimeout` 10초, 요청별 `timeout` 15초, 명시적 상태 코드 검사, 비밀값은 길이만 로깅. 반면 **`WebClientConfig`는 맨 `builder.build()`라 타임아웃이 없다.** 그쪽을 따라가면 OCR 장애 시 요청 스레드가 무한정 묶인다.

**재시도는 하지 않는다.** 동기 요청이라 재시도가 사용자 대기 시간을 배로 늘린다(SPEC §8.3).

**🔒 API 키가 URI 쿼리에 들어간다 — URI를 로깅하면 키가 로그에 남는다.** Vision REST는 `?key=...` 쿼리 파라미터로 인증한다. CLOVA는 키가 헤더에 있어 URI 로깅이 안전했지만 여기서는 아니다. **로그에는 키 없는 엔드포인트와 키 길이만 남긴다.** 예외 메시지에도 URI를 넣지 않는다 — `HttpTimeoutException` 같은 JDK 예외가 URI를 담을 수 있으므로 `e`를 그대로 넘기되 **메시지를 사용자 응답으로 내보내지 않는다**(코드 기반 에러라 이미 그렇다).

**에러 매핑** (SPEC §8.3 + Vision 고유 경로):

| 외부 상황 | 매핑 |
|---|---|
| 연결 실패 / 타임아웃 / 5xx | `ServiceUnavailableException(OCR_SERVICE_UNAVAILABLE)` → 503 |
| 4xx (잘못된 API 키·요청 형식·할당량 초과) | `InternalServerErrorException` → 500 + `log.error` (사용자 잘못이 아니다) |
| 200이지만 본문 파싱 실패 | `InvalidRequestDataException(OCR_RECOGNITION_FAILED)` → 400 |
| **200 + `responses[0].error` 존재** (Vision 고유) | `InvalidRequestDataException(OCR_RECOGNITION_FAILED)` → 400 |
| **200 + `fullTextAnnotation` 없음** (텍스트 미검출, Vision 고유) | `InvalidRequestDataException(OCR_RECOGNITION_FAILED)` → 400 |

> **Vision은 이미지 단위 실패를 200으로 돌려준다.** HTTP 상태만 보면 성공처럼 보이고 `responses[0]`에 `error` 객체가 들어온다. 이 경로를 놓치면 빈 결과가 추출기까지 흘러가 "모든 항목이 null인 진단서"가 저장될 수 있다. 상태 코드 검사만으로는 부족하다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/clients/ocr/GoogleVisionOcrClient.java`
- Modify: `src/main/resources/application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml`
- Modify: `README.md` (Task 19에서 함께 정리해도 된다)
- Test: `src/test/java/com/smarthealthdog/backend/clients/ocr/GoogleVisionOcrClientUT.java`

**Interfaces:**
- Consumes: `HealthCertificateOcrClient`, `HealthCertificateFieldExtractor`, `OcrTextBlock` (Task 13), `ServiceUnavailableException` (Task 6)
- Produces: `GoogleVisionOcrClient` 빈 (`HealthCertificateOcrClient`의 유일한 구현). 설정 키 `ocr.google.*`

### 응답 → `List<OcrTextBlock>` 매핑 설계

Task 13의 추출기는 **라벨 단위 텍스트 조각**을 기대한다(`"병명: 심장비대"` 하나가 한 블록). Vision 응답에서 이걸 얻는 길이 두 개 있다.

| 방법 | 내용 | 판단 |
|---|---|---|
| **(A) `fullTextAnnotation.text`를 줄바꿈으로 쪼갠다** | Vision이 이미 읽기 순서대로 조립해 준 전체 텍스트를 `\n`으로 나눠 한 줄 = 한 블록 | **채택.** 진단서는 `라벨: 값`이 한 줄에 오는 서식이라 추출기의 기대와 정확히 맞는다 |
| (B) `pages[].blocks[].paragraphs[].words[].symbols[]`를 직접 조립 | 심볼마다 `property.detectedBreak.type`(`SPACE`/`EOL_SURE_SPACE`/`LINE_BREAK`)을 보고 공백·개행을 넣어 문단 텍스트를 만든다 | 문단별 `confidence`를 살릴 수 있지만 조립 규칙이 길고 깨지기 쉽다. (A)로 정확도가 부족할 때의 **후속 과제** |

**신뢰도는 `pages[].blocks[].confidence`의 평균**을 쓰고, 그 값을 모든 줄에 같게 부여한다. (A)에서는 줄 단위 신뢰도를 얻을 수 없으므로 문서 단위 신뢰도가 유일한 근거다. `averageConfidence`가 결국 이 값이 되므로 Task 15의 `ocr.certificate.min-confidence` 게이트는 **문서 전체 품질**로 판정한다 — 의도한 동작이다.

> **⚠ `confidence`가 아예 없으면 1.0으로 폴백하고 `log.warn`한다 — 의도적 fail-open이다.** Vision은 `confidence`를 생략할 수 있다. 0.0으로 폴백하면 `min-confidence: 0.75` 게이트가 **모든 요청을 `OCR_RECOGNITION_FAILED`로 거부**해 기능 전체가 죽는다(벤더 응답 형태 하나로 생기는 전면 장애). 품질 게이트를 조용히 끄는 대가를 치르더라도 전면 거부를 피하는 쪽을 택한다. `log.warn`이 이 상황을 드러내는 유일한 신호이므로 **운영에서 이 경고를 모니터링한다.** Step 1의 테스트가 이 동작을 못박는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/clients/ocr/GoogleVisionOcrClientUT.java` 신규 생성. `HttpClient`를 목으로 주입해 **응답 본문 파싱과 에러 매핑만** 검증한다. 실제 네트워크는 타지 않는다.

```java
package com.smarthealthdog.backend.clients.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class GoogleVisionOcrClientUT {

    private GoogleVisionOcrClient client;

    @Mock private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    @Mock private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() {
        client = new GoogleVisionOcrClient(new ObjectMapper(), new HealthCertificateFieldExtractor());

        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        ReflectionTestUtils.setField(client, "endpoint", "https://vision.example.com/v1/images:annotate");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "languageHint", "ko");
        ReflectionTestUtils.setField(client, "readTimeoutSeconds", 15);
    }

    /** Vision DOCUMENT_TEXT_DETECTION 성공 응답. 블록 신뢰도 평균은 (0.99+0.91+0.89)/3 = 0.93 */
    private static final String SUCCESS_BODY = """
        {
          "responses": [
            {
              "fullTextAnnotation": {
                "pages": [
                  {
                    "blocks": [
                      { "confidence": 0.99 },
                      { "confidence": 0.91 },
                      { "confidence": 0.89 }
                    ]
                  }
                ],
                "text": "진단서\\n병명: 심장비대\\n진단 연원일: 2020.10.28\\n"
              }
            }
          ]
        }
        """;

    private void given200(String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(), any())).thenReturn(httpResponse);
    }

    @Test
    void 성공_응답을_추출_결과로_변환한다() throws Exception {
        given200(SUCCESS_BODY);

        HealthCertificateOcrResult result = client.extract(new byte[] {1, 2, 3}, "image/jpeg");

        assertEquals("심장비대", result.diseaseName());
        assertEquals("2020.10.28", result.diagnosedDateText());
        assertEquals(0.93, result.averageConfidence(), 0.001);
        assertTrue(result.documentKeywordFound());
    }

    @Test
    void 요청_본문이_DOCUMENT_TEXT_DETECTION과_languageHints를_담는다() {
        // HTTP 를 타지 않는다 — 본문 생성만 검증하므로 httpClient 스텁이 필요 없다.
        // buildRequestBody 는 private 이므로 ReflectionTestUtils 로 직접 호출한다.
        String body = (String) ReflectionTestUtils.invokeMethod(
            client, "buildRequestBody", new byte[] {1, 2, 3}
        );

        assertTrue(body.contains("DOCUMENT_TEXT_DETECTION"), body);
        assertTrue(body.contains("languageHints"), body);
        assertTrue(body.contains("\"ko\""), body);
        // 이미지는 base64 로 content 에 들어간다.
        assertTrue(body.contains("\"content\""), body);
    }

    @Test
    void 타임아웃이면_503으로_매핑한다() throws Exception {
        when(httpClient.send(any(), any()))
            .thenThrow(new java.net.http.HttpTimeoutException("request timed out"));

        ServiceUnavailableException e = assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void 연결_실패면_503으로_매핑한다() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));

        assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );
    }

    @Test
    void 벤더_5xx면_503으로_매핑한다() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpResponse.body()).thenReturn("{\"error\":{\"code\":503}}");
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );
    }

    @Test
    void 잘못된_API_키_403이면_500으로_매핑한다() throws Exception {
        // API 키 오류나 할당량 초과는 사용자 잘못이 아니다.
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"API key not valid\"}}");
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        assertThrows(
            InternalServerErrorException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );
    }

    @Test
    void 본문_파싱_실패면_400_OCR_RECOGNITION_FAILED로_매핑한다() throws Exception {
        given200("{ not json");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void responses가_비면_400_OCR_RECOGNITION_FAILED로_매핑한다() throws Exception {
        given200("{\"responses\": []}");

        assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );
    }

    @Test
    void 이미지_단위_error가_오면_200이어도_400으로_매핑한다() throws Exception {
        // Vision 고유 경로: HTTP 는 200 인데 responses[0].error 로 실패를 알린다.
        given200("""
            {
              "responses": [
                { "error": { "code": 3, "message": "Bad image data" } }
              ]
            }
            """);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void 텍스트가_검출되지_않으면_400으로_매핑한다() throws Exception {
        // 글자가 없는 사진이면 fullTextAnnotation 자체가 없다.
        given200("{\"responses\": [{}]}");

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
    }

    @Test
    void 블록_신뢰도가_없으면_1_0으로_폴백한다() throws Exception {
        // 0.0 으로 폴백하면 min-confidence 게이트가 모든 요청을 거부해 기능이 전면 중단된다.
        given200("""
            {
              "responses": [
                {
                  "fullTextAnnotation": {
                    "pages": [ { "blocks": [ {}, {} ] } ],
                    "text": "진단서\\n병명: 심장비대\\n"
                  }
                }
              ]
            }
            """);

        HealthCertificateOcrResult result = client.extract(new byte[] {1}, "image/jpeg");

        assertEquals(1.0, result.averageConfidence(), 0.0001);
        assertEquals("심장비대", result.diseaseName());
    }

    @Test
    void 인터럽트되면_플래그를_복원하고_503으로_매핑한다() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new InterruptedException("interrupted"));

        assertThrows(
            ServiceUnavailableException.class,
            () -> client.extract(new byte[] {1}, "image/jpeg")
        );

        assertEquals(true, Thread.interrupted(), "인터럽트 플래그를 복원해야 한다");
    }

    @Test
    void 이미지_바이트가_비면_INVALID_IMAGE를_던지고_호출하지_않는다() {
        assertThrows(
            InvalidRequestDataException.class,
            () -> client.extract(new byte[0], "image/jpeg")
        );

        org.mockito.Mockito.verifyNoInteractions(httpClient);
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'GoogleVisionOcrClientUT'
```

기대: 컴파일 실패 — `GoogleVisionOcrClient`가 없다.

- [ ] **Step 3: 클라이언트를 만든다**

`clients/ocr/GoogleVisionOcrClient.java`:

```java
package com.smarthealthdog.backend.clients.ocr;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.dto.health.ocr.OcrTextBlock;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google Cloud Vision DOCUMENT_TEXT_DETECTION 구현.
 *
 * 타임아웃 에러 매핑 비밀값 로깅 방침은 utils/OAuthClient 를 따른다 —
 * 저장소에서 유일하게 타임아웃과 상태 코드 검사를 제대로 하는 외부 클라이언트다.
 *
 * 재시도하지 않는다. 동기 요청이라 재시도가 사용자 대기 시간을 배로 늘린다.
 *
 * 주의: Vision 은 이미지 단위 실패를 HTTP 200 + responses[0].error 로 돌려준다.
 * 상태 코드만 검사하면 빈 결과가 추출기까지 흘러간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleVisionOcrClient implements HealthCertificateOcrClient {

    /** confidence 가 아예 없을 때의 폴백. 0.0 으로 두면 min-confidence 게이트가 전부 거부한다. */
    private static final double CONFIDENCE_FALLBACK = 1.0;

    private final ObjectMapper objectMapper;
    private final HealthCertificateFieldExtractor fieldExtractor;

    /** OAuthClient 와 동일하게 연결 타임아웃을 건 전용 클라이언트를 쓴다. */
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Value("${ocr.google.endpoint}")
    private String endpoint;

    @Value("${ocr.google.api-key}")
    private String apiKey;

    @Value("${ocr.google.language-hint}")
    private String languageHint;

    @Value("${ocr.google.read-timeout-seconds}")
    private int readTimeoutSeconds;

    /**
     * 진단서 이미지에서 12개 항목을 추출한다.
     *
     * @param imageBytes 이미지 바이트
     * @param contentType Tika 판정 MIME 타입. Vision 은 바이트에서 형식을 스스로 판정하므로
     *                    요청에 쓰이지 않는다 — 인터페이스 계약 유지를 위해 받는다.
     * @return 추출 결과 (판별은 호출자가 한다)
     * @throws InvalidRequestDataException 바이트가 비었거나({@code INVALID_IMAGE}),
     *         응답을 해석할 수 없는 경우({@code OCR_RECOGNITION_FAILED})
     * @throws ServiceUnavailableException 연결 실패 타임아웃 5xx ({@code OCR_SERVICE_UNAVAILABLE})
     * @throws InternalServerErrorException 4xx — 잘못된 API 키 요청 형식 할당량 초과
     */
    @Override
    public HealthCertificateOcrResult extract(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        String requestBody = buildRequestBody(imageBytes);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(readTimeoutSeconds))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        // API 키가 URI 쿼리에 있으므로 URI 를 로깅하면 키가 로그에 남는다.
        // 키 없는 엔드포인트와 키 길이만 남긴다. 이미지 바이트와 OCR 원응답도 로깅하지 않는다 —
        // 견주 성명 주소가 포함된다.
        log.info("Google Vision OCR 요청: endpoint={}, imageBytes={}, apiKeyLength={}",
                 endpoint, imageBytes.length, apiKey == null ? 0 : apiKey.length());

        HttpResponse<String> response = send(request);

        int statusCode = response.statusCode();

        if (statusCode >= 500) {
            log.error("Google Vision OCR 서버 오류: status={}", statusCode);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }

        if (statusCode >= 400) {
            // 잘못된 키 요청 형식 할당량 초과 — 사용자 잘못이 아니므로 500 으로 올린다.
            // 응답 본문에 키가 포함되지 않음을 전제한다 (Vision 은 키를 되돌려주지 않는다).
            log.error("Google Vision OCR 요청 거부: status={}, body={}", statusCode, response.body());
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return parse(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException e) {
            // 인터럽트 플래그를 복원한다 (OAuthClient 와 동일한 방침).
            Thread.currentThread().interrupt();
            log.error("Google Vision OCR 호출이 인터럽트되었습니다.", e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            log.error("Google Vision OCR 연결 실패 또는 타임아웃", e);
            throw new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * images:annotate 요청 본문을 만든다.
     *
     * DOCUMENT_TEXT_DETECTION 은 표 라벨이 빽빽한 인쇄 문서에 맞는 모드다.
     * languageHints 로 한국어를 명시하면 한글 인식률이 올라간다.
     */
    private String buildRequestBody(byte[] imageBytes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "requests", List.of(Map.of(
                    "image", Map.of(
                        "content", Base64.getEncoder().encodeToString(imageBytes)
                    ),
                    "features", List.of(Map.of("type", "DOCUMENT_TEXT_DETECTION")),
                    "imageContext", Map.of("languageHints", List.of(languageHint))
                ))
            ));
        } catch (Exception e) {
            log.error("Google Vision OCR 요청 본문 생성 실패", e);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Vision 응답을 OcrTextBlock 목록으로 바꿔 추출기에 넘긴다.
     *
     * fullTextAnnotation.text 를 줄바꿈으로 쪼개 한 줄을 한 블록으로 쓴다 —
     * 진단서는 "라벨: 값" 이 한 줄에 오는 서식이라 추출기의 기대와 맞는다.
     * 줄 단위 신뢰도는 응답에 없으므로 블록 신뢰도 평균을 모든 줄에 같게 부여한다.
     */
    private HealthCertificateOcrResult parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            // 200 인데 본문을 읽을 수 없다 — 사용자에게는 인식 실패로 알린다.
            log.error("Google Vision OCR 응답 파싱 실패", e);
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode responses = root.path("responses");
        if (!responses.isArray() || responses.isEmpty()) {
            log.error("Google Vision OCR 응답에 responses 가 없습니다.");
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode first = responses.get(0);

        // Vision 은 이미지 단위 실패를 200 + error 로 알린다. 상태 코드만 보면 놓친다.
        JsonNode error = first.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            log.error("Google Vision OCR 이미지 처리 실패: code={}, message={}",
                      error.path("code").asInt(), error.path("message").asText(""));
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        JsonNode annotation = first.path("fullTextAnnotation");
        if (annotation.isMissingNode() || annotation.isNull()) {
            // 글자가 전혀 없는 사진이다.
            log.info("Google Vision OCR: 텍스트가 검출되지 않았습니다.");
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        String fullText = annotation.path("text").asText("");
        double confidence = averageBlockConfidence(annotation);

        List<OcrTextBlock> blocks = new ArrayList<>();
        for (String line : fullText.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(new OcrTextBlock(trimmed, confidence));
            }
        }

        return fieldExtractor.extract(blocks);
    }

    /**
     * pages[].blocks[].confidence 의 평균. 값이 하나도 없으면 CONFIDENCE_FALLBACK 을 쓴다.
     *
     * 0.0 으로 폴백하면 ocr.certificate.min-confidence 게이트가 모든 요청을 거부해
     * 기능이 전면 중단된다. 품질 게이트가 느슨해지는 대가를 치르더라도 전면 거부를 피한다.
     * 이 경로로 들어오면 log.warn 이 유일한 신호이므로 운영에서 모니터링한다.
     */
    private double averageBlockConfidence(JsonNode annotation) {
        double sum = 0.0;
        int count = 0;

        for (JsonNode page : annotation.path("pages")) {
            for (JsonNode block : page.path("blocks")) {
                JsonNode confidence = block.path("confidence");
                if (confidence.isNumber()) {
                    sum += confidence.asDouble();
                    count++;
                }
            }
        }

        if (count == 0) {
            log.warn("Google Vision OCR 응답에 block confidence 가 없습니다. {} 로 폴백합니다 — "
                     + "품질 게이트가 동작하지 않습니다.", CONFIDENCE_FALLBACK);
            return CONFIDENCE_FALLBACK;
        }

        return sum / count;
    }
}
```

> **벤더 응답 스키마는 검증이 필요하다.** 위 코드는 `responses[0].fullTextAnnotation.text` / `.pages[].blocks[].confidence` 구조를 전제한다. **실제 응답을 한 번 받아 `parse`의 경로가 맞는지 확인하고, 다르면 `parse`만 고친다** — 인터페이스와 추출기는 영향이 없다. 특히 확인할 것 두 가지: (1) `blocks[].confidence`가 실제로 내려오는지(안 내려오면 위 폴백이 상시 발동해 품질 게이트가 꺼진 상태가 된다), (2) 진단서 서식에서 `text`의 줄바꿈이 `라벨: 값` 경계와 맞는지(안 맞으면 매핑 설계표의 (B)로 올린다). 확인 전까지 Task 15·16은 가짜 구현으로 테스트되므로 진행이 막히지 않는다.

- [ ] **Step 4: 설정 키를 세 yml에 넣는다**

Task 7에서 만든 `ocr:` 블록에 이어 붙인다. `application-dev.yml` · `application-prod.yml`:

```yaml
ocr:
  user-interval:
    seconds: ${OCR_USER_INTERVAL_SECONDS:30}
  google:
    # GCP 콘솔에서 Cloud Vision API 를 활성화한 뒤 발급한 API 키.
    # 반드시 "API 제한"에서 Cloud Vision API 만 허용하도록 제한한다.
    api-key: ${GOOGLE_VISION_API_KEY}
    endpoint: https://vision.googleapis.com/v1/images:annotate
    # 한국어 진단서이므로 languageHints 로 ko 를 명시한다.
    language-hint: ko
    connect-timeout-seconds: 10
    read-timeout-seconds: 15
  certificate:
    # 전체 평균 신뢰도 임계값. 미달이면 OCR_RECOGNITION_FAILED 로 거부한다.
    min-confidence: 0.75
```

`src/test/resources/application-test.yml`에는 **반드시 기본값을 준다.** 엔드포인트 기본값은 **절대 외부로 나가지 않는 로컬 주소**로 둔다 — 테스트가 실수로 과금되는 실제 Vision 호출을 하지 않게 하는 안전장치다.

```yaml
ocr:
  user-interval:
    seconds: ${OCR_USER_INTERVAL_SECONDS:30}
  google:
    api-key: ${GOOGLE_VISION_API_KEY:test-api-key}
    endpoint: ${GOOGLE_VISION_ENDPOINT:http://localhost:9999/v1/images:annotate}
    language-hint: ko
    connect-timeout-seconds: 10
    read-timeout-seconds: 15
  certificate:
    min-confidence: 0.75
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'GoogleVisionOcrClientUT'
```

기대: 13개 테스트 PASS.

> `httpClient`가 `private final` 필드 초기화라 `ReflectionTestUtils.setField`로 교체한다. `final` 필드에도 동작하지만, 실패하면 필드에서 `final`을 떼고 주석으로 이유를 남긴다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/clients/ocr/GoogleVisionOcrClient.java \
        src/main/resources/application-dev.yml src/main/resources/application-prod.yml \
        src/test/resources/application-test.yml \
        src/test/java/com/smarthealthdog/backend/clients/ocr/GoogleVisionOcrClientUT.java
git commit -m "Feat: Google Vision OCR 클라이언트 구현 및 타임아웃 에러 매핑 적용"
```

---
## Task 15: 동기 흐름 — "판별 실패 시 미저장" 보증

**이 작업이 기능의 핵심이다.** 순서가 보증 그 자체다 (SPEC §5.1):

```
1. @PreAuthorize 권한 검사 통과
   → Long ownerId = Long.parseLong(userDetails.getUsername())

2. petService.get(petId) → pet.getOwner().getId() != ownerId 이면 404 (403 아님)

3. 빈도 제한 검사 (유형별, 시도 기준)
   → 위반이면 400 REQUEST_TOO_FREQUENT, 외부 API를 호출하지 않고 종료
   → 통과 시 "시도" 를 먼저 기록한다 (판별 성공 여부와 무관)

4. FileUploadService.validateImageFile(file)
   (빈 파일 / 확장자 / Tika MIME / 용량)

5. 외부 OCR API 호출 (타임아웃 적용)
   → 호출 실패·타임아웃이면 여기서 종료 (503)

6. 판별 규칙 평가 (문서 종류 / 필수 항목 / 신뢰도)
   → 미달이면 여기서 종료 (400)

   ══════ 여기까지 DB·S3에 어떤 쓰기도 일어나지 않는다 ══════
   (3단계의 시도 기록은 Submission 행이 아니다)

7. imageUploader.storeSubmissionImage(event, "health-certificates/")   → key 반환

8. @Transactional 안에서 Submission + PetHealthCertificate INSERT

9. 201 + submission_id 포함 응답
```

**5·6단계가 7·8단계보다 앞에 있다는 사실이 "AI 판별 실패 시 업로드 불가"의 전부다.** 판별이 끝나기 전에는 저장 행위가 존재하지 않으므로 롤백할 것도 없다.

**두 개의 빈으로 쪼개는 이유 — Spring 자기호출 함정.** 외부 HTTP 호출을 `@Transactional` 안에 두지 않는다(OCR 응답을 최대 15초 기다리는 동안 DB 커넥션을 점유하면 커넥션 풀이 빠르게 고갈된다). 그래서 진입 메서드는 비트랜잭션이고 저장만 `@Transactional`이어야 한다. **같은 클래스 안에서 `this.persist(...)`를 호출하면 프록시를 거치지 않아 `@Transactional`이 무시된다.** 따라서 저장 메서드를 **별도 빈(`HealthCertificateWriter`)**에 둔다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/services/HealthCertificateWriter.java`
- Create: `src/main/java/com/smarthealthdog/backend/services/HealthCertificateService.java`
- Test: `src/test/java/com/smarthealthdog/backend/services/HealthCertificateServiceUT.java`

**Interfaces:**
- Consumes: `DiagnosisAttemptLimiter.checkAndRecordAttempt` (Task 7), `FileUploadService.validateImageFile` (Task 1), `FileUtils.detectImageMimeType` (Task 2), `ImageUploader.storeSubmissionImage` / `delete` (Task 3), `SubmissionService.createCompletedSubmission` (Task 11), `PetHealthCertificateRepository` (Task 11), `HealthCertificateOcrClient.extract` (Task 13)
- Produces:
    - `HealthCertificateWriter.persist(Pet pet, String photoKey, HealthCertificateOcrResult ocr) → PetHealthCertificate` (`@Transactional`)
    - `HealthCertificateService.register(MultipartFile image, Long petId, Long ownerId) → PetHealthCertificate` (**비트랜잭션**)
    - Task 16이 `register`를 호출한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/services/HealthCertificateServiceUT.java` 신규 생성. **"저장 없음"을 응답 코드가 아니라 mock 호출 횟수로 검증**하는 것이 이 기능의 핵심 테스트다(SPEC §12.1).

```java
package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.exceptions.ServiceUnavailableException;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HealthCertificateServiceUT {

    @InjectMocks
    private HealthCertificateService healthCertificateService;

    @Mock private PetService petService;
    @Mock private DiagnosisAttemptLimiter diagnosisAttemptLimiter;
    @Mock private FileUploadService fileUploadService;
    @Mock private HealthCertificateOcrClient ocrClient;
    @Mock private ImageUploader imageUploader;
    @Mock private HealthCertificateWriter healthCertificateWriter;

    private Pet pet;
    private MockMultipartFile image;

    private static final byte[] JPEG_BYTES =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthCertificateService, "minConfidence", 0.75);

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);

        pet = mock(Pet.class);
        when(pet.getOwner()).thenReturn(owner);
        when(petService.get(3L)).thenReturn(pet);

        image = new MockMultipartFile("image", "cert.jpg", "image/jpeg", JPEG_BYTES);
    }

    private HealthCertificateOcrResult 정상_OCR결과() {
        return new HealthCertificateOcrResult(
            "초코", "Canine", "Pug", "Castrated Male", "흰색", "16년 3개월", null,
            "심장비대", LocalDate.of(2020, 10, 28), "2020.10.28",
            LocalDate.of(2020, 10, 28), "2020.10.28",
            "경과 관찰 필요", null,
            0.9124, true
        );
    }

    private HealthCertificateOcrResult OCR결과(
        String diseaseName, String diagnosedDateText, double confidence, boolean keywordFound
    ) {
        return new HealthCertificateOcrResult(
            null, null, null, null, null, null, null,
            diseaseName, null, null, null, diagnosedDateText, null, null,
            confidence, keywordFound
        );
    }

    /** DB·S3 에 어떤 쓰기도 일어나지 않았음을 확인한다. */
    private void 아무것도_저장되지_않았다() {
        verify(imageUploader, never()).storeSubmissionImage(any(), anyString());
        verify(healthCertificateWriter, never()).persist(any(), anyString(), any());
    }

    @Test
    void 정상_흐름은_S3_업로드_후_저장하고_201_대상을_반환한다() {
        when(ocrClient.extract(any(), eq("image/jpeg"))).thenReturn(정상_OCR결과());
        when(imageUploader.storeSubmissionImage(any(), eq("health-certificates/")))
            .thenReturn("health-certificates/abc.jpg");

        PetHealthCertificate saved = mock(PetHealthCertificate.class);
        when(healthCertificateWriter.persist(eq(pet), eq("health-certificates/abc.jpg"), any()))
            .thenReturn(saved);

        PetHealthCertificate result = healthCertificateService.register(image, 3L, 7L);

        assertEquals(saved, result);

        // 순서 보증: 제한 → 검증 → OCR → 업로드 → 저장
        var inOrder = org.mockito.Mockito.inOrder(
            diagnosisAttemptLimiter, fileUploadService, ocrClient, imageUploader, healthCertificateWriter
        );
        inOrder.verify(diagnosisAttemptLimiter)
               .checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);
        inOrder.verify(fileUploadService).validateImageFile(image);
        inOrder.verify(ocrClient).extract(any(), eq("image/jpeg"));
        inOrder.verify(imageUploader).storeSubmissionImage(any(), eq("health-certificates/"));
        inOrder.verify(healthCertificateWriter).persist(any(), anyString(), any());
    }

    @Test
    void 타인의_반려동물이면_404이고_OCR을_호출하지_않는다() {
        User otherOwner = mock(User.class);
        when(otherOwner.getId()).thenReturn(99L);
        when(pet.getOwner()).thenReturn(otherOwner);

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        verify(ocrClient, never()).extract(any(), anyString());
        아무것도_저장되지_않았다();
    }

    @Test
    void 빈도_제한에_걸리면_유료_OCR을_호출하지_않는다() {
        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        // 제한이 OCR 호출보다 앞에 있어야 비용을 막는다.
        verify(ocrClient, never()).extract(any(), anyString());
        아무것도_저장되지_않았다();
    }

    @Test
    void 판별_실패를_연속_요청하면_OCR이_한_번만_호출된다() {
        // 검토 집중 지점: 실패 요청은 Submission 행을 남기지 않으므로,
        // Submission 기준 제한이라면 영원히 제한되지 않는다.
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과("심장비대", "2020.10.28", 0.10, true));

        assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        // 두 번째 요청에서 제한기가 막는다 (시도가 기록되었기 때문).
        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        verify(ocrClient, org.mockito.Mockito.times(1)).extract(any(), anyString());
    }

    @Test
    void 이미지_검증_실패면_OCR을_호출하지_않는다() {
        doThrow(new InvalidRequestDataException(ErrorCode.INVALID_IMAGE))
            .when(fileUploadService).validateImageFile(image);

        assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        verify(ocrClient, never()).extract(any(), anyString());
        아무것도_저장되지_않았다();
    }

    @Test
    void OCR_타임아웃이면_503이고_아무것도_저장하지_않는다() {
        when(ocrClient.extract(any(), anyString()))
            .thenThrow(new ServiceUnavailableException(ErrorCode.OCR_SERVICE_UNAVAILABLE));

        ServiceUnavailableException e = assertThrows(
            ServiceUnavailableException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.OCR_SERVICE_UNAVAILABLE, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 진단서가_아닌_이미지는_NOT_A_HEALTH_CERTIFICATE이고_저장하지_않는다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과("심장비대", "2020.10.28", 0.95, false));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.NOT_A_HEALTH_CERTIFICATE, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 병명이_없으면_OCR_REQUIRED_FIELD_MISSING이고_저장하지_않는다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과(null, "2020.10.28", 0.95, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 진단_연원일_원문이_없으면_OCR_REQUIRED_FIELD_MISSING이다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과("심장비대", null, 0.95, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.OCR_REQUIRED_FIELD_MISSING, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 신뢰도_미달이면_OCR_RECOGNITION_FAILED이고_저장하지_않는다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과("심장비대", "2020.10.28", 0.74, true));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals(ErrorCode.OCR_RECOGNITION_FAILED, e.getErrorCode());
        아무것도_저장되지_않았다();
    }

    @Test
    void 임계값과_같은_신뢰도는_통과한다() {
        when(ocrClient.extract(any(), anyString()))
            .thenReturn(OCR결과("심장비대", "2020.10.28", 0.75, true));
        when(imageUploader.storeSubmissionImage(any(), anyString()))
            .thenReturn("health-certificates/abc.jpg");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenReturn(mock(PetHealthCertificate.class));

        healthCertificateService.register(image, 3L, 7L);

        verify(healthCertificateWriter).persist(any(), anyString(), any());
    }

    @Test
    void 날짜_파싱_실패해도_원문이_있으면_등록된다() {
        HealthCertificateOcrResult ocr = new HealthCertificateOcrResult(
            null, null, null, null, null, null, null,
            "심장비대", null, "20Z0.1O.28",
            null, "20Z0.1O.28",   // 파싱값 null, 원문 보존
            null, null,
            0.95, true
        );
        when(ocrClient.extract(any(), anyString())).thenReturn(ocr);
        when(imageUploader.storeSubmissionImage(any(), anyString()))
            .thenReturn("health-certificates/abc.jpg");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenReturn(mock(PetHealthCertificate.class));

        healthCertificateService.register(image, 3L, 7L);

        verify(healthCertificateWriter).persist(eq(pet), eq("health-certificates/abc.jpg"), eq(ocr));
    }

    @Test
    void S3는_성공했는데_DB가_실패하면_보상_삭제를_호출한다() {
        when(ocrClient.extract(any(), anyString())).thenReturn(정상_OCR결과());
        when(imageUploader.storeSubmissionImage(any(), anyString()))
            .thenReturn("health-certificates/abc.jpg");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenThrow(new RuntimeException("DB 장애"));

        assertThrows(RuntimeException.class, () -> healthCertificateService.register(image, 3L, 7L));

        verify(imageUploader).delete("health-certificates/abc.jpg");
    }

    @Test
    void 보상_삭제까지_실패해도_원래_예외가_올라간다() {
        when(ocrClient.extract(any(), anyString())).thenReturn(정상_OCR결과());
        when(imageUploader.storeSubmissionImage(any(), anyString()))
            .thenReturn("health-certificates/abc.jpg");
        when(healthCertificateWriter.persist(any(), anyString(), any()))
            .thenThrow(new RuntimeException("DB 장애"));
        doThrow(new RuntimeException("S3 장애"))
            .when(imageUploader).delete("health-certificates/abc.jpg");

        RuntimeException e = assertThrows(
            RuntimeException.class,
            () -> healthCertificateService.register(image, 3L, 7L)
        );

        assertEquals("DB 장애", e.getMessage(), "원래 원인이 보상 삭제 실패로 덮이면 안 된다");
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'HealthCertificateServiceUT'
```

기대: 컴파일 실패 — `HealthCertificateService`, `HealthCertificateWriter`가 없다.

- [ ] **Step 3: 저장 전용 빈을 만든다**

`services/HealthCertificateWriter.java`:

```java
package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 진단서 기록 저장 전용 빈.
 *
 * 별도 빈으로 분리한 이유는 Spring 프록시 때문이다 —
 * HealthCertificateService 는 외부 HTTP 호출을 트랜잭션 밖에 두어야 해서
 * 비트랜잭션이고, 같은 클래스 안에서 this.persist(...) 를 호출하면
 * 프록시를 거치지 않아 @Transactional 이 무시된다.
 */
@Service
@RequiredArgsConstructor
public class HealthCertificateWriter {

    private final SubmissionService submissionService;
    private final PetHealthCertificateRepository petHealthCertificateRepository;

    /**
     * 제출과 진단서 기록을 한 트랜잭션에서 저장한다.
     *
     * createCompletedSubmission 도 @Transactional 이지만 기본 전파(REQUIRED)라
     * 이 트랜잭션에 참여한다 — 둘 중 하나가 실패하면 함께 롤백된다.
     *
     * @param pet 반려동물
     * @param photoKey S3 업로드가 끝난 object key
     * @param ocr 판별을 통과한 OCR 결과
     * @return 저장된 진단서 기록
     * @throws InvalidRequestDataException 같은 제출에 진단서가 이미 있는 경우
     */
    @Transactional
    public PetHealthCertificate persist(Pet pet, String photoKey, HealthCertificateOcrResult ocr) {
        Submission submission = submissionService.createCompletedSubmission(
            pet,
            SubmissionTypeEnum.HEALTH_CERTIFICATE,
            photoKey
        );

        // uq_pet_health_certificates_submission 위반이 500 으로 노출되기 전에 막는다.
        // 새로 만든 제출이라 정상 흐름에서는 걸리지 않지만, 재시도 경로에서 걸릴 수 있다.
        if (petHealthCertificateRepository.existsBySubmissionId(submission.getId())) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        PetHealthCertificate certificate = PetHealthCertificate.builder()
            .pet(pet)
            .submission(submission)
            .animalName(ocr.animalName())
            .speciesText(ocr.speciesText())
            .breed(ocr.breed())
            .genderText(ocr.genderText())
            .coatColor(ocr.coatColor())
            .ageText(ocr.ageText())
            .features(ocr.features())
            .diseaseName(ocr.diseaseName())
            .onsetDate(ocr.onsetDate())
            .onsetDateText(ocr.onsetDateText())
            .diagnosedDate(ocr.diagnosedDate())
            .diagnosedDateText(ocr.diagnosedDateText())
            .prognosis(ocr.prognosis())
            .remarks(ocr.remarks())
            // NUMERIC(5,4) 이므로 소수 4자리로 맞춘다. 반올림하지 않으면 저장 시 잘린다.
            .ocrConfidence(BigDecimal.valueOf(ocr.averageConfidence())
                                     .setScale(4, RoundingMode.HALF_UP))
            .manuallyEdited(false)
            .build();

        return petHealthCertificateRepository.save(certificate);
    }
}
```

- [ ] **Step 4: 오케스트레이터를 만든다**

`services/HealthCertificateService.java`:

```java
package com.smarthealthdog.backend.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.utils.FileUtils;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 진단서 등록 동기 흐름.
 *
 * 클래스에 @Transactional 을 붙이지 않는다 —
 * OCR 응답을 최대 15초 기다리는 동안 DB 커넥션을 점유하면
 * 커넥션 풀이 빠르게 고갈된다. 저장만 HealthCertificateWriter 에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCertificateService {

    private static final String S3_PREFIX = "health-certificates/";

    private final PetService petService;
    private final DiagnosisAttemptLimiter diagnosisAttemptLimiter;
    private final FileUploadService fileUploadService;
    private final HealthCertificateOcrClient ocrClient;
    private final ImageUploader imageUploader;
    private final HealthCertificateWriter healthCertificateWriter;

    @Value("${ocr.certificate.min-confidence}")
    private double minConfidence;

    /**
     * 진단서 이미지를 OCR 로 읽어 건강검진 기록을 만든다.
     *
     * 판별에 실패하면 DB 행도 S3 객체도 만들지 않는다 —
     * OCR 호출과 판별이 저장보다 앞에 있으므로 롤백할 대상이 애초에 없다.
     *
     * @param image 진단서 이미지
     * @param petId 반려동물 ID
     * @param ownerId 요청자(소유자) 내부 ID
     * @return 저장된 진단서 기록
     * @throws ResourceNotFoundException 반려동물이 없거나 타인 소유인 경우 {@code RESOURCE_NOT_FOUND}
     * @throws InvalidRequestDataException 빈도 제한 {@code REQUEST_TOO_FREQUENT},
     *         이미지 위반 {@code INVALID_IMAGE}, 진단서 아님 {@code NOT_A_HEALTH_CERTIFICATE},
     *         필수 항목 누락 {@code OCR_REQUIRED_FIELD_MISSING},
     *         신뢰도 미달 {@code OCR_RECOGNITION_FAILED}
     */
    public PetHealthCertificate register(MultipartFile image, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID 와 Owner ID 는 null 일 수 없습니다.");
        }

        // 2. 소유권 — 타인의 반려동물 존재 여부를 노출하지 않기 위해 404 (403 아님)
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 3. 빈도 제한 — 유료 OCR 호출보다 반드시 앞에 둔다.
        //    통과 시 시도가 즉시 기록되므로, 이후 판별 실패해도 다음 요청이 제한된다.
        diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // 4. 이미지 검증 — 기존 메서드를 그대로 재사용한다.
        fileUploadService.validateImageFile(image);

        byte[] imageBytes = readBytes(image);
        String detectedMimeType = detectMimeType(imageBytes);

        // 5. 외부 OCR 호출 — 실패/타임아웃은 클라이언트가 503 으로 매핑한다.
        HealthCertificateOcrResult ocr = ocrClient.extract(imageBytes, detectedMimeType);

        // 6. 판별 규칙 — 미달이면 여기서 끝난다.
        validateOcrResult(ocr);

        // ══════ 여기까지 DB·S3 에 어떤 쓰기도 없었다 ══════

        // 7. S3 업로드
        String photoKey = imageUploader.storeSubmissionImage(
            new SubmissionImageUploadEvent(null, imageBytes, image.getOriginalFilename(), detectedMimeType),
            S3_PREFIX
        );

        // 8. 저장 — 실패 시 보상 삭제로 고아 객체를 남기지 않는다.
        try {
            return healthCertificateWriter.persist(pet, photoKey, ocr);
        } catch (RuntimeException e) {
            compensateDelete(photoKey, e);
            throw e;
        }
    }

    /**
     * 판별 규칙 (SPEC 6.1).
     * 나머지 10개 항목은 비어도 등록을 허용한다 — 진단서마다 빈칸이 흔하고
     * 수정 화면에서 사용자가 채울 수 있다.
     */
    private void validateOcrResult(HealthCertificateOcrResult ocr) {
        // 1. 문서 종류 — 고유 키워드가 하나도 없으면 진단서가 아니다.
        if (!ocr.documentKeywordFound()) {
            throw new InvalidRequestDataException(ErrorCode.NOT_A_HEALTH_CERTIFICATE);
        }

        // 2. 필수 항목 — 병명과 진단 연원일 원문.
        //    날짜는 파싱값이 아니라 원문을 본다. 파싱 실패가 등록을 막지 않는다.
        if (isBlank(ocr.diseaseName()) || isBlank(ocr.diagnosedDateText())) {
            throw new InvalidRequestDataException(ErrorCode.OCR_REQUIRED_FIELD_MISSING);
        }

        // 3. 신뢰도 임계값 — 임계값과 같은 값은 통과시킨다.
        if (ocr.averageConfidence() < minConfidence) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }
    }

    private void compensateDelete(String photoKey, RuntimeException cause) {
        try {
            imageUploader.delete(photoKey);
        } catch (RuntimeException deleteFailure) {
            // 보상 삭제 실패는 원래 원인을 덮지 않는다.
            // 고아 객체 키를 로그에 남겨 수동 정리가 가능하게 한다.
            log.error("보상 삭제 실패 — 고아 S3 객체가 남았습니다. key={}", photoKey, deleteFailure);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    private String detectMimeType(byte[] imageBytes) {
        try {
            String mimeType = FileUtils.detectImageMimeType(new ByteArrayInputStream(imageBytes));
            if (mimeType == null) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
            }

            return mimeType;
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

> **`SubmissionImageUploadEvent`의 첫 인자가 `null`이다.** 이 record는 `(Submission, byte[], String, String)` 모양인데, 동기 경로는 **업로드가 제출 생성보다 앞서므로** 아직 `Submission`이 없다. 이벤트로 발행하지 않고 값 객체로만 쓰기 때문에 문제가 없다(SPEC §2.4). `storeSubmissionImage`는 `fileBytes`만 보므로 `null` submission을 역참조하지 않는다 — Task 3 Step 4의 구현이 그렇게 되어 있는지 확인한다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'HealthCertificateServiceUT'
```

기대: 전부 PASS. 특히 `아무것도_저장되지_않았다()`를 쓰는 7개 테스트가 "실패 시 미저장" 보증의 실체다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/services/HealthCertificateWriter.java \
        src/main/java/com/smarthealthdog/backend/services/HealthCertificateService.java \
        src/test/java/com/smarthealthdog/backend/services/HealthCertificateServiceUT.java
git commit -m "Feat: 진단서 동기 등록 흐름 구현 및 판별 실패 시 미저장 보증"
```

---

## Task 16: `POST /api/pets/{id}/submissions/certificate`

**왜 `PetController`인가:** **신규 컨트롤러를 만들지 않는다.** 기존 눈·소변 업로드가 `PetController`의 `/{id}/submissions/eye` · `/urine`에 있다. 같은 모양으로 추가하면 프론트가 동일한 규칙으로 다룬다.

**기존 실수를 반복하지 않는다.** `POST /api/pets/{id}/submissions/eye`는 `201`에 **빈 본문**을 돌려줘서 클라이언트가 방금 만든 리소스를 알 수 없다. 진단서는 `submission_id`를 포함한 전체 객체를 돌려준다 — 프론트가 이 응답을 그대로 수정 화면에 띄울 수 있다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateResult.java`
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateCreatedResponse.java`
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateMapper.java`
- Modify: `src/main/java/com/smarthealthdog/backend/controllers/PetController.java:178`
- Test: `src/test/java/com/smarthealthdog/backend/dto/health/HealthCertificateMapperUT.java`

**Interfaces:**
- Consumes: `HealthCertificateService.register` (Task 15), `PetHealthCertificate` (Task 11)
- Produces:
    - `HealthCertificateResult` — 12개 항목 + `ocr_confidence` + `is_manually_edited` (JSON은 snake_case)
    - `HealthCertificateCreatedResponse` — `submission_id` · `pet_id` · `submitted_at` + 위 항목을 평탄화한 201 본문
    - `HealthCertificateMapper.toResult(PetHealthCertificate)` / `toCreatedResponse(PetHealthCertificate)`
    - Task 17·18이 `toResult`를 재사용한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/dto/health/HealthCertificateMapperUT.java` 신규 생성. **검토 집중 지점 2**(날짜 NULL + 원문 보존)를 여기서 못박는다.

```java
package com.smarthealthdog.backend.dto.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;

public class HealthCertificateMapperUT {

    private final HealthCertificateMapper mapper = new HealthCertificateMapper();

    private PetHealthCertificate 기록(LocalDate diagnosedDate, String diagnosedDateText) {
        Pet pet = org.mockito.Mockito.mock(Pet.class);
        org.mockito.Mockito.when(pet.getId()).thenReturn(3L);
        org.mockito.Mockito.when(pet.getName()).thenReturn("초코");

        Submission submission = org.mockito.Mockito.mock(Submission.class);
        java.util.UUID id = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(submission.getId()).thenReturn(id);
        org.mockito.Mockito.when(submission.getSubmittedAt())
            .thenReturn(Instant.parse("2026-10-03T12:00:00Z"));

        return PetHealthCertificate.builder()
            .pet(pet)
            .submission(submission)
            .animalName("초코")
            .speciesText("Canine")
            .breed("Pug")
            .genderText("Castrated Male")
            .coatColor("흰색")
            .ageText("16년 3개월")
            .diseaseName("심장비대, 폐침윤")
            .onsetDate(LocalDate.of(2020, 10, 28))
            .onsetDateText("2020.10.28")
            .diagnosedDate(diagnosedDate)
            .diagnosedDateText(diagnosedDateText)
            .prognosis("경과 관찰 필요")
            .ocrConfidence(new BigDecimal("0.9124"))
            .manuallyEdited(false)
            .build();
    }

    @Test
    void toResult가_12개_항목과_메타를_옮긴다() {
        HealthCertificateResult result =
            mapper.toResult(기록(LocalDate.of(2020, 10, 28), "2020.10.28"));

        assertEquals("초코", result.animalName());
        assertEquals("Canine", result.speciesText());
        assertEquals("Pug", result.breed());
        assertEquals("Castrated Male", result.genderText());
        assertEquals("흰색", result.coatColor());
        assertEquals("16년 3개월", result.ageText());
        assertEquals("심장비대, 폐침윤", result.diseaseName());
        assertEquals(LocalDate.of(2020, 10, 28), result.diagnosedDate());
        assertEquals("2020.10.28", result.diagnosedDateText());
        assertEquals("경과 관찰 필요", result.prognosis());
        assertEquals(new BigDecimal("0.9124"), result.ocrConfidence());
        assertEquals(false, result.manuallyEdited());
    }

    @Test
    void 날짜_파싱값이_null이어도_원문은_함께_내려간다() {
        // 검토 집중 지점 2: 파싱 실패 행이 정상적으로 존재한다.
        // 파싱값만 보고 원문을 빼면 사용자는 입력한 날짜가 사라진 것으로 본다.
        HealthCertificateResult result = mapper.toResult(기록(null, "20Z0.1O.28"));

        assertNull(result.diagnosedDate());
        assertEquals("20Z0.1O.28", result.diagnosedDateText());
    }

    @Test
    void toCreatedResponse가_submission_id와_pet_id를_포함한다() {
        PetHealthCertificate certificate = 기록(LocalDate.of(2020, 10, 28), "2020.10.28");

        HealthCertificateCreatedResponse response = mapper.toCreatedResponse(certificate);

        assertEquals(certificate.getSubmission().getId(), response.submissionId());
        assertEquals(3L, response.petId());
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), response.submittedAt());
        assertEquals("심장비대, 폐침윤", response.result().diseaseName());
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'HealthCertificateMapperUT'
```

기대: 컴파일 실패 — DTO와 매퍼가 없다.

- [ ] **Step 3: 결과 DTO를 만든다**

`dto/health/HealthCertificateResult.java`. 이 저장소에는 전역 네이밍 전략이 없어 기본 camelCase가 적용되므로 `@JsonProperty`로 snake_case를 고정한다.

```java
package com.smarthealthdog.backend.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 진단서 결과 — 12개 항목 + 메타.
 *
 * SubmissionDetail&lt;T&gt; 의 results 요소로도 쓰이고,
 * 201 / PATCH 응답 안에도 들어간다.
 *
 * 견주 성명 주소 필드는 없다 (SPEC 1.5 범위 밖).
 */
public record HealthCertificateResult(
    @JsonProperty("animal_name") String animalName,
    @JsonProperty("species_text") String speciesText,
    @JsonProperty("breed") String breed,
    @JsonProperty("gender_text") String genderText,
    @JsonProperty("coat_color") String coatColor,
    @JsonProperty("age_text") String ageText,
    @JsonProperty("features") String features,
    @JsonProperty("disease_name") String diseaseName,

    /** 파싱 성공 시에만 값이 있다. null 이어도 아래 원문은 항상 있다. */
    @JsonProperty("onset_date") LocalDate onsetDate,
    @JsonProperty("onset_date_text") String onsetDateText,
    @JsonProperty("diagnosed_date") LocalDate diagnosedDate,
    @JsonProperty("diagnosed_date_text") String diagnosedDateText,

    @JsonProperty("prognosis") String prognosis,
    @JsonProperty("remarks") String remarks,
    @JsonProperty("ocr_confidence") BigDecimal ocrConfidence,
    @JsonProperty("is_manually_edited") boolean manuallyEdited
) {}
```

`dto/health/HealthCertificateCreatedResponse.java`:

```java
package com.smarthealthdog.backend.dto.health;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * POST /api/pets/{id}/submissions/certificate 의 201 본문.
 *
 * 기존 POST .../submissions/eye 는 201 에 빈 본문을 돌려줘서
 * 클라이언트가 방금 만든 리소스를 알 수 없었다. 같은 실수를 반복하지 않는다.
 *
 * result 를 @JsonUnwrapped 로 평탄화해 명세의 평면 JSON 모양을 맞추면서도
 * 필드 매핑을 HealthCertificateResult 한 곳에만 둔다.
 */
public record HealthCertificateCreatedResponse(
    @JsonProperty("submission_id") UUID submissionId,
    @JsonProperty("pet_id") Long petId,
    @JsonProperty("submitted_at") Instant submittedAt,
    @JsonUnwrapped HealthCertificateResult result
) {}
```

> **`@JsonUnwrapped`를 쓰는 이유.** 명세 §4.2의 201 본문은 `submission_id`와 12개 항목이 **같은 레벨에 평탄하게** 놓인 모양이다. 필드를 복사해 두 번 선언하면 항목이 바뀔 때 한쪽만 고치는 버그가 생긴다. `@JsonUnwrapped`로 직렬화만 평탄화하고 매핑은 한 곳에 둔다. **직렬화 결과를 눈으로 확인한다** — record + `@JsonUnwrapped` 조합이 Jackson 버전에 따라 동작하지 않으면, `HealthCertificateCreatedResponse`에 12개 필드를 펼치고 `HealthCertificateMapper` 안에서만 복사한다(복사 지점이 한 곳이면 허용 가능하다).

- [ ] **Step 4: 매퍼를 만든다**

`dto/health/HealthCertificateMapper.java`:

```java
package com.smarthealthdog.backend.dto.health;

import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.domain.PetHealthCertificate;

/**
 * 진단서 엔티티 → 응답 DTO.
 *
 * SubmissionMapper 와 분리한 이유:
 * SubmissionMapper 는 ImgUtils 를 주입받아 photoUrl 을 채우는데,
 * 진단서 응답은 photoUrl 을 쓰지 않는다(전용 이미지 엔드포인트만 사용).
 * 이미지 URL 생성 의존성을 이 매퍼에 끌어오지 않는다.
 */
@Component
public class HealthCertificateMapper {

    /**
     * @throws IllegalArgumentException certificate 가 null 인 경우
     */
    public HealthCertificateResult toResult(PetHealthCertificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("진단서 기록이 null일 수 없습니다.");
        }

        return new HealthCertificateResult(
            certificate.getAnimalName(),
            certificate.getSpeciesText(),
            certificate.getBreed(),
            certificate.getGenderText(),
            certificate.getCoatColor(),
            certificate.getAgeText(),
            certificate.getFeatures(),
            certificate.getDiseaseName(),
            certificate.getOnsetDate(),
            certificate.getOnsetDateText(),
            certificate.getDiagnosedDate(),
            certificate.getDiagnosedDateText(),
            certificate.getPrognosis(),
            certificate.getRemarks(),
            certificate.getOcrConfidence(),
            certificate.isManuallyEdited()
        );
    }

    public HealthCertificateCreatedResponse toCreatedResponse(PetHealthCertificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("진단서 기록이 null일 수 없습니다.");
        }

        return new HealthCertificateCreatedResponse(
            certificate.getSubmission().getId(),
            certificate.getPet().getId(),
            certificate.getSubmission().getSubmittedAt(),
            toResult(certificate)
        );
    }
}
```

- [ ] **Step 5: 컨트롤러에 엔드포인트를 추가한다**

`controllers/PetController.java` — 기존 `addUrineDiagnosis` 바로 아래에 추가한다. 컨트롤러는 얇게 유지한다.

```java
    /** 건강검진표(진단서) 이미지 제출 — 동기 OCR */
    @PostMapping("/{id}/submissions/certificate")
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<HealthCertificateCreatedResponse> addHealthCertificate(
            @PathVariable Long id,
            @RequestPart(value = "image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());

        PetHealthCertificate certificate = healthCertificateService.register(image, id, ownerId);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(healthCertificateMapper.toCreatedResponse(certificate));
    }
```

의존성과 import를 추가한다.

```java
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.HealthCertificateCreatedResponse;
import com.smarthealthdog.backend.dto.health.HealthCertificateMapper;
import com.smarthealthdog.backend.services.HealthCertificateService;

// 클래스 필드에 추가 (PetController 가 @RequiredArgsConstructor 인지 먼저 확인하고,
// @Autowired 필드 주입을 쓰는 기존 스타일이면 그 스타일을 따른다)
private final HealthCertificateService healthCertificateService;
private final HealthCertificateMapper healthCertificateMapper;
```

> **확인:** `PetController`의 주입 방식을 `head -60 src/main/java/com/smarthealthdog/backend/controllers/PetController.java`로 본다. `@RequiredArgsConstructor`면 `private final`을 추가하고, 기존 `@Autowired` 필드 스타일이면 그 파일의 관례를 따른다(신규 클래스는 생성자 주입이지만, 기존 파일의 스타일을 혼용하지 않는다).

- [ ] **Step 6: 테스트와 빌드를 확인한다**

```bash
./gradlew test --tests 'HealthCertificateMapperUT'
./gradlew build -x test
```

- [ ] **Step 7: 직렬화 모양을 눈으로 확인한다**

`@JsonUnwrapped`가 record에서 동작하는지 실제 기동으로 확인한다.

```bash
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Swagger(`/swagger-ui/index.html`)에서 `POST /api/pets/{id}/submissions/certificate`의 응답 스키마가 **평평한 snake_case**인지 본다. 중첩 객체(`result: {...}`)로 보이면 Step 3의 대안(필드 평탄화)으로 바꾼다.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateResult.java \
        src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateCreatedResponse.java \
        src/main/java/com/smarthealthdog/backend/dto/health/HealthCertificateMapper.java \
        src/main/java/com/smarthealthdog/backend/controllers/PetController.java \
        src/test/java/com/smarthealthdog/backend/dto/health/HealthCertificateMapperUT.java
git commit -m "Feat: 진단서 업로드 엔드포인트 추가 및 생성 응답에 submission_id 포함"
```

---
## Task 17: `GET /api/submissions/{id}/certificate` — 상세 조회

**왜 `SubmissionDetail<T>`를 재사용하는가:** 이미 제네릭이다 — `id`, `petInfo`, `type`, `photoUrl`, `status`, `submittedAt`, `completedAt`, `failureReason`, `Set<T> results`. **결과 타입만 갈아끼우면 된다.** 기존 `GET /{id}/eye`와 동일한 포장이라 프론트가 같은 코드로 다룬다.

> ⚠️ **`photoUrl`은 항상 `null`이다.** 기존 매퍼 메서드들은 `imgUtils.getImgUrl(...)`로 채우는데, `ProdImgUtils.getImgUrl`은 CloudFront 도메인이 설정돼 있으면 **무서명·무만료 URL**을 반환한다. 견주 성명·주소가 찍힌 이미지에 그 URL을 쓰면 그대로 공개된다. 이미지는 `GET /{id}/image`(Task 8)로만 받게 해서 무서명 URL이 새어나갈 경로를 원천 차단한다.

**Files:**
- Modify: `src/main/java/com/smarthealthdog/backend/dto/diagnosis/get/SubmissionMapper.java`
- Modify: `src/main/java/com/smarthealthdog/backend/services/SubmissionService.java`
- Modify: `src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java`
- Test: `src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java` (기존에 추가)

**Interfaces:**
- Consumes: `HealthCertificateMapper.toResult` (Task 16), `PetHealthCertificateRepository.findBySubmissionId` (Task 11), `SubmissionDetail<T>` (기존)
- Produces:
    - `SubmissionMapper.toSubmissionDetailForCertificate(Submission, PetHealthCertificate) → SubmissionDetail<HealthCertificateResult>`
    - `SubmissionService.getSubmissionAndCertificateById(UUID id, Long userId) → SubmissionDetail<HealthCertificateResult>`
    - Task 18이 PATCH 응답에 같은 메서드를 쓴다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SubmissionServiceTest.java`에 추가한다. H2 통합 테스트로 하는 이유는 `findBySubmissionId`의 `JOIN FETCH`가 실제로 동작하는지 확인해야 하기 때문이다.

```java
@Test
void getSubmissionAndCertificateById_photoUrl을_항상_null로_둔다() {
    // 무서명 CloudFront URL 이 새어나갈 경로를 막는다.
    PetHealthCertificate saved = healthCertificateWriter.persist(
        testPet, "health-certificates/abc.jpg", 정상_OCR결과()
    );
    UUID submissionId = saved.getSubmission().getId();

    SubmissionDetail<HealthCertificateResult> detail =
        submissionService.getSubmissionAndCertificateById(submissionId, testOwner.getId());

    assertNull(detail.getPhotoUrl(), "진단서 상세는 photoUrl 을 내려주지 않는다");
    assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, detail.getType());
    assertEquals("COMPLETED", detail.getStatus());
    assertEquals(1, detail.getResults().size());
}

@Test
void getSubmissionAndCertificateById_12개_항목을_results에_담는다() {
    PetHealthCertificate saved = healthCertificateWriter.persist(
        testPet, "health-certificates/abc.jpg", 정상_OCR결과()
    );

    SubmissionDetail<HealthCertificateResult> detail =
        submissionService.getSubmissionAndCertificateById(
            saved.getSubmission().getId(), testOwner.getId()
        );

    HealthCertificateResult result = detail.getResults().iterator().next();
    assertEquals("심장비대", result.diseaseName());
    assertEquals("2020.10.28", result.diagnosedDateText());
}

@Test
void getSubmissionAndCertificateById_날짜_파싱값이_null인_행도_정상_조회된다() {
    // 검토 집중 지점 2: 파싱 실패 행이 정상적으로 존재한다.
    HealthCertificateOcrResult 파싱실패 = new HealthCertificateOcrResult(
        null, null, null, null, null, null, null,
        "심장비대", null, "20Z0.1O.28", null, "20Z0.1O.28", null, null,
        0.95, true
    );
    PetHealthCertificate saved = healthCertificateWriter.persist(
        testPet, "health-certificates/abc.jpg", 파싱실패
    );

    SubmissionDetail<HealthCertificateResult> detail =
        submissionService.getSubmissionAndCertificateById(
            saved.getSubmission().getId(), testOwner.getId()
        );

    HealthCertificateResult result = detail.getResults().iterator().next();
    assertNull(result.diagnosedDate());
    assertEquals("20Z0.1O.28", result.diagnosedDateText(), "파싱 실패해도 원문은 보존된다");
}

@Test
void getSubmissionAndCertificateById_타인의_제출이면_404다() {
    PetHealthCertificate saved = healthCertificateWriter.persist(
        testPet, "health-certificates/abc.jpg", 정상_OCR결과()
    );

    assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSubmissionAndCertificateById(
            saved.getSubmission().getId(), 999999L
        )
    );
}

@Test
void getSubmissionAndCertificateById_진단서가_없는_제출이면_404_HEALTH_CERTIFICATE_NOT_FOUND다() {
    // 눈 진단 제출에 진단서 상세를 요청한 경우.
    Submission eye = submissionRepository.save(
        submissionService.createSubmission(testPet, SubmissionTypeEnum.EYE)
    );

    ResourceNotFoundException e = assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSubmissionAndCertificateById(eye.getId(), testOwner.getId())
    );

    assertEquals(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND, e.getErrorCode());
}

@Test
void getSubmissionAndCertificateById_삭제된_제출이면_404다() {
    PetHealthCertificate saved = healthCertificateWriter.persist(
        testPet, "health-certificates/abc.jpg", 정상_OCR결과()
    );
    UUID submissionId = saved.getSubmission().getId();
    submissionService.deleteSubmissionById(submissionId, testOwner.getId());

    assertThrows(
        ResourceNotFoundException.class,
        () -> submissionService.getSubmissionAndCertificateById(submissionId, testOwner.getId())
    );
}
```

이 테스트가 쓰는 헬퍼를 같은 클래스에 추가한다.

```java
@Autowired
private HealthCertificateWriter healthCertificateWriter;

private HealthCertificateOcrResult 정상_OCR결과() {
    return new HealthCertificateOcrResult(
        "초코", "Canine", "Pug", "Castrated Male", "흰색", "16년 3개월", null,
        "심장비대", LocalDate.of(2020, 10, 28), "2020.10.28",
        LocalDate.of(2020, 10, 28), "2020.10.28",
        "경과 관찰 필요", null,
        0.9124, true
    );
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest'
```

기대: 컴파일 실패 — `getSubmissionAndCertificateById`가 없다.

- [ ] **Step 3: 매퍼 메서드를 추가한다**

`dto/diagnosis/get/SubmissionMapper.java` — 기존 두 매퍼와 같은 구조로 추가한다. **차이는 `photoUrl`에 `null`을 넣는 것 하나다.**

```java
    /**
     * 진단서 상세 응답.
     *
     * photoUrl 을 의도적으로 null 로 둔다 —
     * imgUtils.getImgUrl 은 CloudFront 무서명 URL 을 반환할 수 있고,
     * 진단서 이미지에는 견주 성명 주소가 찍혀 있다.
     * 이미지는 GET /api/submissions/{id}/image 로만 받는다.
     */
    public SubmissionDetail<HealthCertificateResult> toSubmissionDetailForCertificate(
            Submission submission,
            PetHealthCertificate certificate
    ) {
        if (submission == null) {
            throw new IllegalArgumentException("서브미션이 null일 수 없습니다.");
        }

        if (certificate == null) {
            throw new IllegalArgumentException("진단서 기록이 null일 수 없습니다.");
        }

        SubmissionSummaryPetInfo petInfo = new SubmissionSummaryPetInfo(
            submission.getPet().getId(),
            submission.getPet().getName(),
            submission.getPet().getSpecies()
        );

        return new SubmissionDetail<HealthCertificateResult>(
            submission.getId(),
            petInfo,
            submission.getType(),
            null,   // ← 무서명 URL 이 새어나갈 경로를 원천 차단한다
            submission.getStatus().name(),
            submission.getSubmittedAt(),
            submission.getCompletedAt(),
            submission.getFailureReason(),
            Set.of(healthCertificateMapper.toResult(certificate))
        );
    }
```

의존성과 import를 추가한다.

```java
import java.util.Set;

import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.HealthCertificateMapper;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;

// 클래스 필드
private final HealthCertificateMapper healthCertificateMapper;
```

- [ ] **Step 4: 서비스 조회 메서드를 추가한다**

`services/SubmissionService.java` — 기존 `getSubmissionAndDiagnosesById`의 소유권·삭제 검증 패턴을 그대로 따른다. 단 `languageCode`는 필요 없다(번역할 조건 이름이 없다).

```java
    /**
     * 제출 ID로 진단서 상세를 가져온다.
     *
     * @param id 제출 ID
     * @param userId 요청자(소유자) 내부 ID
     * @return 진단서가 담긴 제출 상세 (photoUrl 은 항상 null)
     * @throws ResourceNotFoundException 제출이 없거나 타인 소유이거나 삭제된 경우
     *         {@code RESOURCE_NOT_FOUND}, 해당 제출에 진단서가 없는 경우
     *         {@code HEALTH_CERTIFICATE_NOT_FOUND}
     */
    public SubmissionDetail<HealthCertificateResult> getSubmissionAndCertificateById(
        UUID id,
        Long userId
    ) {
        if (id == null || userId == null) {
            throw new IllegalArgumentException("Submission ID 와 User ID 는 null 일 수 없습니다.");
        }

        // 소유권 검증이 내장된 조회를 재사용한다.
        Submission submission = getSubmissionByIdAndOwnerId(id, userId);

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        PetHealthCertificate certificate = petHealthCertificateRepository.findBySubmissionId(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));

        return submissionMapper.toSubmissionDetailForCertificate(submission, certificate);
    }
```

의존성과 import를 추가한다.

```java
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;

// 클래스 필드
private final PetHealthCertificateRepository petHealthCertificateRepository;
```

> **순환 참조 주의.** `SubmissionService`가 `PetHealthCertificateRepository`를 직접 받고, `HealthCertificateWriter`가 `SubmissionService`를 받는다. 리포지토리는 서비스를 참조하지 않으므로 순환이 생기지 않는다. `SubmissionService`에 `HealthCertificateWriter`나 `HealthCertificateService`를 주입하면 순환이 되므로 **하지 않는다.**

- [ ] **Step 5: 컨트롤러에 엔드포인트를 추가한다**

`controllers/SubmissionController.java`:

```java
    /** 진단서 상세 조회. photoUrl 은 항상 null — 이미지는 /{id}/image 로 받는다. */
    @GetMapping("/{id}/certificate")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionDetail<HealthCertificateResult>> getHealthCertificate(
            @PathVariable("id") UUID submissionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
            submissionService.getSubmissionAndCertificateById(submissionId, userId)
        );
    }
```

import를 추가한다.

```java
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'SubmissionServiceTest' --tests 'SubmissionServiceUT'
```

기대: PASS. 기존 눈·소변 상세 조회 테스트도 그대로 통과해야 한다(`SubmissionMapper` 생성자가 늘어났으므로 `@SpringBootTest`에서 빈 주입이 확인된다).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/dto/diagnosis/get/SubmissionMapper.java \
        src/main/java/com/smarthealthdog/backend/services/SubmissionService.java \
        src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java \
        src/test/java/com/smarthealthdog/backend/services/SubmissionServiceTest.java
git commit -m "Feat: 진단서 상세 조회 추가 및 응답에서 무서명 이미지 URL 제거"
```

---

## Task 18: `PATCH /api/submissions/{id}/certificate` — 수정 화면

**왜:** OCR 오인식 보정용이다. **전달된 필드만 반영**하며, 기존 `PartialUpdatePetRequest`와 같은 방식(nullable record 필드)을 따른다.

**규칙** (SPEC §4.5):
- 12개 항목 전부 수정 가능. `ocr_confidence` · `submission_id` · `pet_id`는 수정 불가
- 날짜는 `*_text`를 받아 **서버가 재파싱**해 `DATE` 컬럼을 갱신
- 한 번이라도 보정되면 `is_manually_edited = true`로 바뀌며 되돌릴 수 없다
- `disease_name` · `diagnosed_date_text`를 빈 값으로 지우는 것은 거부(`400 INVALID_INPUT`)

**검증 배선은 두 곳을 모두 건드려야 한다.** `@Valid`로 받으므로 `UpdateHealthCertificateRequestErrorCode` 컴포넌트와 `ValidErrorCodeFinder`의 `case`가 **둘 다** 있어야 한다. 하나라도 빠지면 필드 구분 없이 `INVALID_INPUT`으로 뭉개진다.

**Files:**
- Create: `src/main/java/com/smarthealthdog/backend/dto/health/UpdateHealthCertificateRequest.java`
- Create: `src/main/java/com/smarthealthdog/backend/validation/UpdateHealthCertificateRequestErrorCode.java`
- Modify: `src/main/java/com/smarthealthdog/backend/validation/ValidErrorCodeFinder.java:17-24, 41-56`
- Create: `src/main/java/com/smarthealthdog/backend/services/HealthCertificateUpdateService.java`
- Modify: `src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java`
- Test: `src/test/java/com/smarthealthdog/backend/services/HealthCertificateUpdateServiceUT.java`
- Test: `src/test/java/com/smarthealthdog/backend/validation/ValidErrorCodeFinderUT.java` (기존에 추가)

**Interfaces:**
- Consumes: `DateTextParser.parse` (Task 12), `PetHealthCertificateRepository` (Task 11), `SubmissionService.getSubmissionAndCertificateById` (Task 17)
- Produces:
    - `UpdateHealthCertificateRequest` — 12개 nullable 필드 (JSON snake_case)
    - `HealthCertificateUpdateService.update(UUID submissionId, Long userId, UpdateHealthCertificateRequest request) → SubmissionDetail<HealthCertificateResult>`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/smarthealthdog/backend/services/HealthCertificateUpdateServiceUT.java` 신규 생성. **검토 집중 지점 4**(공백 문자열)를 여기서 못박는다.

```java
package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HealthCertificateUpdateServiceUT {

    @InjectMocks
    private HealthCertificateUpdateService healthCertificateUpdateService;

    @Mock private SubmissionService submissionService;
    @Mock private PetHealthCertificateRepository petHealthCertificateRepository;

    private UUID submissionId;
    private PetHealthCertificate certificate;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();

        certificate = PetHealthCertificate.builder()
            .animalName("초코")
            .diseaseName("심장비대")
            .diagnosedDate(LocalDate.of(2020, 10, 28))
            .diagnosedDateText("2020.10.28")
            .ocrConfidence(new BigDecimal("0.9124"))
            .manuallyEdited(false)
            .build();

        when(petHealthCertificateRepository.findBySubmissionId(submissionId))
            .thenReturn(Optional.of(certificate));
        when(petHealthCertificateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** 12개 필드가 모두 null 인 요청 — 필요한 필드만 채워서 쓴다. */
    private UpdateHealthCertificateRequest 요청(String diseaseName) {
        return new UpdateHealthCertificateRequest(
            null, null, null, null, null, null, null,
            diseaseName, null, null, null, null
        );
    }

    @Test
    void 전달된_필드만_반영한다() {
        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest(
            "콩이", null, null, null, null, null, null,
            null, null, null, null, null
        );

        healthCertificateUpdateService.update(submissionId, 7L, request);

        assertEquals("콩이", certificate.getAnimalName());
        // 전달되지 않은 필드는 그대로다.
        assertEquals("심장비대", certificate.getDiseaseName());
    }

    @Test
    void 날짜_원문을_받으면_서버가_재파싱해_DATE_컬럼을_갱신한다() {
        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest(
            null, null, null, null, null, null, null,
            null, null, null, null, "2021.03.15"
        );

        healthCertificateUpdateService.update(submissionId, 7L, request);

        assertEquals("2021.03.15", certificate.getDiagnosedDateText());
        assertEquals(LocalDate.of(2021, 3, 15), certificate.getDiagnosedDate());
    }

    @Test
    void 재파싱에_실패하면_원문만_갱신하고_DATE는_null로_만든다() {
        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest(
            null, null, null, null, null, null, null,
            null, null, null, null, "알 수 없음"
        );

        healthCertificateUpdateService.update(submissionId, 7L, request);

        assertEquals("알 수 없음", certificate.getDiagnosedDateText());
        assertNull(certificate.getDiagnosedDate(), "파싱 실패 시 이전 DATE 가 남아 있으면 원문과 어긋난다");
    }

    @Test
    void 한_번이라도_보정되면_is_manually_edited가_true가_된다() {
        healthCertificateUpdateService.update(submissionId, 7L, 요청("심장비대, 폐침윤"));

        assertTrue(certificate.isManuallyEdited());
    }

    @Test
    void 아무_필드도_전달되지_않으면_INVALID_INPUT이다() {
        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateUpdateService.update(submissionId, 7L, 요청(null))
        );

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        verify(petHealthCertificateRepository, never()).save(any());
    }

    @Test
    void 병명을_공백으로_지우려_하면_INVALID_INPUT이다() {
        // 검토 집중 지점 4: DB 는 NOT NULL 만 보므로 공백은 통과한다.
        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateUpdateService.update(submissionId, 7L, 요청("   "))
        );

        assertEquals(ErrorCode.INVALID_INPUT, e.getErrorCode());
        assertEquals("심장비대", certificate.getDiseaseName(), "거부된 요청이 값을 바꾸면 안 된다");
        verify(petHealthCertificateRepository, never()).save(any());
    }

    @Test
    void 진단_연원일_원문을_공백으로_지우려_하면_INVALID_INPUT이다() {
        UpdateHealthCertificateRequest request = new UpdateHealthCertificateRequest(
            null, null, null, null, null, null, null,
            null, null, null, null, "  "
        );

        assertThrows(
            InvalidRequestDataException.class,
            () -> healthCertificateUpdateService.update(submissionId, 7L, request)
        );

        verify(petHealthCertificateRepository, never()).save(any());
    }

    @Test
    void 진단서가_없으면_404_HEALTH_CERTIFICATE_NOT_FOUND다() {
        UUID unknown = UUID.randomUUID();
        when(petHealthCertificateRepository.findBySubmissionId(unknown)).thenReturn(Optional.empty());

        ResourceNotFoundException e = assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateUpdateService.update(unknown, 7L, 요청("심장비대"))
        );

        assertEquals(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void 타인의_제출이면_404다() {
        // getSubmissionByIdAndOwnerId 가 소유권을 검증해 404 를 던진다.
        when(submissionService.getSubmissionByIdAndOwnerId(any(), anyLong()))
            .thenThrow(new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThrows(
            ResourceNotFoundException.class,
            () -> healthCertificateUpdateService.update(submissionId, 999L, 요청("심장비대"))
        );

        verify(petHealthCertificateRepository, never()).save(any());
    }

    @Test
    void ocr_confidence는_수정할_수_없다() {
        // 요청 DTO 에 해당 필드가 없으므로 구조적으로 불가능하다.
        BigDecimal before = certificate.getOcrConfidence();

        healthCertificateUpdateService.update(submissionId, 7L, 요청("심장비대, 폐침윤"));

        assertEquals(before, certificate.getOcrConfidence());
    }
}
```

`ValidErrorCodeFinderUT.java`에도 추가한다.

```java
@Test
void UpdateHealthCertificateRequest의_필드_오류를_INVALID_INPUT으로_매핑한다() {
    // case 가 빠지면 default 로 떨어져 필드 구분이 사라진다.
    // 이 테스트는 ValidErrorCodeFinder 에 case 를 추가했는지 확인한다.
    MethodArgumentNotValidException e =
        methodArgumentNotValidException("UpdateHealthCertificateRequest", "diseaseName");

    assertEquals(
        List.of(ErrorCode.INVALID_INPUT),
        validErrorCodeFinder.findErrorCode(e)
    );
}
```

> **확인:** `ValidErrorCodeFinderUT`의 기존 헬퍼 이름(위에서 `methodArgumentNotValidException(...)`으로 가정)을 `cat`으로 확인하고 맞춘다. 기존 테스트가 `BindingResult`를 직접 목으로 만든다면 같은 방식을 따른다.

- [ ] **Step 2: 실패를 확인한다**

```bash
./gradlew test --tests 'HealthCertificateUpdateServiceUT'
```

기대: 컴파일 실패 — `UpdateHealthCertificateRequest`, `HealthCertificateUpdateService`가 없다.

- [ ] **Step 3: 요청 DTO를 만든다**

`dto/health/UpdateHealthCertificateRequest.java`. `PartialUpdatePetRequest`와 같은 nullable record 방식이다.

```java
package com.smarthealthdog.backend.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * 진단서 부분 수정 요청. 전달된 필드만 반영한다.
 *
 * ocr_confidence submission_id pet_id 는 수정 대상이 아니므로
 * 필드 자체를 두지 않는다 — 구조적으로 수정이 불가능하다.
 *
 * 날짜는 원문(*_date_text)만 받고 서버가 재파싱해 DATE 컬럼을 갱신한다.
 * 클라이언트가 보낸 LocalDate 를 신뢰하면 원문과 어긋날 수 있다.
 *
 * PartialUpdatePetRequest 와 같은 방식이라 "전달 안 함" 과 "명시적 null" 을
 * 구분하지 않는다. 명세 범위에 필드를 비우는 요구가 없으므로 문제가 없다.
 */
public record UpdateHealthCertificateRequest(
    @JsonProperty("animal_name") @Size(max = 255) String animalName,
    @JsonProperty("species_text") @Size(max = 255) String speciesText,
    @JsonProperty("breed") @Size(max = 255) String breed,
    @JsonProperty("gender_text") @Size(max = 255) String genderText,
    @JsonProperty("coat_color") @Size(max = 255) String coatColor,
    @JsonProperty("age_text") @Size(max = 255) String ageText,
    @JsonProperty("features") String features,
    @JsonProperty("disease_name") String diseaseName,
    @JsonProperty("onset_date_text") @Size(max = 64) String onsetDateText,
    @JsonProperty("prognosis") String prognosis,
    @JsonProperty("remarks") String remarks,
    @JsonProperty("diagnosed_date_text") @Size(max = 64) String diagnosedDateText
) {}
```

> **필드 순서를 주의한다.** `diagnosedDateText`가 마지막이다 — Step 1의 테스트가 그 순서로 생성자를 호출한다. 순서를 바꾸면 테스트가 조용히 다른 필드를 채운다.

- [ ] **Step 4: 검증 컴포넌트와 `case`를 둘 다 추가한다**

`validation/UpdateHealthCertificateRequestErrorCode.java` — `UpdateUserProfileRequestErrorCode`와 같은 구조다.

```java
package com.smarthealthdog.backend.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
public class UpdateHealthCertificateRequestErrorCode {
    private final ErrorCode INVALID_INPUT = ErrorCode.INVALID_INPUT;

    public List<ErrorCode> getErrorCode(MethodArgumentNotValidException e) {
        if (e == null || e.getBindingResult() == null) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        List<String> fields = e.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField())
            .toList();

        if (fields.isEmpty()) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        return fields.stream()
            .map(fieldName -> getErrorCode(fieldName))
            .distinct()
            .toList();
    }

    /**
     * 12개 항목 모두 길이 제약만 있어 전용 코드가 필요하지 않다.
     * 필드별 코드가 생기면 여기에 case 를 추가한다.
     */
    private ErrorCode getErrorCode(String fieldName) {
        return switch (fieldName) {
            default -> INVALID_INPUT;
        };
    }
}
```

`validation/ValidErrorCodeFinder.java` — **필드와 `case`를 둘 다** 추가한다.

```java
    // 필드 선언부에 추가
    private final UpdateHealthCertificateRequestErrorCode updateHealthCertificateRequestErrorCode;

    // switch 에 추가
            case "UpdateHealthCertificateRequest":
                return updateHealthCertificateRequestErrorCode.getErrorCode(e);
```

- [ ] **Step 5: 수정 서비스를 만든다**

`services/HealthCertificateUpdateService.java`. 조회·검증·저장이 모두 DB 작업이고 외부 호출이 없으므로 **클래스 전체를 `@Transactional`로 둬도 안전하다** (Task 15의 비트랜잭션 제약은 OCR 호출 때문이었다).

```java
package com.smarthealthdog.backend.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionDetail;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.utils.DateTextParser;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 진단서 OCR 오인식 보정.
 *
 * 전달된 필드만 반영하고, 날짜는 원문을 받아 서버가 재파싱한다.
 * 한 번이라도 보정되면 is_manually_edited 가 true 가 되며 되돌릴 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HealthCertificateUpdateService {

    private final SubmissionService submissionService;
    private final PetHealthCertificateRepository petHealthCertificateRepository;

    /**
     * 진단서 항목을 보정한다.
     *
     * @param submissionId 제출 ID
     * @param userId 요청자(소유자) 내부 ID
     * @param request 보정할 필드만 담은 요청
     * @return 보정 후 상세 (상세 조회와 같은 모양)
     * @throws ResourceNotFoundException 제출이 없거나 타인 소유이거나 삭제된 경우
     *         {@code RESOURCE_NOT_FOUND}, 진단서가 없는 경우 {@code HEALTH_CERTIFICATE_NOT_FOUND}
     * @throws InvalidRequestDataException 변경할 필드가 없거나, 필수 항목을
     *         빈 값·공백으로 지우려는 경우 {@code INVALID_INPUT}
     */
    public SubmissionDetail<HealthCertificateResult> update(
        UUID submissionId,
        Long userId,
        UpdateHealthCertificateRequest request
    ) {
        if (submissionId == null || userId == null || request == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // 소유권 검증이 내장된 조회를 재사용한다.
        Submission submission = submissionService.getSubmissionByIdAndOwnerId(submissionId, userId);
        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        PetHealthCertificate certificate = petHealthCertificateRepository
            .findBySubmissionId(submissionId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));

        // 필수 항목을 공백으로 지우려는 시도를 거부한다.
        // DB 는 NOT NULL 만 보므로 "   " 는 그냥 통과해 빈 병명이 저장된다.
        rejectBlankRequiredField(request.diseaseName());
        rejectBlankRequiredField(request.diagnosedDateText());

        boolean changed = false;

        if (request.animalName() != null) {
            certificate.setAnimalName(request.animalName());
            changed = true;
        }
        if (request.speciesText() != null) {
            certificate.setSpeciesText(request.speciesText());
            changed = true;
        }
        if (request.breed() != null) {
            certificate.setBreed(request.breed());
            changed = true;
        }
        if (request.genderText() != null) {
            certificate.setGenderText(request.genderText());
            changed = true;
        }
        if (request.coatColor() != null) {
            certificate.setCoatColor(request.coatColor());
            changed = true;
        }
        if (request.ageText() != null) {
            certificate.setAgeText(request.ageText());
            changed = true;
        }
        if (request.features() != null) {
            certificate.setFeatures(request.features());
            changed = true;
        }
        if (request.diseaseName() != null) {
            certificate.setDiseaseName(request.diseaseName());
            changed = true;
        }
        if (request.prognosis() != null) {
            certificate.setPrognosis(request.prognosis());
            changed = true;
        }
        if (request.remarks() != null) {
            certificate.setRemarks(request.remarks());
            changed = true;
        }

        // 날짜는 원문을 저장하고 DATE 컬럼은 재파싱 결과로 덮는다.
        // 파싱에 실패하면 null 로 만든다 — 이전 DATE 를 남기면 원문과 어긋난다.
        if (request.onsetDateText() != null) {
            certificate.setOnsetDateText(request.onsetDateText());
            certificate.setOnsetDate(DateTextParser.parse(request.onsetDateText()).orElse(null));
            changed = true;
        }
        if (request.diagnosedDateText() != null) {
            certificate.setDiagnosedDateText(request.diagnosedDateText());
            certificate.setDiagnosedDate(
                DateTextParser.parse(request.diagnosedDateText()).orElse(null)
            );
            changed = true;
        }

        if (!changed) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // 한 번 true 가 되면 되돌리지 않는다.
        certificate.setManuallyEdited(true);

        PetHealthCertificate saved = petHealthCertificateRepository.save(certificate);

        return submissionService.getSubmissionAndCertificateById(submissionId, userId);
    }

    /**
     * 필수 항목을 공백 문자열로 지우려는 시도를 거부한다.
     * null 은 "전달하지 않음" 이므로 허용한다.
     */
    private void rejectBlankRequiredField(String value) {
        if (value != null && value.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }
    }
}
```

> **`saved` 변수가 쓰이지 않는다.** `save` 호출 자체가 목적(테스트가 `verify(save)`로 확인)이고 응답은 재조회로 만든다. 경고가 싫다면 `petHealthCertificateRepository.save(certificate);`로 반환값을 버린다.

- [ ] **Step 6: 컨트롤러에 엔드포인트를 추가한다**

`controllers/SubmissionController.java`. **기존 `PATCH /{id}/eye`는 `can_update_health_records`(AI 콜백용) 권한이지만, 이 엔드포인트는 사용자가 쓰므로 `can_view_own_health_records`를 쓴다** — 권한 마이그레이션 없이 USER 역할이 이미 가진 권한이다.

```java
    /** 진단서 OCR 오인식 보정. 전달된 필드만 반영한다. */
    @PatchMapping("/{id}/certificate")
    @PreAuthorize("hasAuthority('can_view_own_health_records')")
    public ResponseEntity<SubmissionDetail<HealthCertificateResult>> updateHealthCertificate(
            @PathVariable("id") UUID submissionId,
            @Valid @RequestBody UpdateHealthCertificateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(
            healthCertificateUpdateService.update(submissionId, userId, request)
        );
    }
```

의존성과 import를 추가한다.

```java
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.services.HealthCertificateUpdateService;

// 클래스 필드
private final HealthCertificateUpdateService healthCertificateUpdateService;
```

> **권한 선택을 팀과 확인한다.** `can_view_own_health_records`로 수정까지 허용하는 것은 "조회 권한으로 쓰기"라 어색하다. 더 정확한 선택은 `PermissionEnum`에 `can_edit_own_health_records`를 추가하고 USER 역할에 부여하는 마이그레이션(`V57`)을 쓰는 것이다. **명세는 §4.1에서 `can_view_own_health_records`를 지정했으므로 일단 그대로 따르고**, 리뷰에서 결정이 뒤집히면 권한 문자열 한 줄과 마이그레이션 하나만 바뀐다.

- [ ] **Step 7: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests 'HealthCertificateUpdateServiceUT' --tests 'ValidErrorCodeFinder*'
```

기대: PASS.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/smarthealthdog/backend/dto/health/UpdateHealthCertificateRequest.java \
        src/main/java/com/smarthealthdog/backend/validation/UpdateHealthCertificateRequestErrorCode.java \
        src/main/java/com/smarthealthdog/backend/validation/ValidErrorCodeFinder.java \
        src/main/java/com/smarthealthdog/backend/services/HealthCertificateUpdateService.java \
        src/main/java/com/smarthealthdog/backend/controllers/SubmissionController.java \
        src/test/java/com/smarthealthdog/backend/services/HealthCertificateUpdateServiceUT.java \
        src/test/java/com/smarthealthdog/backend/validation/ValidErrorCodeFinderUT.java
git commit -m "Feat: 진단서 부분 수정 엔드포인트 추가 및 검증 에러 코드 배선"
```

---

> **Phase 1 완료 지점 — `HEALTH-01`이 배포 가능하다.**
>
> ```bash
> ./gradlew test
> ./gradlew build -x test
> ```
>
> 수동 확인(dev 기동 후 Swagger):
> 1. `POST /api/pets/{id}/submissions/certificate` 에 실제 진단서 사진 → `201` + `submission_id`
> 2. 같은 요청을 즉시 반복 → `400 REQUEST_TOO_FREQUENT`
> 3. 진단서가 아닌 사진 → `400 NOT_A_HEALTH_CERTIFICATE`, **S3 버킷에 객체가 생기지 않았는지 확인**
> 4. `GET /api/submissions/{id}/certificate` → `photoUrl: null`
> 5. `GET /api/submissions/{id}/image` → 5분 만료 서명 URL, 브라우저에서 **다운로드되는지**(인라인 렌더링이 아닌지) 확인
> 6. `PATCH /api/submissions/{id}/certificate` 로 병명 수정 → `is_manually_edited: true`
> 7. `GET /api/submissions?type=HEALTH_CERTIFICATE` → 진단서만 나오는지
>
> 브랜치 예: `feat/HEALTH-01-진단서-ocr`

---

# Phase 2 — 문서와 정리

## Task 19: README·환경변수 정리와 미사용 코드 확인

**Files:**
- Modify: `README.md`
- ~~Modify: `src/test/resources/application-test.yml` (`naver.*` 기본값)~~ — 2026-10-06 `clients/naver/*` 삭제로 불필요

- [ ] **Step 1: README에 신규 환경변수를 추가한다**

`README.md`의 환경변수 안내에 아래를 **MacOS/Linux · Windows(PowerShell) 양쪽 형식으로** 추가한다(기존 문서 형식을 그대로 따른다).

| 변수 | 쓰임 | 기본값 |
|---|---|---|
| `UPLOAD_IMAGE_MAX_SIZE_BYTES` | 이미지 상한. `spring.servlet.multipart.max-file-size`와 같은 값 | `7340032` |
| `OCR_USER_INTERVAL_SECONDS` | 사용자 단위 유료 호출 최소 간격 | `30` |
| `GOOGLE_VISION_API_KEY` | Cloud Vision API 키. GCP 콘솔에서 **Cloud Vision API만 허용**하도록 제한한다 | 없음 (필수) |
| `HEALTH_CERTIFICATE_IMAGE_URL_EXPIRATION_SECONDS` | 이미지 서명 URL 만료 | `300` |

- [x] **Step 2: ~~`application-test.yml`의 `naver.*` 누락을 고친다~~ — 무효 (2026-10-06)**

병원 검색 기능(`HospitalSearchController`·`HospitalSearchService`·`clients/naver/*`·`dto/hospital/**`)
을 삭제했다. `naver.*` 키를 주입하는 코드가 더 이상 없으므로, 세 yml(`dev`/`test`/`docker-compose-dev`)
에서 `naver` 블록과 `NAVER_*` 환경변수를 제거했다. README에 추가할 `NAVER_*` 변수도 없다.

대신 이 Step이 지키려던 **원칙은 OCR 키에 그대로 적용된다**: 기본값 없는 `@Value` 주입은
`@SpringBootTest` 컨텍스트를 환경변수 없이 뜨지 못하게 만든다. 테스트 yml에는 반드시 기본값을
넣는다(SPEC §10.2).

```bash
# 기본값 없는 주입이 남아 있지 않은지 확인하는 검사
grep -rn '\${' src/main/java --include=*.java | grep -v ':.*:' | sort -u
```

- [ ] **Step 3: 미사용 코드를 확인한다 (지우지는 않는다)**

아래를 조사해 **결과만 PR 설명에 적는다.** 실제 제거는 별도 PR로 분리한다 — 이 PR의 테스트 범위를 넘기 때문이다.

```bash
grep -rn "getMostRecentSubmissionByPet" src/main --include=*.java
grep -rn "filterSubmissions(" src/main --include=*.java
grep -rn "findTopByPetOrderBySubmittedAtDesc" src/main --include=*.java
grep -rn "LocalImageUploader" src/main --include=*.java
```

예상: Task 7 이후 `getMostRecentSubmissionByPet`·`findTopByPetOrderBySubmittedAtDesc` 호출부가 0건, `filterSubmissions`도 0건(`filterUserSubmissions`와 중복), `LocalImageUploader`는 `@Profile("local")`로 어떤 프로파일에서도 활성화되지 않는다.

- [ ] **Step 4: 전체 테스트와 빌드를 확인한다**

```bash
./gradlew test
./gradlew build -x test
```

기대: 환경변수를 **하나도 설정하지 않은 상태**에서 전체 테스트가 통과한다. Step 2를 제대로 했는지 확인하는 기준이다.

- [ ] **Step 5: 커밋**

```bash
git add README.md
git commit -m "Docs: 신규 환경변수 안내 추가"
```

---

# 자기 점검 결과

## 1. 명세 커버리지

| 명세 항목 | 담당 작업 |
|---|---|
| §1.3 핵심 정책 (실패 시 미저장) | Task 15 (Step 1의 `아무것도_저장되지_않았다()` 7개 테스트) |
| §2.1~2.3 `SubmissionService`·`ImageUploader`·`S3Uploader` 재사용 | Task 3, 11 |
| §2.4 `SubmissionImageUploadEvent` 값 객체 재사용 | Task 15 Step 4 |
| §2.5 `getSecureImgUrl` | Task 4 |
| §2.6 `SubmissionMapper` 재사용 + `photoUrl` null | Task 17 |
| §3.1 `pet_health_certificates` + 설계 판단 ①②③ | Task 10, 11 |
| §3.2 `SubmissionTypeEnum` + Celery 분기 | Task 5 |
| §4.2 `POST .../certificate` | Task 16 |
| §4.3 `GET /{id}/certificate` | Task 17 |
| §4.4 `PATCH /{id}/certificate` | Task 18 |
| §4.5 `GET /{id}/image` | Task 8 |
| §4.6 목록 `type` 필터 | Task 9 |
| §5.1~5.3 처리 흐름·트랜잭션 경계·보상 삭제 | Task 15 |
| §5.4 소유권 404 | Task 8, 15, 17, 18 |
| §5.5 빈도 제한 (a)(b)(c) | Task 7 |
| §6 인식 실패 규칙 3개 | Task 15 Step 4 `validateOcrResult` |
| §7.1 `ErrorCode` 5개 | Task 6 |
| §7.2 부수 작업 3개 | Task 6 (1·2), Task 18 (3) |
| §8.1~8.3 OCR 벤더·인터페이스·타임아웃 | Task 13, 14 |
| §9.1 S3 키 레이아웃 | Task 15 (`health-certificates/`) |
| §9.2 읽기 경로 격리 | Task 4, 8, 17 |
| §9.3 Content-Type 고정 | Task 2 |
| §9.4 SSE·화이트리스트 | Task 2 (SSE), Task 13 (화이트리스트 추출) |
| §10.1 멀티파트 상한 | Task 1 |
| §10.2 OCR 설정 | Task 7, 14 |
| §10.3 README | Task 19 |
| §12.1 필수 거부 경로 테스트 7개 | Task 15 Step 1 |
| §12.1.1 보안 회귀 테스트 | Task 3 (`S3UploaderUT`), Task 15 (연속 실패) |
| §12.2 재사용 회귀 테스트 | Task 3 Step 7, Task 5, Task 7 Step 10, Task 9 Step 6, Task 4 Step 6 |

**빠진 항목:** §9.2가 지적한 **`/uploads/**`가 `permitAll`이고 `StaticResourceConfig`에 프로파일 게이트가 없어 prod에서도 활성**이라는 문제는 어느 작업도 고치지 않는다. 진단서 이미지는 S3에만 저장되어 `/uploads/**`로 접근할 수 없으므로 이 기능의 범위 밖이고, `SecurityConfig`를 건드리면 기존 인증 흐름 회귀 위험이 이 PR에 들어온다. **별도 보안 과제로 분리하고 PR 설명에 적는다.**

## 2. 플레이스홀더 점검

"TBD", "적절한 예외 처리", "위 내용에 대한 테스트 작성" 류의 문구가 없는지 확인했다. 모든 단계가 실행할 명령 또는 작성할 코드를 코드 블록으로 담고 있다.

## 3. 타입 일관성 점검

- `SubmissionStatus` (❌ `SubmissionStatusEnum` 아님) — 실제 enum 이름을 확인해 반영
- `RequestDiagnosisData(imageUrl, submission)` — 인자 순서를 확인해 Task 5 테스트에 반영
- `CeleryTaskSender(StringRedisTemplate, SubmissionRepository)` — 실제 생성자에 맞춤
- `ImgUtils.getSecureImgUrl(String, Duration)` — Task 4가 정의, Task 8·17이 같은 시그니처로 사용
- `ImageUploader.storeSubmissionImage(SubmissionImageUploadEvent, String)` — Task 3이 정의, Task 15가 같은 시그니처로 사용
- `HealthCertificateOcrResult` 필드 순서 — Task 13이 정의, Task 15 테스트와 Task 17 테스트가 같은 순서로 생성
- `UpdateHealthCertificateRequest` 필드 순서 — Task 18이 정의하고 **`diagnosedDateText`가 마지막**임을 명시

## 4. 검토 집중 지점 테스트 배치

| # | 지점 | 담당 테스트 |
|---|---|---|
| 1 | `photoUrl == ""` 행에 이미지 요청 | Task 8 Step 1 `getSecureImageUrl_이미지가_아직_없는_행이면_404를_낸다` |
| 2 | 날짜 파싱 NULL 행 조회 | Task 16 `날짜_파싱값이_null이어도_원문은_함께_내려간다`, Task 17 `날짜_파싱값이_null인_행도_정상_조회된다` |
| 3 | `submission_id` 중복 저장 | Task 15 Step 3 `HealthCertificateWriter.persist`의 `existsBySubmissionId` 검사 |
| 4 | 공백 문자열로 필수 항목 지우기 | Task 18 `병명을_공백으로_지우려_하면_INVALID_INPUT이다` |
| 5 | 반려동물을 바꿔 pet 단위 제한 우회 | Task 7 `유료_유형은_반려동물을_바꿔도_사용자_단위_간격에_걸린다` |
