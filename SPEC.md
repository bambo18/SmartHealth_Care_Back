# 기능 명세서 — 건강검진표 OCR

| 항목 | 내용 |
|---|---|
| 기능 ID | `HEALTH-01` (건강검진표 OCR 등록) |
| 작성일 | 2026-10-03 |
| 대상 | 백엔드 (`com.smarthealthdog.backend`) |
| 관련 브랜치 | `feat/HEALTH-01-건강검진표-OCR` |
| 설계 원칙 | **기존 Submission 업로드 파이프라인을 최대한 재사용**하되, 처리만 동기로 전환 |

---

## 1. 개요

### 1.1 배경 요구사항

- 사용자가 건강검진표 이미지를 업로드하면 AI를 통해 검진 항목 및 수치 정보를 OCR 기반으로 인식·변환
- 추출된 건강검진 데이터를 Backend로 전달하여 마이페이지의 반려동물 건강검진 정보로 등록
- 인식 결과의 정확성 및 잘못된 이미지에 대한 예외 처리/검증 방식 구현
- Frontend와 이미지 업로드 방식, API 요청/응답 DTO, 추출 데이터 표시 및 수정 화면에 대한 협의 필요

### 1.2 기능 범위

| ID | 기능 | 입력 | 처리 | 산출물 |
|---|---|---|---|---|
| `HEALTH-01` | 건강검진표(진단서) OCR 등록 | 진단서 사진 1장 | 외부 OCR API로 **글자 추출** | 12개 항목이 채워진 건강검진 기록 |

이 명세는 **진단서를 OCR로 읽어 기록으로 만드는 것 한 가지만** 다룬다. 구강 사진으로 치주 단계를 판별하는 **이미지 추론은 범위 밖이다** — 외부 AI 서비스와의 동기 HTTP 계약이 없어 착수할 수 없고(현재 계약은 Redis + Celery 비동기 큐뿐이다), 실패 원인과 재시도 범위가 OCR과 섞여서도 안 된다. 필요해지면 별도 명세·별도 기능 ID·별도 엔드포인트로 분리한다.

### 1.3 핵심 정책

> **OCR 인식에 실패하면 업로드 자체가 성립하지 않는다.**
> 요청 처리 중 OCR이 실패하거나 인식 품질이 기준에 못 미치면 `400`을 반환하고 **DB 행도 S3 객체도 만들지 않는다.**

### 1.4 확정된 설계 결정

| 항목 | 결정 |
|---|---|
| 처리 방식 | **동기 처리** (요청 안에서 OCR·판별까지 완료) |
| 추론 위치 | **외부 클라우드 API를 서버에서 직접 호출** |
| 원본 이미지 | **S3 저장 + 단기 서명 URL로만 접근** |
| 기록 구조 | **기존 `Submission` 집합체를 재사용**하고 상세 테이블을 연결 |
| Pet 반영 | **기록 행에만 스냅샷 저장**, `Pet` 엔티티 변경 없음 |

### 1.5 범위 밖

- **견주 성명·주소는 추출하지도 저장하지도 않는다.**
- 진단서 발급 병원 정보, 수의사 서명, 면허번호는 포함하지 않는다.
- 추출 결과 기반 진단·처방·알림 기능은 포함하지 않는다. 기록 보관과 조회까지가 범위다.

---

## 2. 기존 컴포넌트 재사용 설계

이 기능은 신규 저장·조회 스택을 만들지 않는다. 기존 진단 업로드 파이프라인의 **구성 요소를 그대로 쓰고, 실행 순서만 비동기에서 동기로 바꾼다.**

### 2.1 재사용 일람

| 기존 파일 | 재사용 방식 | 변경 |
|---|---|---|
| `services/SubmissionService.java` | 업로드 기록의 뼈대. `createSubmission` · `saveSubmission` · `getSubmissionByIdAndOwnerId` · `deleteSubmissionById` · `getSubmissionsByPetId` · `getSubmissionsByUserId`를 **그대로 호출** | 생성 메서드 1종 + 조회 메서드 1종 추가 |
| `utils/ImageUploader.java` | 업로드 추상화 유지. 동기 저장·삭제 메서드를 **인터페이스에 추가** | 메서드 2개 추가 |
| `utils/S3Uploader.java` | `putObject` 로직을 동기 메서드로 **추출**해 기존 비동기 리스너와 신규 동기 경로가 **같은 구현을 공유** | 리팩터링 + 삭제 기능 |
| `dto/.../SubmissionImageUploadEvent.java` | 동기 업로드 메서드의 **입력 DTO로 그대로 사용** (이벤트 발행 없이 값 객체로) | 변경 없음 |
| `utils/DevImgUtils.java` · `ProdImgUtils.java` | 서명 URL 생성 로직 재사용. `createPresignedUrl`을 TTL 파라미터화 | 메서드 1개 추가 |
| `domain/Submission.java` | 기록 엔티티로 재사용 | 변경 없음 |
| `dto/diagnosis/get/*` · `SubmissionMapper` | `SubmissionDetail<T>` · `SubmissionPage` · `SubmissionSummary` 재사용 | 매퍼 메서드 1개 추가 |
| `controllers/SubmissionController.java` · `PetController.java` | 기존 컨트롤러에 엔드포인트 추가 | **신규 컨트롤러 없음** |

### 2.2 `SubmissionService` — 기록의 뼈대로 재사용

`SubmissionTypeEnum`에 `HEALTH_CERTIFICATE` 상수를 추가한다. **`submissions.type` 컬럼에는 CHECK 제약이 없으므로**(`V42`가 `VARCHAR(255) NOT NULL DEFAULT 'EYE'`로만 추가) **이 enum 확장에는 마이그레이션이 필요 없다.**

그대로 호출하는 메서드:

| 메서드 | 쓰임 |
|---|---|
| `createSubmission(Pet, SubmissionTypeEnum)` | UUIDv7 발급, `photoUrl=""`, `submittedAt` 설정 |
| `saveSubmission(Submission)` | `@Transactional` 저장 |
| `getSubmissionByIdAndOwnerId(UUID, Long)` | **소유권 검증이 내장된 조회** (`findByIdWithPetAndUserAndOwnerId`). 별도 소유권 헬퍼 불필요 |
| `deleteSubmissionById(UUID, Long)` | 소프트 삭제(`DELETED`). **삭제 엔드포인트를 새로 만들 필요 없음** |
| `getSubmissionsByPetId(...)` / `getSubmissionsByUserId(...)` | 마이페이지 목록. 페이징·정렬 검증·날짜 범위 필터가 이미 구현됨 |

**추가할 메서드 2개:**

```java
// 동기 경로 전용: OCR 성공 후 이미 완료된 상태로 기록을 만든다
@Transactional
public Submission createCompletedSubmission(Pet pet, SubmissionTypeEnum type, String photoKey);

SubmissionDetail<HealthCertificateResult> getSubmissionAndCertificateById(UUID id, Long userId);
```

> **배치 작업과의 간섭 없음 (중요).**
> `PushAIInferenceTasks`는 `status = PENDING AND photoUrl <> ''`인 행만 집어 Celery 큐로 보내고, `RetryAIInferenceTasks`는 `PROCESSING`만 건드린다. 동기 경로는 기록을 **처음부터 `COMPLETED`로 생성**하므로 두 배치 중 어느 것도 이 행을 집지 않는다. Celery 큐로 잘못 흘러갈 경로가 구조적으로 없다.

> ⚠️ **`failSubmission`은 쓰지 않는다.** 이 메서드는 `status != PROCESSING`이면 예외를 던지는데 기존 업로드 실패 경로는 `PENDING` 상태에서 이를 호출하는 기존 버그가 있다. 동기 경로는 실패 시 아무것도 저장하지 않으므로 애초에 실패 상태 전환이 필요 없다.

