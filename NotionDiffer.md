# 로컬 `OCR/V2` ↔ 원격 `origin/main` 차이

조사 시점 `2026-10-21` · 로컬 HEAD `1a1fb66` · 원격 `origin/main` `61285fa` · 공통 조상 `cf37499`

> **핵심 한 줄**
> 두 브랜치는 **거의 겹치지 않는다.** main은 보호소·산책·조도를, 우리는 OCR을 건드렸다.
> 실제 충돌은 `application-test.yml` **딱 1개**이고, 그 해결책은 "우리 것 그대로"다.

---

## 1. `git pull`이 거부된 이유

머지가 시작조차 못 하고 안전장치에서 멈췄다. 두 종류다.

| 종류 | 파일 | git 메시지 |
|---|---|---|
| 커밋 안 한 **수정** 4개 | `SunlightController`, `SunlightProgressResponse`, `LightSensorSampleRepository`, `SunlightService` | `Your local changes ... would be overwritten by merge` |
| 커밋 안 한 **새 파일** 3개 | `SunlightControllerTest`, `SunlightControllerUT`, `SunlightServiceUT` | `The following untracked working tree files would be overwritten` |

main의 PR #28(`Feat: 조도 날짜 조회 방식 변경`)이 같은 경로를 건드리기 때문이다.

### 그런데 내용이 이미 같다

막힌 7개 파일 전부 `origin/main` 버전과 **바이트 단위로 동일**하다(줄바꿈 제외).

```
SAME : SunlightController.java          SAME : SunlightControllerTest.java
SAME : SunlightProgressResponse.java    SAME : SunlightControllerUT.java
SAME : LightSensorSampleRepository.java SAME : SunlightServiceUT.java
SAME : SunlightService.java
```

이 조도 작업은 이미 `SunlightV2` 브랜치를 타고 main에 올라갔다. git은 "결과가 같은지"를 따지지 않고 **"경로가 더러운지"만** 보기 때문에 막은 것이다. 로컬 사본을 버려도 잃는 것이 없다.

---

## 2. 커밋 분기

### 우리만 가진 커밋 (2개)

| 커밋 | 내용 |
|---|---|
| `d270449` | `Feat: 건강검진표 OCR 등록 기능 구현` |
| `1a1fb66` | `Merge remote-tracking branch 'origin/main' into OCR/V2` |

### main만 가진 커밋 (15개, PR #28~#34)