### 2.3 `ImageUploader` + `S3Uploader` — 한 구현, 두 진입점

현재 `S3Uploader.uploadSubmissionImage`는 `@Async @TransactionalEventListener(AFTER_COMMIT)`라서 **DB 커밋과 HTTP 응답이 S3 업로드보다 먼저 끝난다.** 이 경로로는 "실패 시 미저장"이 성립할 수 없다.

그렇다고 S3 업로드 코드를 새로 작성하지는 않는다. **`putObject` 본문을 동기 메서드로 추출하고, 기존 비동기 리스너가 그 메서드를 호출하도록 바꾼다.**

```java
public interface ImageUploader {
    // ── 기존 (변경 없음) ──
    void uploadProfilePicture(UserProfilePictureUploadEvent event) throws IOException;
    void uploadPetImage(PetPictureUploadEvent event) throws IOException;
    void uploadSubmissionImage(SubmissionImageUploadEvent event) throws IOException;

    // ── 신규: 동기 저장/삭제 ──
    /** 업로드 후 S3 object key 를 반환한다. 예외는 호출자에게 그대로 전파된다. */
    String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) throws IOException;

    /** 보상 삭제용. 존재하지 않는 키는 무시한다. */
    void delete(String key);
}
```

`S3Uploader` 리팩터링 후 모습:

```java
@Override
public String storeSubmissionImage(SubmissionImageUploadEvent event, String prefix) {
    // 검증 → ext 추출(소문자) → key 생성 → putObject(SSE, Tika contentType)
    // 성공 시 key 반환
}

@Override
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void uploadSubmissionImage(SubmissionImageUploadEvent event) {
    try {
        String key = storeSubmissionImage(event, "diagnoses/");   // ← 같은 구현 재사용
        event.submission().setPhotoUrl(key);
        submissionService.saveSubmission(event.submission());
    } catch (Exception e) {
        submissionService.failSubmission(event.submission(), SERVICE_ERROR);
        throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
```

**기존 눈·소변 진단의 동작은 전혀 바뀌지 않는다.** 동기 경로는 `storeSubmissionImage`를 직접 호출할 뿐이다.

추출하면서 함께 개선하는 사항(기존 비동기 경로에도 자동 적용):
- 확장자 비교를 **소문자 변환 후** 수행 (현재 `.JPG`가 거부됨)
- `contentType`·`contentDisposition`·확장자를 모두 **Tika 판정값 기준으로** 설정 — 클라이언트 헤더를 쓰면 폴리글롯 저장형 XSS가 성립한다. **필수 항목이며 공격 시나리오와 적용 범위는 §9.3에 있다**
- `PutObjectRequest`에 **SSE 지정** (현재 암호화 설정 없음)

> `LocalImageUploader`(`@Profile("local")`)도 인터페이스 구현체이므로 신규 메서드 2개를 함께 구현해야 한다.

### 2.4 `SubmissionImageUploadEvent` — 값 객체로 재사용

```java
public record SubmissionImageUploadEvent(
    Submission submission, byte[] fileBytes, String originalFilename, String contentType
) {}
```

이 record에는 이벤트 고유 요소가 없다. 동기 경로에서는 `ApplicationEventPublisher`에 발행하지 않고 **`storeSubmissionImage`의 인자로 직접 전달**한다. 같은 목적의 파라미터 객체를 새로 만들지 않는다.

### 2.5 `DevImgUtils` / `ProdImgUtils` — 서명 URL 재사용

**이미 필요한 것이 구현되어 있다.** `getImgUrlForAIWorker(key)`는 dev·prod 양쪽 모두 **항상 presigned URL**을 반환한다(`ProdImgUtils` 주석: "AI Worker는 원본 S3 이미지 접근이 필요하므로 presigned URL을 반환한다"). 즉 서명 URL 생성 로직은 중복 작성할 필요가 없고, TTL만 조절하면 된다.

양쪽 클래스의 `private createPresignedUrl(key)`를 TTL 파라미터화하고 인터페이스에 노출한다:

```java
public interface ImgUtils {
    String getImgUrl(String key);                 // 기존
    String getImgUrlForAIWorker(String key);      // 기존 → getSecureImgUrl(key, 1시간) 위임
    String getSecureImgUrl(String key, Duration ttl);   // 신규
}
```

| 구현 | `getSecureImgUrl` 동작 |
|---|---|
| `DevImgUtils` | 기존 `createPresignedUrl`에 TTL 적용 |
| `ProdImgUtils` | 기존 `createPresignedUrl`에 TTL 적용 — **CloudFront 분기를 타지 않는다** |

> 🔒 **진단서 이미지에는 `getImgUrl(key)`을 절대 호출하지 않는다.** `ProdImgUtils.getImgUrl`은 CloudFront 도메인이 설정돼 있으면 **무서명·무만료 URL**을 반환한다. 견주 성명·주소가 찍힌 이미지에 이 메서드를 쓰면 그대로 공개된다.

> `src/test`의 `TestImageConfig.TestImgUtils`도 `ImgUtils` 구현체이므로 신규 메서드를 함께 구현해야 한다.

### 2.6 `SubmissionMapper` / `SubmissionDetail` — 응답 DTO 재사용

`SubmissionDetail<T>`는 이미 제네릭이다 — `id`, `petInfo`, `type`, `photoUrl`, `status`, `submittedAt`, `completedAt`, `failureReason`, `Set<T> results`. 결과 타입만 갈아끼우면 된다.

추가할 매퍼 메서드:

```java
SubmissionDetail<HealthCertificateResult> toSubmissionDetailForCertificate(Submission, PetHealthCertificate);
```

> ⚠️ 기존 매퍼 메서드들은 `photoUrl`을 `imgUtils.getImgUrl(...)`로 채운다. **신규 매퍼는 `photoUrl`을 `null`로 둔다.** 이미지는 전용 엔드포인트(§4.5)로만 받게 해서 무서명 URL이 새어나갈 경로를 원천 차단한다.

---

## 3. 데이터 모델 (`V55`)

저장소에 적용된 마지막 마이그레이션은 `V53__add_search_permissions_to_social_account_user.sql`이다.
빈도 제한 시도 테이블(§5.5c)이 `V54`를 쓰므로 **진단서 테이블은 `V55`**다.
파일명: `V56__create_pet_health_certificates.sql`

DDL 스타일은 `V51`을 따른다 — 대문자 키워드, `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, `fk_<table>_<target>` / `chk_<table>_<col>` 명명, `TIMESTAMP WITH TIME ZONE ... DEFAULT NOW()`, 명시적 `CREATE INDEX idx_<table>_<col>`.

### 3.1 신규 테이블 `pet_health_certificates`

```sql
CREATE TABLE pet_health_certificates (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pet_id BIGINT NOT NULL,
    submission_id UUID NOT NULL,

    -- 진단서 12개 항목
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

    CONSTRAINT uq_pet_health_certificates_submission UNIQUE (submission_id),

    CONSTRAINT chk_pet_health_certificates_ocr_confidence
        CHECK (ocr_confidence >= 0 AND ocr_confidence <= 1)
);

CREATE INDEX idx_pet_health_certificates_pet_id
    ON pet_health_certificates (pet_id);

CREATE INDEX idx_pet_health_certificates_diagnosed_date
    ON pet_health_certificates (diagnosed_date);
```

**이미지 키 컬럼이 없다.** 원본 이미지는 `submissions.photo_url`에 저장된다 — `Submission`을 재사용했기 때문에 중복 컬럼이 불필요하다. `V51`이 만든 `oral_health_records`도 같은 구조다 — 상세 테이블이 `submission_id`로 제출에 매달린다.

`UNIQUE (submission_id)`로 제출 1건당 진단서 1건을 보장한다.

#### 설계 판단 ①: 종류·성별·연령은 enum이 아니라 원문 텍스트로 저장한다

샘플 진단서는 종류를 `Canine`, 성별을 `Castrated Male`, 연령을 `16년 3개월`로 적는다. 기존 `PetSpecies(DOG/CAT)`·`PetGender(MALE/FEMALE/UNKNOWN)`로는 **중성화 여부와 개월 단위 나이가 손실된다.** 진단서는 발급 시점 문서이므로 원문 보존이 맞다.

#### 설계 판단 ②: 날짜는 파싱값과 원문을 함께 저장한다

한국 진단서는 `2020.10.28` 표기가 흔하고 OCR이 구분자나 숫자를 틀릴 수 있다. 정렬·기간 필터는 `DATE` 컬럼으로 하되 화면에는 원문을 보여준다. **파싱 실패가 등록을 막지는 않는다** — 원문이 있으면 등록하고 `DATE`는 `NULL`로 둔 뒤 수정 화면에서 보정한다.

#### 설계 판단 ③: `raw_ocr_json` 컬럼은 두지 않는다

OCR 원응답에는 견주 성명·주소가 포함된다. 디버깅 편의로 원응답을 통째로 저장하면 그 순간 개인정보가 DB에 들어간다. **화이트리스트한 12개 항목만 꺼내 쓰고 원응답은 메모리에서만 다루며 로그에도 남기지 않는다.**

### 3.2 `SubmissionTypeEnum` 확장

```java
public enum SubmissionTypeEnum { EYE, URINE, HEALTH_CERTIFICATE }
```

> ⚠️ **`CeleryTaskSender.sendDiagnosisTaskInBatch`의 분기를 반드시 손봐야 한다.**
> 현재 `if (type == EYE) {...} else {...소변...}` 구조라 **새 상수가 조용히 소변 분기로 빠진다.** 동기 경로는 Celery를 타지 않지만, 방어적으로 `switch`로 바꾸고 `HEALTH_CERTIFICATE`는 `IllegalStateException`을 던지게 한다.

---

## 4. API 명세

**신규 컨트롤러를 만들지 않는다.** 업로드는 `PetController`, 조회·수정·삭제는 `SubmissionController`에 추가한다. 기존 눈·소변 진단과 경로 모양이 같아 프론트가 동일한 규칙으로 다룰 수 있다.

### 4.1 엔드포인트 일람

| # | 메서드 · 경로 | 권한 | 신규/기존 |
|---|---|---|---|
| 1 | `POST /api/pets/{id}/submissions/certificate` | `can_use_health_check` | 신규 (`PetController`) |
| 2 | `GET /api/submissions/{id}/certificate` | `can_view_own_health_records` | 신규 (`SubmissionController`) |
| 3 | `PATCH /api/submissions/{id}/certificate` | `can_use_health_check` | 신규 — **수정 화면** |
| 4 | `GET /api/submissions/{id}/image` | `can_view_own_health_records` | 신규 — 서명 URL |
| 5 | `GET /api/submissions/pets/{petId}?type=HEALTH_CERTIFICATE` | `can_view_own_health_records` | **기존 재사용** + `type` 파라미터 |
| 6 | `GET /api/submissions?type=HEALTH_CERTIFICATE` | `can_view_own_health_records` | **기존 재사용** + `type` 파라미터 |
| 7 | `DELETE /api/submissions/{id}` | `can_use_health_check` | **기존 그대로 사용** |

- **권한 마이그레이션 불필요.** `can_use_health_check`, `can_view_own_health_records`는 `PermissionEnum`에 이미 있고 `V11`에서 USER 역할에 부여되어 있다.
- **목록·삭제 엔드포인트를 새로 만들지 않는다.** 기존 `/api/submissions` 계열이 페이징·정렬 검증·날짜 범위 필터·소프트 삭제를 모두 갖추고 있다.
- `PATCH /api/submissions/{id}/eye|urine`는 AI 서비스 콜백용(`can_update_health_records`)이지만, 동기 처리인 `certificate`에는 AI 콜백이 없으므로 **경로 충돌이나 의미 혼동이 없다.**

### 4.2 `POST /api/pets/{id}/submissions/certificate`

**요청** — `multipart/form-data`, 파트 `image` (기존 눈·소변 엔드포인트와 동일한 파트 이름)

**성공 `201 Created`**

```json
{
  "submission_id": "018f3c2a-7b1e-7000-9c3d-1a2b3c4d5e6f",
  "pet_id": 3,
  "animal_name": "초코",
  "species_text": "Canine",
  "breed": "Pug",
  "gender_text": "Castrated Male",
  "coat_color": "흰색",
  "age_text": "16년 3개월",
  "features": null,
  "disease_name": "심장비대, 폐침윤",
  "onset_date": "2020-10-28",
  "onset_date_text": "2020.10.28",
  "diagnosed_date": "2020-10-28",
  "diagnosed_date_text": "2020.10.28",
  "prognosis": "엑스레이 검사상 폐침윤이 확인되며 폐수종, 폐렴, 폐출혈 의심됩니다.",
  "remarks": "10.28일 진료에 대한 진단서 발급 원합니다.",
  "ocr_confidence": 0.9124,
  "is_manually_edited": false,
  "submitted_at": "2026-10-03T12:00:00Z"
}
```

> **생성된 `submission_id`를 응답에 담는다.** 기존 `POST /api/pets/{id}/submissions/eye`는 `201`에 빈 본문을 돌려주어 클라이언트가 방금 만든 리소스를 알 수 없다. 같은 실수를 반복하지 않는다. 프론트는 이 응답을 그대로 수정 화면에 띄우면 된다.

**실패**

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_IMAGE` | 형식·용량 위반, 빈 파일 |
| 400 | `NOT_A_HEALTH_CERTIFICATE` | 진단서 양식이 아님 |
| 400 | `OCR_RECOGNITION_FAILED` | 신뢰도 미달 |
| 400 | `OCR_REQUIRED_FIELD_MISSING` | 병명 또는 진단 연원일 미검출 |
| 404 | `RESOURCE_NOT_FOUND` | 존재하지 않거나 타인의 반려동물 |
| 503 | `OCR_SERVICE_UNAVAILABLE` | OCR 서비스 타임아웃·장애 |

### 4.3 `GET /api/submissions/{id}/certificate`

기존 `GET /api/submissions/{id}/eye`와 동일한 `SubmissionDetail<T>` 포장으로 반환한다.

```json
{
  "id": "018f3c2a-7b1e-7000-9c3d-1a2b3c4d5e6f",
  "petInfo": { "id": 3, "name": "초코", "species": "DOG" },
  "type": "HEALTH_CERTIFICATE",
  "photoUrl": null,
  "status": "COMPLETED",
  "submittedAt": "2026-10-03T12:00:00Z",
  "completedAt": "2026-10-03T12:00:04Z",
  "failureReason": null,
  "results": [ { "...12개 항목...": "..." } ]
}
```

`photoUrl`은 항상 `null`이다 — 이미지는 #4로만 받는다(§2.6).

### 4.4 `PATCH /api/submissions/{id}/certificate` — 수정 화면

OCR 오인식 보정용. **전달된 필드만 반영**한다(기존 `PartialUpdatePetRequest`와 같은 방식).

```json
{
  "animal_name": "초코",
  "disease_name": "심장비대",
  "diagnosed_date_text": "2020.10.28",
  "prognosis": "경과 관찰 필요"
}
```

- 12개 항목 전부 수정 가능. `ocr_confidence` · `submission_id` · `pet_id`는 수정 불가
- 날짜는 `*_text`를 받아 서버가 재파싱해 `DATE` 컬럼을 갱신
- 한 번이라도 보정되면 `is_manually_edited = true`로 바뀌며 되돌릴 수 없다
- `disease_name` · `diagnosed_date_text`를 빈 값으로 지우는 것은 거부(`400 INVALID_INPUT`)