| 커밋 | 내용 |
|---|---|
| `d4ae05c` | `Settings: 테스트 프로파일에 naver 검색 설정 추가` |
| `67c5b99` | `Feat: 조도 날짜 조회 방식 변경` (PR #28 `SunlightV2`) |
| `4ecc154` | `API수정 타입 매치실패 해결` (PR #29) |
| `c6a355d` | `유저 컨트롤러 테스트` (PR #30) |
| `6588229` | `보호소 API 수정` (PR #31) |
| `1e37f21` | `보호소 수정 및 삽입` (PR #32) |
| `4edccd1` | `나의 실수` (PR #33) |
| `ed5003c` | `보호소 수정` (PR #34) |

---

## 3. main에서 들어올 변경 (17파일, +3,526 / −343)

| 파일 | 증감 | 성격 |
|---|---|---|
| `services/ShelterSearchService.java` | +845 | 보호소 검색 대수선 |
| `services/WalkService.java` | +445 | 산책 로직 |
| `clients/naver/NaverReverseGeocodingClient.java` | +290 | 역지오코딩 |
| `repositories/ShelterSearchRepository.java` | +167 | 지역 검색 쿼리 |
| `controllers/ShelterSearchController.java` | +169 | 보호소 API |
| `controllers/UserController.java` | +153 | 유저 API |
| `repositories/WalkRepository.java` | +141 | 산책 쿼리 |
| `services/SunlightService.java` | +102 | **조도 — 이미 로컬에 있음** |
| `controllers/SunlightController.java` | +34 | **조도 — 이미 로컬에 있음** |
| `repositories/LightSensorSampleRepository.java` | +8 | **조도 — 이미 로컬에 있음** |
| `dto/sunlight/SunlightProgressResponse.java` | +2 | **조도 — 이미 로컬에 있음** |
| `db/migration/V55__seed_shelters.sql` | 신규 **0바이트** | 빈 파일 |
| `db/migration/V56__seed_shelters.sql` | +162 | 보호소 시드 |
| `test/.../SunlightServiceUT.java` | +578 | **이미 로컬에 있음** |
| `test/.../SunlightControllerTest.java` | +452 | **이미 로컬에 있음** |
| `test/.../SunlightControllerUT.java` | +311 | **이미 로컬에 있음** |
| `test/resources/application-test.yml` | +10 | naver 블록 — **유일한 충돌 지점** |

---

## 4. 우리만 가진 변경 (51파일, OCR 기능 전체)

main에는 아예 없는 것들이다. 머지로 사라지지 않는다.

| 영역 | 파일 |
|---|---|
| OCR 클라이언트 | `clients/ocr/GoogleVisionOcrClient`, `HealthCertificateOcrClient`, `HealthCertificateFieldExtractor` |
| 도메인 | `domain/PetHealthCertificate`, `domain/DiagnosisAttempt` |
| 서비스 | `services/HealthCertificateService`, `services/DiagnosisAttemptLimiter` |
| 리포지토리 | `repositories/PetHealthCertificateRepository`, `DiagnosisAttemptRepository` |
| DTO | `dto/health/HealthCertificateResult`, `ImageUrlResponse`, `UpdateHealthCertificateRequest`, `dto/health/ocr/HealthCertificateOcrResult` |
| 예외·검증 | `exceptions/ServiceUnavailableException`, `validation/UpdateHealthCertificateRequestErrorCode` |
| 수정 | `PetController`, `SubmissionController`, `SubmissionService`, `AIDiagnosisClientService`, `FileUploadService`, `ImgUtils` 계열 5개, `S3Uploader`, `ErrorCode`, `ValidErrorCodeFinder`, `GlobalExceptionHandler` |
| 테스트 | `HealthCertificateServiceTest/UT`, `DiagnosisAttemptLimiterUT`, `HealthCertificateFieldExtractorUT`, `S3UploaderUT` 외 |
| 마이그레이션 | `V54__create_diagnosis_attempts`, `V55__create_pet_health_certificates` |

---

## 5. 충돌 1건 — `src/test/resources/application-test.yml`

`git merge-tree`로 미리 돌려본 결과, 충돌은 이 파일 하나다.

```
CONFLICT (content): Merge conflict in src/test/resources/application-test.yml
```

양쪽이 **파일 끝에** 블록을 덧붙여서 생긴 위치 충돌이다. 내용 자체는 싸우지 않는다.

| | 추가한 내용 |
|---|---|
| main (`d4ae05c`) | `naver.search.*`, `naver.maps.*` (주석까지 포함, 10줄) |
| 우리 (`d270449`) | `spring.servlet.multipart.*`, `upload.image.max-size-bytes`, `ocr.google.*`, `ocr.certificate.min-confidence`, `ocr.user-interval.seconds`, `health-certificate.image.*` **+ main과 똑같은 naver 블록** |

**우리 쪽이 main의 naver 블록을 글자 그대로 이미 포함하고 있다.** 따라서 해결은 `--ours` 하나로 끝난다. main에서 더 가져올 내용이 없다.

---

## 6. Flyway 버전 번호 충돌 (머지와 별개, 더 위험)

머지가 끝나면 바로 터진다. main이 `V55`/`V56`을 가져가 버렸다.

| 버전 | origin/main | 로컬 (untracked) |
|---|---|---|
| V54 | `extend_shelters_for_region_search` | `extend_shelters_for_region_search` (동일) |
| V55 | `seed_shelters` (0바이트) | `create_diagnosis_attempts` ← **충돌** |
| V56 | `seed_shelters` | `create_pet_health_certificates` ← **충돌** |

커밋된 `V54__create_diagnosis_attempts.sql`(= `V54__extend_shelters...`와 번호 중복)를 피하려고 로컬에서 `V55`/`V56`으로 옮겨둔 상태였는데, 그 자리마저 main이 차지했다. **`V57`/`V58`로 다시 옮겨야 한다.**

내용 확인 결과, 로컬 `V55`/`V56`은 커밋된 `V54`/`V55`와 **헤더 주석 한 줄만 다른 순수 번호 변경**이다.

```diff
-- V54: 진단 요청 "시도" 기록 테이블      →  -- V55: 진단 요청 "시도" 기록 테이블
-- V55: 건강검진표(진단서) OCR ...        →  -- V56: 건강검진표(진단서) OCR ...
```

---

## 7. 막히지 않은 로컬 변경 (보존됨)

| 파일 | 상태 | 내용 |
|---|---|---|
| `test/.../SubmissionControllerUT.java` | 수정 | `SubmissionController` 생성자가 2인자(`+ HealthCertificateService`)로 바뀐 것 반영 |
| `db/migration/V54__create_diagnosis_attempts.sql` | 삭제 | 번호 중복 제거 |
| `db/migration/V55__create_pet_health_certificates.sql` | 삭제 | 번호 중복 제거 |
| `CLAUDE.md`, `PLAN.md`, `SPEC.md`, `Notion.md`, `NotionOCR.md`, `NotionDiffer.md` | untracked | 로컬 문서 (추적 안 함) |
| `.bkit/`, `.claude/`, `.superpowers/` | untracked | 툴링 |

---

## 8. 처리 순서

| # | 작업 | 근거 |
|---|---|---|
| 1 | 조도 수정 4개 `git checkout --` 로 되돌리기 | main 버전과 내용 동일, 버려도 손실 0 |
| 2 | 조도 테스트 3개 삭제 | main에서 동일 파일을 받아옴 |
| 3 | `git pull origin main` | 안전장치 해제됨 |
| 4 | `application-test.yml` 충돌을 `--ours`로 해결 | 우리 버전이 main 내용을 모두 포함 |
| 5 | 머지 커밋 완료 | — |
| 6 | 로컬 `V55`/`V56` → `V57`/`V58` 재번호 | main이 `V55`/`V56` 점유 |

---

## 9. 실행 결과

| # | 작업 | 결과 |
|---|---|---|
| 1 | 조도 수정 4개 `git checkout --` | 완료 — 되돌림 |
| 2 | 조도 테스트 3개 제거 | 완료 — 스크래치패드 `sunlight-local-backup/`에 백업 후 이동 |
| 3 | `git pull --no-rebase origin main` | 완료 — 충돌 1건(`application-test.yml`), 예측과 일치 |
| 4 | 충돌 해결 | 완료 — 마커 3줄 제거 = `--ours`와 바이트 단위 동일함을 `git show :2:` 로 검증. main의 naver 블록 보존 확인 |
| 5 | 머지 커밋 | `fb1574c Merge branch 'main' ... into OCR/V2` |
| 6 | 마이그레이션 재번호 | `V55→V57__create_diagnosis_attempts`, `V56→V58__create_pet_health_certificates` (헤더 주석도 함께 수정). 전체 버전 중복 **0건** |

### 머지 후 최종 마이그레이션

```
V54__extend_shelters_for_region_search.sql
V55__seed_shelters.sql           (main, 0바이트)
V56__seed_shelters.sql           (main)
V57__create_diagnosis_attempts.sql          (OCR)
V58__create_pet_health_certificates.sql     (OCR)
```

머지 전에 있던 `V54__create_diagnosis_attempts.sql` / `V55__create_pet_health_certificates.sql` 삭제는 커밋되지 않은 작업 트리 변경으로 그대로 남아 있다(`SubmissionControllerUT.java` 수정도 동일).

---

## 10. main에서 넘어온 기존 결함 — `WalkServiceUT` 컴파일 실패

`./gradlew compileJava compileTestJava` 결과: **`compileJava` 통과, `compileTestJava` 실패.**

```
WalkServiceUT.java:43: error: cannot find symbol
        when(walkRepository.findByPetIdAndStartTimeRange(
  symbol: method findByPetIdAndStartTimeRange(long,Object,Object,Pageable)
```

### 우리 머지 탓이 아니다

| 확인 항목 | 결과 |
|---|---|
| `origin/main`의 `WalkRepository`에 `findByPetIdAndStartTimeRange` | **없음** (`findByPetId`, `...GreaterThanEqual`, `...LessThan`, `...GreaterThanEqualAndStartTimeLessThan` 4개로 쪼개짐) |
| `origin/main`의 `WalkServiceUT`가 그 메서드를 호출 | **호출함** (43행, 50행) |
| `OCR/V2`가 `WalkRepository`/`WalkService`/`WalkServiceUT`를 건드렸나 | **전혀 건드리지 않음** |

즉 `origin/main` **단독으로도 `./gradlew test`가 컴파일 단계에서 깨진다.** 보호소/산책 작업(PR #31 전후)에서 레포지토리를 4개 메서드로 분리하면서 테스트를 같이 고치지 않은 것이다.

### 정확한 수정 방향

`WalkService.listByPet`은 날짜 조건이 둘 다 `null`이면 `walkRepository.findByPetId(petId, pageable)`을 호출한다. `WalkServiceUT`의 테스트가 바로 그 `(null, null)` 경로이므로, 스텁과 검증 대상을 바꾸면 된다.

```diff
-        when(walkRepository.findByPetIdAndStartTimeRange(
-                eq(7L), isNull(), isNull(), any(Pageable.class)))
-                .thenReturn(Page.empty());
+        when(walkRepository.findByPetId(eq(7L), any(Pageable.class)))
+                .thenReturn(Page.empty());
...
-        verify(walkRepository).findByPetIdAndStartTimeRange(
-                eq(7L), isNull(), isNull(), pageableCaptor.capture());
+        verify(walkRepository).findByPetId(eq(7L), pageableCaptor.capture());
```

`isNull` import도 쓰이지 않게 되므로 함께 제거해야 한다. 다만 이 파일은 OCR 작업 범위 밖(PM 쪽 소관)이라, 여기서 고칠지는 별도 판단이 필요하다.

### 수정 완료

`WalkServiceUT.java` 3곳을 고쳤다. (import 1줄 + 스텁 + `verify`)

```diff
-import static org.mockito.ArgumentMatchers.isNull;

-        when(walkRepository.findByPetIdAndStartTimeRange(
-                eq(7L), isNull(), isNull(), any(Pageable.class)))
+        when(walkRepository.findByPetId(eq(7L), any(Pageable.class)))
                 .thenReturn(Page.empty());

-        verify(walkRepository).findByPetIdAndStartTimeRange(
-                eq(7L), isNull(), isNull(), pageableCaptor.capture());
+        verify(walkRepository).findByPetId(eq(7L), pageableCaptor.capture());
```

`assertEquals` 3개(offset 37, pageSize 10, `startTime` ASC)는 그대로 둬서, **"offset을 가공 없이 DB `Pageable`로 넘긴다"는 테스트의 원래 의도는 보존**된다. 바뀐 것은 호출 대상 메서드뿐이다.

### 검증 결과

| 명령 | 결과 |
|---|---|
| `./gradlew compileJava compileTestJava` | **EXIT=0** |
| `./gradlew test --tests 'WalkServiceUT'` | **BUILD SUCCESSFUL** |
| `./gradlew test` (전체) | **BUILD SUCCESSFUL in 2m 9s** |

전체 스위트 집계 — 테스트 클래스 **53개**, 테스트 **423개**, `failures=0` `errors=0` `skipped=0`.

머지된 `OCR/V2`는 이제 컴파일·테스트 모두 깨끗하다.