**응답 `200`** — 상세와 동일한 전체 객체

### 4.5 `GET /api/submissions/{id}/image`

`getSubmissionByIdAndOwnerId`로 소유권을 검증한 뒤 `imgUtils.getSecureImgUrl(photoUrl, 5분)`을 반환한다.

```json
{ "image_url": "https://.../health-certificates/...?X-Amz-Expires=300&...", "expires_in": 300 }
```

리다이렉트(`302`)가 아니라 JSON으로 돌려준다. 프론트가 만료를 알고 재요청 시점을 판단해야 하기 때문이다. 이 엔드포인트는 모든 제출 유형에 공통으로 동작한다.

### 4.6 목록 조회 — 기존 엔드포인트에 `type` 필터 추가

`getSubmissionsByPetId` · `getSubmissionsByUserId`는 각각 `SubmissionSpecifications.filterPetSubmissions` · `filterUserSubmissions`를 사용한다. 두 Specification에 **`type` 동등 조건(선택)** 을 추가하고 컨트롤러에 `@RequestParam(required = false) SubmissionTypeEnum type`을 더하면, 페이징·정렬 검증·날짜 범위 필터를 그대로 쓰면서 유형별 목록이 된다.

> 참고: `filterPetSubmissions`는 `filterUserSubmissions`와 달리 **`DELETED`를 제외하지 않는 비대칭**이 있다. 유형 필터를 넣는 김에 함께 맞추는 것을 권장한다.

---

## 5. 처리 흐름 — "실패 시 미저장" 보증

### 5.1 순서

```
1. @PreAuthorize 권한 검사 통과
   → Long ownerId = Long.parseLong(userDetails.getUsername())

2. petService.get(petId) → pet.getOwner().getId() != ownerId 이면 404 (403 아님)

3. 빈도 제한 검사 (유형별, 시도 기준)  ← §5.5
   → 위반이면 400 REQUEST_TOO_FREQUENT, 외부 API를 호출하지 않고 종료
   → 통과 시 "시도" 를 먼저 기록한다 (인식 성공 여부와 무관)

4. FileUploadService.validateImageFile(file)  ← 기존 메서드 재사용
   (빈 파일 / 확장자 / Tika MIME / 용량)

5. 외부 OCR API 호출 (타임아웃 적용)
   → 호출 실패·타임아웃이면 여기서 종료 (503)

6. 인식 규칙 평가 (문서 종류 / 필수 항목 / 신뢰도)
   → 미달이면 여기서 종료 (400)

   ══════ 여기까지 DB·S3에 어떤 쓰기도 일어나지 않는다 ══════
   (3단계의 시도 기록은 Submission 행이 아니다 — §5.5)

7. imageUploader.storeSubmissionImage(event, "health-certificates/")   → key 반환
   (SubmissionImageUploadEvent 를 값 객체로 전달, 이벤트 발행 아님)

8. @Transactional 안에서
     submissionService.createCompletedSubmission(pet, HEALTH_CERTIFICATE, key)
     → pet_health_certificates INSERT

9. 201 + submission_id 포함 응답
```

**5·6단계가 7·8단계보다 앞에 있다는 사실이 "OCR 인식 실패 시 업로드 불가"의 전부다.** 인식 판정이 끝나기 전에는 저장 행위가 존재하지 않으므로 롤백할 것도 없다.

**3단계가 5단계보다 앞에 있는 이유는 비용이다.** 이 엔드포인트는 요청 1건당 유료 외부 OCR 호출 1건을 발생시킨다. 빈도 제한이 검증·호출보다 뒤에 있으면 제한 자체가 비용을 막지 못한다.

### 5.2 트랜잭션 경계

**외부 HTTP 호출을 `@Transactional` 안에 두지 않는다.** 서비스 진입 메서드는 비트랜잭션으로 두고, 검증·OCR·S3 업로드를 마친 뒤 **저장만** 별도 `@Transactional` 메서드에 위임한다. OCR 응답을 기다리는 동안(최대 15초) DB 커넥션을 점유하면 커넥션 풀이 빠르게 고갈된다.

### 5.3 S3 성공 · DB 실패 구간

6단계는 성공했는데 7단계가 실패하면 주인 없는 S3 객체가 남는다. 이때 **`imageUploader.delete(key)`로 보상 삭제**한다 — §2.3에서 `ImageUploader`에 추가한 메서드다.

> ⚠️ **현재 저장소에는 S3 삭제 코드가 전혀 없다.** `deleteObject` 검색 결과 0건이며, 프로필 사진을 교체해도 옛 객체가 그대로 남는다. 이 기능에서 처음으로 삭제 기능이 들어간다.

보상 삭제마저 실패하면 `log.error`로 객체 키를 남기고 요청은 500으로 끝낸다. 고아 객체 정리 배치는 후속 과제로 둔다.

### 5.4 소유권 검증 규칙

타인의 리소스 접근은 **항상 404, 절대 403이 아니다.**

```java
// WalkService.java:58-61 의 주석
// 다른 사용자의 반려동물 존재 여부를 노출하지 않기 위해
// 기존 프로젝트 방식과 동일하게 404 처리
```

기록 단건 조회·수정·삭제는 **`SubmissionService.getSubmissionByIdAndOwnerId`가 이미 소유권 검증을 포함**하므로 별도 헬퍼가 필요 없다.

### 5.5 요청 빈도 제한 — 세 가지 문제

#### (a) 기존 기능에 미치는 부작용 — 유형별 분리

`AIDiagnosisClientService`의 빈도 제한은 `getMostRecentSubmissionByPet(pet)`을 쓰는데, 이 쿼리는 **유형을 가리지 않는다**(`findTopByPetOrderBySubmittedAtDesc`).

`Submission`을 재사용하면 **진단서를 올린 직후 눈 진단이 "요청이 너무 잦다"며 거부되는** 교차 간섭이 생긴다. 반드시 함께 처리한다:

```java
// SubmissionRepository 에 추가
Optional<Submission> findTopByPetAndTypeOrderBySubmittedAtDesc(Pet pet, SubmissionTypeEnum type);
```

#### (b) ⚠️ 신규 동기 엔드포인트에는 빈도 제한을 **반드시 적용한다** — 선택 사항이 아니다

동기 엔드포인트 1회 요청의 비용은 기존 비동기 진단과 비교가 되지 않는다:

| 항목 | 기존 눈·소변 (비동기) | 신규 진단서 (동기) |
|---|---|---|
| 유료 외부 API 호출 | 큐 적재만, 워커가 처리 | **요청 1건 = 유료 OCR 호출 1건** |
| 요청 스레드 점유 | 즉시 반환 | **최대 15초** (`read-timeout-seconds: 15`) |
| 힙 점유 | 업로드 후 해제 | `file.getBytes()`로 **파일 전체를 OCR 응답 대기 내내 유지** |

제한이 없으면 인증된 사용자 1명이 반복 호출만으로 **외부 API 비용 증폭과 요청 스레드·힙 고갈을 동시에** 유발한다. 제한 수단이 아니라 적용 여부를 논의 대상으로 두지 않는다.

#### (c) 🔴 `Submission` 행을 기준으로 세면 제한이 작동하지 않는다

이것이 이 기능 고유의 함정이다. 기존 빈도 제한은 **가장 최근 `Submission` 행의 `submittedAt`** 을 기준으로 판정한다. 그런데 §1.3 핵심 정책에 따라 **인식에 실패한 요청은 `Submission` 행을 만들지 않는다.**

```
공격자 요청 → OCR 호출(유료) → 인식 실패 → 400 → Submission 행 없음
           → 다음 요청의 빈도 검사가 볼 기록이 없음 → 무한 반복 가능
```

**즉 비용을 발생시키는 요청만 정확히 제한을 빠져나간다.** 성공한 요청만 제한되므로 제한이 사실상 없는 것과 같다.

대응 — **성공·실패와 무관하게 남는 "시도" 를 세야 한다.** §5.1의 3단계가 OCR 호출(5단계)보다 앞에 있고, 인식 결과를 보기 전에 기록되는 이유가 이것이다.

| 선택지 | 판단 |
|---|---|
| Redis 카운터 (`user:{id}:ocr-attempt`, TTL = 제한 간격) | 권장. 다만 **`RedisConfig`가 `@Profile("dev")` 전용**이므로 prod 프로파일에 Redis 빈을 먼저 노출해야 한다 — 선결 조건 |
| `Submission` 행을 `FAILED`로 먼저 만들고 실패 시 `DELETED`로 소프트 삭제 | §1.3 "DB 행도 만들지 않는다" 와 정면 충돌한다. **채택하지 않는다** |
| 별도 시도 기록 테이블 | Redis 선결 조건을 미루려면 차선. `V54`로 테이블을 하나 만든다 |

> 어느 쪽을 택하든 **빈도 제한 대상은 `Submission` 행이 아니다.** 구현 착수 전 결정이 필요하다 (§14).

---

## 6. 인식 실패 규칙

| # | 규칙 | 기본값 | 실패 시 |
|---|---|---|---|
| 1 | **문서 종류 확인** — `진단서`, `병명`, `동물의 표시` 등 고유 키워드가 하나도 검출되지 않으면 거부 | 키워드 1개 이상 | `NOT_A_HEALTH_CERTIFICATE` |
| 2 | **필수 항목** — 병명, 진단 연원일(원문)이 비면 거부 | 2개 모두 필수 | `OCR_REQUIRED_FIELD_MISSING` |
| 3 | **신뢰도 임계값** — 전체 평균 신뢰도 | `0.75` | `OCR_RECOGNITION_FAILED` |

나머지 10개 항목(동물명·종류·품종·성별·모색·연령·특징·발병 연원일·예후 소견·비고)은 **비어도 등록을 허용한다.** 진단서마다 빈칸이 흔하고, 수정 화면에서 사용자가 채울 수 있다.

---

## 7. 에러 코드

응답 본문은 기존과 동일하게 `ErrorMessage(List<String> codes, List<String> messages)` 형태다.

### 7.1 추가할 `ErrorCode` 상수

| 상수 | 메시지 | HTTP |
|---|---|---|
| `OCR_RECOGNITION_FAILED` | `진단서를 인식하지 못했습니다. 더 밝고 선명한 사진으로 다시 시도해주세요.` | 400 |
| `OCR_REQUIRED_FIELD_MISSING` | `진단서에서 병명 또는 진단 연원일을 찾지 못했습니다.` | 400 |
| `NOT_A_HEALTH_CERTIFICATE` | `진단서 양식으로 보이지 않는 이미지입니다.` | 400 |
| `OCR_SERVICE_UNAVAILABLE` | `인식 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요.` | 503 |
| `HEALTH_CERTIFICATE_NOT_FOUND` | `건강검진 기록을 찾을 수 없습니다.` | 404 |

이미지 형식·용량 위반은 기존 `INVALID_IMAGE`를 재사용한다.

### 7.2 함께 처리할 부수 작업

1. **`ServiceUnavailableException` 신설** — `CustomException`을 상속하고 `GlobalExceptionHandler`에 503 핸들러를 추가한다. 현재 503을 내는 경로가 없다.
2. **`InternalServerErrorException` 핸들러 추가** — 선언만 되어 있고 전용 핸들러가 없어 지금은 맨 아래 `Exception` catch-all로 떨어진다.
3. **`PATCH` 요청 DTO 검증 배선** — `UpdateHealthCertificateRequest`를 `@Valid`로 받으므로 `UpdateHealthCertificateRequestErrorCode` 컴포넌트와 `ValidErrorCodeFinder`의 `case`를 **둘 다** 추가해야 한다. 하나라도 빠지면 필드 구분 없이 `INVALID_INPUT`으로 뭉개진다.

---

## 8. 외부 연동 클라이언트

### 8.1 OCR 벤더 — Google Cloud Vision (⚠ 미확정)

> **벤더는 아직 확정되지 않았다.** 현재 방향은 Google Cloud Vision이며 이 문서는 그 전제로 쓰였다. 확정 전까지 §8.2의 인터페이스 분리가 유일한 안전장치다 — 벤더가 바뀌면 `clients/ocr/` 아래 구현체 하나와 `ocr.google.*` 설정 키만 교체되고, 그 바깥은 손대지 않는다. **구현 중 벤더가 바뀌면 이 절과 §10.2·§10.3·§11.1만 갱신하면 된다.**

**Google Cloud Vision `DOCUMENT_TEXT_DETECTION`을 쓴다.** 밀집된 문서 텍스트에 특화된 모드이고, `languageHints: ["ko"]`로 한국어를 명시할 수 있다. 진단서는 병원마다 서식이 달라 고정 템플릿을 전제할 수 없으므로, 템플릿 정의 없이 전체 텍스트를 블록 단위로 받아 `HealthCertificateFieldExtractor`가 항목을 뽑는 구조가 맞다.

인증은 API 키 하나(`ocr.google.api-key`)로 끝나고 호출은 `images:annotate` 단일 엔드포인트다. Gradle 의존성 추가는 불필요하다 — `java.net.http.HttpClient`로 직접 호출한다(§8.3).

**요청 크기 주의:** Vision은 이미지를 **base64로 JSON 본문에 담아** 보내므로 크기가 약 1.33배로 불어난다. 업로드 상한 7MB(§10.1)는 이 팽창을 감안한 값이다 — base64 약 9.3MB로 Vision의 요청 크기 한도 안에 들어간다.

> **미해결 — 개인정보:** Google Cloud Vision은 해외 리전으로 이미지를 전송한다. 진단서에는 보호자·동물 식별 정보가 담기므로 국외 이전 고지·동의가 필요한지 법무 확인이 필요하다(§13.2). 이 항목이 막히면 국내 벤더(NCP CLOVA OCR 등)로 전환하는데, 그때 바뀌는 범위가 바로 위 경고에 적힌 그만큼이다.

### 8.2 인터페이스 분리

```java
public interface HealthCertificateOcrClient {
    HealthCertificateOcrResult extract(byte[] imageBytes, String contentType);
}
```

구현(`GoogleVisionOcrClient`)은 인터페이스 뒤에 숨긴다. **OCR 벤더가 미확정(§8.1)이므로 이 분리는 선택이 아니라 필수다** — 비즈니스 코드는 `HealthCertificateOcrClient`만 알고, 벤더 교체는 구현체 하나를 갈아끼우는 일로 끝나야 한다. 외부 의존성을 인터페이스로 가리는 것은 이 저장소의 확립된 관례이며(`ImgUtils`, `ImageUploader`, `EmailService`, `DiagnosisTaskRequestClient`), 테스트에서 가짜 구현으로 갈아끼우기 위해서도 필요하다.

### 8.3 타임아웃·에러 매핑

**`utils/OAuthClient.java`를 템플릿으로 삼는다.** 저장소에서 유일하게 제대로 된 외부 클라이언트다 — `connectTimeout` 10초, 요청별 `timeout` 15초, 명시적 상태 코드 검사, `InterruptedException`에서 인터럽트 플래그 복원, 비밀값은 길이만 로깅.

반면 **`WebClientConfig`는 맨 `builder.build()`라 타임아웃이 없다.** 이쪽을 따라가면 OCR 장애 시 요청 스레드가 무한정 묶인다.

| 외부 상황 | 매핑 |
|---|---|
| 연결 실패 / 타임아웃 / 5xx | `ServiceUnavailableException(OCR_SERVICE_UNAVAILABLE)` → 503 |
| 4xx (잘못된 요청·키) | `InternalServerErrorException` → 500 + `log.error` (사용자 잘못이 아님) |
| 200이지만 본문 파싱 실패 | `InvalidRequestDataException(OCR_RECOGNITION_FAILED)` → 400 |

재시도는 **하지 않는다.** 동기 요청이라 재시도가 사용자 대기 시간을 배로 늘린다.

---

## 9. 보안 · 개인정보

### 9.1 S3 키 레이아웃

`storeSubmissionImage(event, prefix)`의 prefix로 구분한다.

```
diagnoses/{uuid}.{ext}              ← 기존 눈·소변 (변경 없음)
health-certificates/{uuid}.{ext}    ← 신규
```

접두사 단위로 접근 정책과 수명 주기 규칙을 따로 걸 수 있어야 하므로 기존 `diagnoses/`와 섞지 않는다.

### 9.2 읽기 경로 격리 — 가장 중요한 항목

> **진단서 이미지는 `/uploads/**`와 무서명 CloudFront URL을 절대 타지 않는다.**

현재 구조의 위험:

| 사실 | 영향 |
|---|---|
| `SecurityConfig`에서 `/uploads/**`가 `permitAll`이고 소유권 검사가 **전혀 없다** | URL만 알면 누구나 영구 접근 |
| `StaticResourceConfig`에 프로파일 게이트가 없어 prod에서도 활성 | 운영 환경에서도 동일하게 노출 |
| `ProdImgUtils.getImgUrl`은 CloudFront **무서명** URL을 반환 | 만료 없는 공개 링크 |

대응: **`getSecureImgUrl(key, ttl)`만 사용**(§2.5)하고, `SubmissionMapper`의 신규 매퍼는 `photoUrl`을 `null`로 둔다(§2.6). 이미지는 오직 `GET /api/submissions/{id}/image`(§4.5)로만, 소유권 검증 후 5분 만료 서명 URL로 제공한다.

### 9.3 저장 `Content-Type` 고정 — 폴리글롯 저장형 XSS 차단

> **서명 URL은 "누가 접근하는지"를 제한할 뿐, "브라우저가 그 바이트를 무엇으로 해석하는지"는 제한하지 않는다.** 이 항목은 §9.2와 별개의 취약점이며 서명 URL로 완화되지 않는다.

**현재 코드 사실:**

| 사실 | 위치 |
|---|---|
| `putObject`의 `contentType`에 **멀티파트 파트 헤더 값을 그대로** 넣는다 (`event.contentType()` ← `MultipartFile.getContentType()`) | `S3Uploader.java:81`(profiles) · `:113`(pets) · `:151`(diagnoses) |
| `FileUtils.isMIMEImage`는 **`boolean`만 반환**하고 Tika가 판정한 MIME 문자열을 버린다 | `FileUtils.java:25` |
| `PutObjectRequest`에 `contentDisposition` 지정이 없다 | 위 세 곳 동일 |

**공격 시나리오 — Tika 검증을 통과하면서 HTML로 렌더링된다:**

| 단계 | 내용 |
|---|---|
| 1 | 유효한 JPEG의 EXIF/comment 세그먼트에 `<script>` 를 삽입한 **폴리글롯 파일**을 만든다 |
| 2 | 멀티파트 파트 헤더만 `Content-Type: text/html`로 바꿔 업로드한다 |
| 3 | `validateImageFile` 통과 — 확장자는 `.jpg`, Tika는 **바이트를 보고** `image/jpeg`로 판정하므로 둘 다 참 |
| 4 | S3 객체의 메타데이터는 `text/html`로 저장된다 (Tika 판정값은 쓰이지 않음) |
| 5 | `GET /api/submissions/{id}/image`로 받은 서명 URL을 열면 브라우저가 **HTML로 렌더링**한다 → S3/CloudFront 도메인에서의 저장형 XSS |

5분 만료·소유권 검증은 이 경로를 막지 못한다. 공격자가 **자기 계정으로 올리고 자기 URL을 여는** 것만으로 성립하며, 그 URL은 다른 사용자에게 전달될 수 있다.

**필수 변경 — `storeSubmissionImage` 추출 시 함께 반영한다:**

```java
// FileUtils — 판정값을 버리지 않는 메서드를 추가한다.
// 기존 isMIMEImage(InputStream)는 호출부 변경을 피하기 위해 그대로 남기고,
// 내부에서 이 메서드를 호출하도록 바꾼다.
/** 허용 이미지면 Tika 판정 MIME 을, 아니면 null 을 반환한다. */
public static String detectImageMimeType(InputStream ioStream) throws IOException;
```

```java
// S3Uploader — 세 경로 모두 동일하게 적용
PutObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .contentType(detectedMimeType)            // ← 클라이언트 헤더가 아니라 Tika 판정값
    .contentDisposition("attachment")         // ← 인라인 렌더링 차단 (2차 방어)
    .serverSideEncryption(ServerSideEncryption.AES256)
    .build()
```

- **확장자도 Tika 판정값에서 파생한다.** 클라이언트 파일명의 확장자를 신뢰하지 않는다(`image/jpeg` → `.jpg`, `image/png` → `.png`).
- **적용 범위는 신규 두 경로에 한정되지 않는다.** `uploadProfilePicture`·`uploadPetImage`는 `storeSubmissionImage`를 공유하지 않는 **별개의 `putObject` 호출**이므로, 세 곳을 모두 고쳐야 한다. 기존 프로필·반려동물 사진 경로에 동일한 취약점이 이미 존재한다.
- **검증 기준:** 기존 회귀 테스트로는 잡히지 않는다. §12.1에 전용 케이스를 추가한다.

### 9.4 그 밖의 조치

- **SSE 적용** — `storeSubmissionImage` 추출 시 함께 반영. 현재 `PutObjectRequest`는 버킷/키/contentType만 설정하고 암호화 지정이 없다
- **저장 항목 화이트리스트** — 견주 성명·주소는 컬럼으로 만들지 않고 로그에도 남기지 않는다

### 9.5 잔여 위험

**원본 이미지 자체에는 견주 성명·주소가 그대로 찍혀 있다.** 비공개 버킷·단기 서명 URL·소유권 검증으로 완화하지만 완전히 제거되지는 않는다. 보존 기간과 파기 정책, 이미지 보관 동의 문구는 기획 협의가 필요하다(§13).

---

## 10. 설정

### 10.1 멀티파트 상한 — 선결 조건

> ⚠️ **현재 저장소 어디에도 `spring.servlet.multipart` 설정이 없다.** base `application.yml`도 없어 Spring 기본값이 적용된다 — **최대 1MB.** `FileUploadService.validateImageFile`이 5MB를 검사하지만 그 전에 컨테이너가 `MaxUploadSizeExceededException`으로 막는다. 폰으로 찍은 진단서 사진은 대부분 1MB를 넘으므로 **이 설정 없이는 기능이 동작하지 않는다.**

`application-dev.yml` · `application-prod.yml` · `src/test/resources/application-test.yml` **세 곳 모두에** 추가한다(공통 파일이 없으므로).

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 7MB
      max-request-size: 9MB

upload:
  image:
    # spring.servlet.multipart.max-file-size 와 반드시 같은 값
    max-size-bytes: ${UPLOAD_IMAGE_MAX_SIZE_BYTES:7340032}   # 7MB
```

> ⚠️ **`validateImageFile`의 하드코딩된 5MB를 함께 고쳐야 한다.** `FileUploadService.java:117`은 `long maxFileSize = 5 * 1024 * 1024;`로 상한을 **코드에 박아** 두고 있다. 컨테이너 상한만 10MB로 올리면 **7MB 사진이 컨테이너를 통과한 뒤 `INVALID_IMAGE`로 거부**되는, 설정과 코드가 서로 다른 상한을 주장하는 상태가 된다.
>
> 상한의 단일 출처는 yml로 둔다 — `FileUploadService`가 `@Value("${upload.image.max-size-bytes}")`로 주입받게 바꾼다. 이 변경은 프로필·반려동물 사진 업로드에도 함께 적용되므로, 세 경로의 상한이 처음으로 일치하게 된다.

**7MB로 정한 근거 (아래로도 위로도 못 가는 값):** 진단서는 인쇄된 작은 글자를 OCR로 읽어야 하므로 해상도를 낮출 수 없다 — 5MB로 압축을 강제하면 OCR 신뢰도가 떨어져 `OCR_RECOGNITION_FAILED` 거부율이 올라간다. 위쪽은 Vision이 막는다: Vision은 이미지를 **base64로 JSON 본문에 담아** 보내므로 크기가 약 1.33배로 불어나고, 10MB 사진은 요청 본문 약 13.4MB가 되어 요청 크기 한도를 넘길 위험이 있다(넘기면 Vision이 4xx를 내고 §8.3 매핑에 따라 500으로 노출돼 사용자가 원인을 알 수 없다). 7MB → base64 약 9.3MB로 안전하게 들어간다.

> **착수 전 확인:** Vision `images:annotate`의 요청 크기 한도와 인라인 `content` 최대 크기를 공식 문서에서 확인한다. 가정과 다르면 **yml의 숫자만** 조정한다 — 상한의 단일 출처가 yml이므로 코드는 건드리지 않는다.

**동시 요청 한계도 함께 계산한다.** 동기 경로는 `file.getBytes()`로 파일 전체를 힙에 올린 뒤 OCR 응답을 최대 15초 기다린다. 파일 상한 7MB × 동시 요청 수가 그대로 힙 사용량이 되므로, 상한을 올리는 것과 §5.5(b)의 빈도 제한은 **함께 적용해야 의미가 있다.** 상한만 올리고 제한을 빼면 고갈 한계만 7MB 단위로 커진다.

### 10.2 OCR 설정

```yaml
ocr:
  google:
    # GCP 콘솔에서 Cloud Vision API를 활성화한 뒤 발급한 API 키.
    # 반드시 "API 제한"에서 Cloud Vision API만 허용하도록 제한한다.
    api-key: ${GOOGLE_VISION_API_KEY}
    endpoint: https://vision.googleapis.com/v1/images:annotate
    # 한국어 진단서이므로 languageHints로 ko를 명시한다.
    language-hint: ko
    connect-timeout-seconds: 10
    read-timeout-seconds: 15
  certificate:
    min-confidence: 0.75
  # 사용자 단위 최소 요청 간격(초). 유료 OCR 호출에만 적용된다 (§5.5)
  user-interval:
    seconds: ${OCR_USER_INTERVAL_SECONDS:30}

health-certificate:
  image:
    presigned-url-expiration-seconds: 300
```

**테스트 yml에는 반드시 기본값을 넣는다**(`${GOOGLE_VISION_API_KEY:test-api-key}` 형태). 엔드포인트 기본값은 **절대 외부로 나가지 않는 로컬 주소**(`http://localhost:9999/v1/images:annotate`)로 둔다 — 테스트가 실수로 과금되는 실제 Vision 호출을 하지 않게 하는 안전장치다. 기본값 없는 키를 `@Value`로 주입하면 `@SpringBootTest` 컨텍스트가 환경변수 없이는 뜨지 않는다 — 삭제된 `clients/naver/*`가 정확히 그 함정이었다. 반복하지 않는다.

### 10.3 README 갱신

`README.md` 환경변수 안내에 `GOOGLE_VISION_API_KEY` · `UPLOAD_IMAGE_MAX_SIZE_BYTES` · `OCR_USER_INTERVAL_SECONDS` · `HEALTH_CERTIFICATE_IMAGE_URL_EXPIRATION_SECONDS` 를 MacOS/Linux · Windows(PowerShell) 양쪽 형식으로 추가한다. API 키는 GCP 콘솔에서 Cloud Vision API만 허용하도록 제한하라는 주의를 함께 적는다.

---

## 11. 구현 파일 목록

### 11.1 신규 (재사용 덕분에 최소화됨)

| 계층 | 경로 |
|---|---|
| 마이그레이션 | `db/migration/V55__create_diagnosis_attempts.sql`, `db/migration/V56__create_pet_health_certificates.sql` |
| 도메인 | `domain/PetHealthCertificate.java`, `domain/DiagnosisAttempt.java` |
| 리포지토리 | `repositories/PetHealthCertificateRepository.java`, `repositories/DiagnosisAttemptRepository.java` |
| 서비스 | `services/HealthCertificateService.java`, `services/DiagnosisAttemptLimiter.java` |
| 클라이언트 | `clients/ocr/HealthCertificateOcrClient.java`, `clients/ocr/GoogleVisionOcrClient.java`, `clients/ocr/HealthCertificateFieldExtractor.java`|
| DTO | `dto/health/HealthCertificateResult.java`, `UpdateHealthCertificateRequest.java`, `ImageUrlResponse.java`, `dto/health/ocr/HealthCertificateOcrResult.java`|
| 예외 | `exceptions/ServiceUnavailableException.java` |
| 검증 | `validation/UpdateHealthCertificateRequestErrorCode.java` |

**신규 컨트롤러 없음. 신규 저장 컴포넌트 없음. 신규 목록·삭제 엔드포인트 없음.**

### 11.2 수정 (재사용 지점)

| 경로 | 변경 |
|---|---|
| `utils/ImageUploader.java` | `storeSubmissionImage(event, prefix)` · `delete(key)` 추가 |
| `utils/S3Uploader.java` | `putObject` 본문을 동기 메서드로 추출, 기존 리스너가 호출하도록 변경, 삭제 구현, SSE 반영. **세 `putObject` 호출 전부에 Tika contentType + `contentDisposition("attachment")` + Tika 기준 확장자 적용** (§9.3) |
| `utils/FileUtils.java` | `detectImageMimeType(InputStream)` 추가 — Tika 판정 MIME 을 버리지 않고 반환 (§9.3) |
| `services/FileUploadService.java` | 하드코딩 5MB 상한을 `@Value("${upload.image.max-size-bytes}")` 주입으로 전환 (§10.1) |
| `utils/LocalImageUploader.java` | 신규 인터페이스 메서드 구현 |
| `utils/ImgUtils.java` | `getSecureImgUrl(key, ttl)` 추가 |
| `utils/DevImgUtils.java` · `ProdImgUtils.java` | `createPresignedUrl` TTL 파라미터화, `getImgUrlForAIWorker`를 위임으로 변경 |
| `src/test/.../TestImageConfig.java` | 신규 `ImgUtils` 메서드 구현 |
| `services/SubmissionService.java` | `createCompletedSubmission` + 상세 조회 1종 추가 |
| `domain/SubmissionTypeEnum.java` | `HEALTH_CERTIFICATE` 추가 |
| `utils/CeleryTaskSender.java` | `if/else`를 `switch`로 전환, 신규 유형은 예외 |
| `repositories/SubmissionRepository.java` | `findTopByPetAndTypeOrderBySubmittedAtDesc` 추가 |
| `repositories/SubmissionSpecifications.java` | `type` 조건 추가 (+ `filterPetSubmissions`의 `DELETED` 비대칭 정리) |
| `services/AIDiagnosisClientService.java` | 빈도 제한을 유형별로 분리 (§5.5a) |
| 빈도 제한 시도 저장소 (Redis 또는 `V54` 신규 테이블) | 신규 동기 엔드포인트용. `Submission` 행 기준으로는 작동하지 않음 (§5.5c) |
| `config/RedisConfig.java` | Redis 카운터를 택할 경우 `@Profile("dev")` → prod 포함으로 확장 (§5.5c) |
| `dto/diagnosis/get/SubmissionMapper.java` | 매퍼 1종 추가 (`photoUrl`은 `null`) |
| `controllers/PetController.java` | 업로드 엔드포인트 1개 추가 |
| `controllers/SubmissionController.java` | 상세 1 · PATCH 1 · 이미지 1 추가, 목록에 `type` 파라미터 |
| `validation/ErrorCode.java` · `ValidErrorCodeFinder.java` · `handlers/GlobalExceptionHandler.java` | §7 참고 |
| `application-dev.yml` · `application-prod.yml` · `application-test.yml` · `README.md` | §10 참고 |

---

## 12. 테스트 전략

저장소 관례인 **`XxxUT`(Mockito 순수 단위) / `XxxTest`(`@SpringBootTest` + `@ActiveProfiles("test")` + H2) 쌍**을 따른다. OCR 클라이언트는 인터페이스이므로 UT에서는 `@Mock`, 통합 테스트에서는 `@MockitoBean`으로 대체한다.

### 12.1 필수 거부 경로 테스트

| 케이스 | 기대 결과 |
|---|---|
| OCR 신뢰도 미달 | 400 `OCR_RECOGNITION_FAILED` + **`storeSubmissionImage`와 `saveSubmission`이 한 번도 호출되지 않음** (`verify(..., never())`) |
| 병명 누락 | 400 `OCR_REQUIRED_FIELD_MISSING`, 저장 없음 |
| 진단서 아닌 이미지 | 400 `NOT_A_HEALTH_CERTIFICATE`, 저장 없음 |
| OCR 타임아웃 | 503 `OCR_SERVICE_UNAVAILABLE`, 저장 없음 |
| S3 성공 후 DB 실패 | `imageUploader.delete(key)`가 호출됨 |
| 타인의 반려동물 | **404** `RESOURCE_NOT_FOUND` (403 아님) |
| 날짜 파싱 실패 | 201 성공, `diagnosed_date`는 `NULL`, `diagnosed_date_text`는 원문 유지 |

"저장 없음"을 **응답 코드가 아니라 mock 호출 횟수로 검증**하는 것이 이 기능의 핵심 테스트다.

### 12.1.1 보안 회귀 테스트 (§9.3 · §5.5)

위 거부 경로 테스트로는 잡히지 않는다. 별도로 추가한다:

| 케이스 | 기대 결과 |
|---|---|
| 파트 헤더 `Content-Type: text/html` + 실제 JPEG 바이트 | `PutObjectRequest`의 `contentType`이 **`image/jpeg`**, `contentDisposition`이 **`attachment`** (`ArgumentCaptor`로 검증) |
| 파일명 `x.jpg` + 실제 PNG 바이트 | S3 key 확장자가 **Tika 판정 기준** `.png` |
| 프로필·반려동물 사진 업로드 | 위 두 케이스가 **동일하게** 성립 — 세 경로 모두 고쳤는지 확인 |
| 인식 실패(400)를 연속 2회 요청 | 두 번째 요청이 `REQUEST_TOO_FREQUENT`로 거부되고 **OCR 클라이언트가 1회만 호출됨** — §5.5c 함정의 직접 검증 |
| 상한 초과 파일 (yml 상한 + 1B) | `MultipartException` → 400. 코드 상한과 yml 상한이 **같은 값**인지 확인 (§10.1) |

### 12.2 재사용으로 인한 회귀 테스트 (필수)

기존 코드를 건드리므로 아래를 반드시 확인한다:

| 대상 | 확인 |
|---|---|
| `S3Uploader` 리팩터링 | 기존 `AIDiagnosisClientServiceTest` · `FileUploadServiceTest`가 그대로 통과 — 눈·소변 비동기 업로드 동작 불변 |
| `SubmissionTypeEnum` 확장 | `CeleryTaskSender`가 신규 유형을 소변 분기로 처리하지 않음 |
| 빈도 제한 분리 | 진단서 업로드 직후 눈 진단이 `REQUEST_TOO_FREQUENT`로 거부되지 않음 |
| 목록 `type` 필터 | 필터 없이 호출하면 기존과 동일한 결과 |
| `ImgUtils` 인터페이스 확장 | `TestImageConfig` 포함 전 구현체가 컴파일되고 `SubmissionMapper` 기존 동작 불변 |

---

## 13. 협의 필요

### 13.1 프론트엔드

- multipart 파트 이름 — 기존 눈·소변과 동일하게 `image` 확정 여부
- 수정 화면이 PATCH로 보낼 필드 범위 — 전체 전송 vs 변경분만
- 서명 URL 만료(5분) 후 재요청 플로우
- 에러 코드별 사용자 안내 문구 — 특히 재촬영을 유도해야 하는 `OCR_RECOGNITION_FAILED` / `NOT_A_HEALTH_CERTIFICATE`
- 목록은 기존 `/api/submissions` 계열에 `type` 파라미터를 붙이는 방식으로 합의
- 업로드 중 로딩 UI — 동기 처리라 OCR 응답까지 최대 15초 대기 가능

### 13.2 기획 · 법무

- 진단서 원본 이미지 보존 기간과 파기 정책
- 견주 성명·주소가 포함된 이미지 보관에 대한 이용자 동의 문구
- 반려동물 삭제 시 기록·이미지 동반 삭제 여부 (DB는 `ON DELETE CASCADE`지만 S3 객체는 별도 처리 필요)
- **OCR 벤더 확정 + 개인정보 국외 이전 검토** — 현재 방향은 Google Cloud Vision(§8.1, 미확정)으로, 진단서 이미지가 해외 리전으로 전송된다. 국외 이전 고지·동의가 필요한지, 필요하다면 동의 문구와 수집 항목 고지에 어떻게 반영할지 확인이 필요하다. 막히면 국내 벤더로 전환하며, 그 영향 범위는 §8.1에 적혀 있다

---

## 14. 선결 과제 요약

구현 착수 전 반드시 해결해야 하는 항목:

1. **멀티파트 상한 설정 + 코드 상한 일치** — 없으면 1MB 초과 사진이 전부 거부된다. yml만 올리고 `validateImageFile`의 하드코딩 5MB를 두면 설정과 코드가 다른 상한을 주장한다 (§10.1)
2. **`S3Uploader` 동기 메서드 추출** — 기존 `@Async @TransactionalEventListener` 경로로는 "실패 시 미저장"이 성립하지 않는다 (§2.3)
3. **`getSecureImgUrl` 추가** — 기존 `getImgUrl`을 쓰면 prod에서 개인정보가 무서명 URL로 공개된다 (§2.5, §9.2)
4. **저장 `contentType`을 Tika 판정값으로 전환** — 클라이언트 헤더를 그대로 저장하면 서명 URL로도 막히지 않는 저장형 XSS가 성립한다. 신규 경로만이 아니라 **프로필·반려동물 사진 경로에도 이미 존재하는 결함**이다 (§9.3)
5. **빈도 제한 — 유형별 분리 + 신규 엔드포인트 적용 + 시도 기준 저장소 결정** — 유형 분리를 빼면 기존 눈·소변 진단에 교차 간섭이 생기고, 신규 동기 엔드포인트에 적용하지 않으면 유료 OCR 비용과 스레드가 무방비다. 특히 **`Submission` 행 기준으로 세면 인식 실패 요청이 제한을 전부 빠져나간다** (§5.5)
6. **OCR 벤더 확정 + Vision API 키 발급** — 현재 방향은 Google Cloud Vision이나 **아직 확정이 아니다**(§8.1). `HEALTH-01`의 나머지(업로드·저장·조회·수정·빈도 제한)는 벤더와 무관하게 진행 가능하며, 실제 OCR 호출 구현(`GoogleVisionOcrClient`)만 이 확정을 기다린다. §8.2의 인터페이스 분리를 지키면 벤더 변경의 영향이 구현체 하나와 `ocr.google.*` 키로 한정된다
