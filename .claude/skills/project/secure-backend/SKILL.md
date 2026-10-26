---
name: secure-backend
classification: workflow
classification-reason: "진단(감사) → 우선순위 → 패치 → 검증의 순차 단계를 가지며, 매 단계가 이 리포지토리의 실제 파일을 대상으로 한다. 단순 패턴 참조가 아니라 실행 절차이므로 workflow."
deprecation-risk: none
effort: medium
description: |
  Spring Boot 3 / JWT 백엔드(smarthealthdog)의 방어 계층을 감사하고 구현한다.
  인증·인가, IDOR, 레이트리밋, 파일 업로드, SSRF, 비밀값 관리, 보안 헤더, 로깅까지
  15개 방어 계층을 실제 코드 경로에 매핑해 점검하고 패치한다.
  Triggers: /secure-backend, /secure-backend audit, /secure-backend fix {layer}, /secure-backend verify
  Keywords: 보안, 보안 점검, 취약점, 하드닝, 방어, 인증, 인가, 레이트리밋, 업로드 검증, 비밀키,
    security, harden, hardening, vulnerability, audit, OWASP, IDOR, SSRF, rate limit, JWT, CORS, CSP
argument-hint: "[audit | fix <layer-no|layer-name> | verify] (예: /secure-backend fix 7)"
user-invocable: true
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
---

# secure-backend

이 리포지토리(`com.smarthealthdog.backend`)의 백엔드 방어 계층을 **실제 파일 경로 기준으로**
감사하고 구현하는 워크플로우. 일반론이 아니라 아래 15계층 표에 적힌 파일만 본다.

## When to use

- "보안 점검해줘", "취약점 찾아줘", "하드닝", "방어 코드 추가" 요청
- 신규 컨트롤러/엔드포인트 추가 직후 (특히 `@PreAuthorize`만 붙인 경우 → 계층 4 필수)
- 배포 전 (`main` push = CI/CD 트리거) 최종 게이트
- 외부 입력을 다루는 코드 변경 시: 파일 업로드, Kakao 클라이언트, Celery 메시지

## When NOT to use

- 프론트엔드 보안, 인프라/네트워크(SG, WAF) → 범위 밖. `bkit:infra-architect`로 넘긴다.
- 단순 코드 품질/리팩터링 → `bkit:code-review`.

---

## Step 1 — Audit (항상 먼저 실행)

인자 없이 호출되거나 `audit`이면 15계층 전체를 점검한다. 각 계층마다:

1. 표의 **점검 명령**을 그대로 실행한다 (Grep/Read).
2. 결과를 `PASS` / `GAP` / `N/A(사유)` 로 판정한다. **추측 금지** — 파일을 읽어서 확인한다.
3. `GAP`이면 심각도(`Critical`/`High`/`Medium`/`Low`)와 근거 `파일:줄`을 기록한다.

출력은 아래 형식의 단일 표 + Critical/High 항목만 상세 설명:

```
| # | 계층 | 상태 | 심각도 | 근거 |
|---|------|------|--------|------|
| 5 | Swagger 노출 | GAP | High | security/SecurityConfig.java:72 — prod에서도 permitAll |
```

마지막에 `다음: /secure-backend fix <번호>` 를 제안한다. **감사 단계에서 코드를 수정하지 않는다.**

---

## 15 Defense Layers

| # | 계층 | 점검 대상 | 점검 명령 / 확인 포인트 |
|---|------|-----------|------------------------|
| 1 | 인증 필터 무결성 | `security/JWTTokenFilter.java`, `security/AuthEntryPointJWT.java` | 토큰 파싱 실패 시 `SecurityContext`가 비워지고 체인이 **중단**되는지. 필터가 `ErrorMessage`를 직접 직렬화하므로 예외 누락 시 500 대신 익명 통과가 되지 않는지. |
| 2 | JWT 서명·클레임 | `utils/JWTUtils.java` | `jwt.secret` 바이트 길이 ≥ 32(HS256 256bit) 시작 시 검증. 파싱 시 알고리즘 고정(`verifyWith(key)`), `none` 수락 불가. `iss`/`aud` 클레임 부재 → 다른 환경 토큰 교차 수락 위험. 만료 외 `nbf` 미사용. |
| 3 | 공개 경로 최소화 | `security/SecurityConfig.java:69-77` | `permitAll` 목록 4개(`/api/auth/**`, `/uploads/**`, `/swagger-ui/**`, `/v3/api-docs/**`)가 **prod에서도 열려 있다**. `/uploads/**`와 Swagger는 dev 전용으로 분리해야 한다(계층 5). `/api/auth/**` 하위에 민감 엔드포인트가 새로 추가됐는지 `controllers/AuthController.java`로 확인. |
| 4 | IDOR / 소유권 검증 | `controllers/PetController.java`, `SubmissionController.java`, `WalkController.java`, `services/*` | **권한 기반 인가(`@PreAuthorize("hasAuthority('can_edit_pet')")`)는 "내 펫인지"를 검증하지 않는다.** 모든 `{id}` 경로 파라미터에 대해 서비스 계층에서 `userId` 소유권 비교가 있는지 확인. 없으면 Critical. |
| 5 | 프로파일별 노출 분리 | `security/SecurityConfig.java`, `config/SwaggerConfig.java`, `config/StaticResourceConfig.java` | Swagger·`/uploads/**`가 prod에 노출되는지. dev/prod 필터체인을 `@Profile`로 분리(이 프로젝트 관례: 프로파일별 빈). |
| 6 | CORS | `security/SecurityConfig.java` | `cors()` 설정이 **아예 없다** → 명시적 allowlist 필요. `allowedOrigins("*")` + `allowCredentials(true)` 조합 금지. |
| 7 | 레이트리밋 / 브루트포스 | `controllers/AuthController.java`, `services/AuthService.java`, `services/EmailVerificationService.java` | 로그인, 리프레시, 이메일 인증코드 발송에 시도 제한이 있는지. AI 추론만 `inference-service.interval.seconds`로 보호됨. Redis는 `config/RedisConfig.java`가 **dev 전용 프로파일**이므로 prod 레이트리밋 저장소를 먼저 결정해야 한다. |
| 8 | 계정 열거 / 타이밍 | `services/CustomUserDetailsService.java`, `services/AuthService.java`, `validation/ErrorCode.java` | "없는 이메일"과 "틀린 비밀번호"가 **같은 `ErrorCode`**로 응답되는지. 밴/삭제/`SOCIAL_ACCOUNT_USER` 거부 메시지가 계정 상태를 누설하지 않는지. 미존재 사용자일 때 BCrypt 비교를 건너뛰어 응답 시간이 갈리지 않는지. |
| 9 | 입력 검증 완결성 | `validation/ValidErrorCodeFinder.java`, `validation/*ErrorCode.java`, `dto/**` | 모든 `@RequestBody` DTO에 `@Valid`가 붙었는지. 새 DTO는 `<Dto>ErrorCode` 컴포넌트 + `ValidErrorCodeFinder`의 `case` **둘 다** 있어야 한다(없으면 검증 실패가 코드 없이 새어나간다). 컨트롤러 시그니처에 `domain/` 엔티티 직접 바인딩(mass assignment) 금지. |
| 10 | 파일 업로드 | `utils/FileUtils.java`, `services/FileUploadService.java`, `utils/S3Uploader.java` | Tika MIME 검사(`isMIMEImage`) 통과 후 **같은 스트림을 다시 읽을 수 있는지**(reset/버퍼링 — 아니면 0바이트 업로드). 최대 크기 제한(`spring.servlet.multipart.max-file-size`). S3 key에 사용자 제공 파일명을 그대로 쓰지 않기(경로 traversal·헤더 인젝션) → UUID 키. `Content-Type`을 클라이언트 값이 아닌 검증된 MIME으로 세팅. |
| 11 | SSRF / 아웃바운드 | `utils/OAuthClient.java`, `config/WebClientConfig.java` | 사용자 입력이 URL 경로/호스트에 들어가지 않는지(쿼리 파라미터만). 리다이렉트 추종 제한, 응답 타임아웃·크기 제한 설정 여부. Kakao `code` 검증. |
| 12 | 신뢰 경계: AI 워커 콜백 | `controllers/SubmissionController.java`, `services/SubmissionService.java`, `utils/CeleryTaskSender.java` | `PATCH /api/submissions/{id}/eye|urine|status`는 외부 Python 워커가 호출한다 → `AI_MODEL_SERVICE` 역할 + `can_update_health_records` 권한 **및** 상태 전이 검증(`PROCESSING`에서만 완료 수락, `COMPLETED`/`DELETED` 종단)이 모두 걸려 있는지. Celery 메시지에 들어가는 presigned URL의 TTL(dev 1h)과 로그 노출. |
| 13 | 비밀값 / 설정 | `application-{dev,prod}.yml`, `db/migration/V*.sql`, `.github/workflows/`, `docker-compose*.yml` | 하드코딩된 키·비밀번호·토큰이 커밋되지 않았는지. Flyway placeholder(`${ai.user.password-hash}`)가 로그에 찍히지 않는지. `AI_MODEL_SERVICE_*` 자격증명 로테이션 경로. `.env` 류가 `.gitignore`에 있는지. |
| 14 | 보안 헤더 / 에러 노출 | `security/SecurityConfig.java`, `handlers/GlobalExceptionHandler.java` | HSTS, `X-Content-Type-Options`, `frameOptions`, API용 최소 CSP 명시. `GlobalExceptionHandler`가 **모든** 예외를 `ErrorMessage(codes, messages)`로 변환하고 스택트레이스/SQL/클래스명을 내보내지 않는지. `Exception` catch-all 핸들러 존재 여부. |
| 15 | 로깅 / 감사 추적 | 전역 (`Grep "log\.\(info\|debug\|error\)"`) | 토큰·비밀번호·presigned URL·이메일 전문이 로그에 찍히지 않는지. 로그인 성공/실패, 권한 거부, 밴 처리(`domain/UserAccountAction`)가 감사 기록으로 남는지. |

---

## Step 2 — Fix

`fix <번호|이름>` 이면 해당 계층만 패치한다. 여러 계층을 한 번에 고치지 않는다(리뷰 불가 PR 방지).

패치 규칙 — 이 리포지토리 관례를 반드시 따른다:

- 새 예외는 `exceptions/CustomException` 계열 + `validation/ErrorCode`에 `CODE("한국어 메시지")` 추가.
  자유 문자열 에러 금지.
- 새 검증 DTO면 `<Dto>ErrorCode` 컴포넌트와 `ValidErrorCodeFinder`의 `case`를 **함께** 추가.
- 신규 클래스는 Lombok `@RequiredArgsConstructor` + `private final` 생성자 주입.
- 서비스 메서드 Javadoc은 한국어, `@throws`로 `ErrorCode` 보유 예외를 명시.
- 외부 의존성은 인터페이스 + 프로파일별 구현(`ImgUtils`/`ImageUploader`/`EmailService` 패턴)으로 숨긴다.
  비즈니스 코드에서 `@Profile` 분기 금지.
- 엔티티를 바꿨으면 `src/main/resources/db/migration/V{next}__*.sql` 신규 추가.
  **적용된 마이그레이션은 절대 수정하지 않는다.**
- 테스트는 쌍으로: `XxxUT.java`(Mockito 순수 단위) + `XxxTest.java`(`@SpringBootTest` + `@ActiveProfiles("test")`).
  보안 패치는 **거부 케이스(403/401)를 반드시 테스트**한다.
- `main`에 직접 커밋/머지 금지. 브랜치는 `fix/SEC-<n>-설명`, 커밋은 `Fix: ...` / `Settings: ...`.

### 계층별 패치 스케치

**4 (IDOR)** — 서비스 계층에 소유권 가드를 한 곳에 모은다:

```java
/**
 * 요청자가 해당 펫의 소유자인지 검증한다.
 * @throws ForbiddenException 소유자가 아닌 경우 ({@link ErrorCode#PET_NOT_OWNED})
 */
private Pet requireOwnedPet(long petId, long userId) {
    return petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new ForbiddenException(ErrorCode.PET_NOT_OWNED));
}
```

`findById` 후 비교가 아니라 **쿼리 자체에 `userId`를 넣어** 존재 여부 누설까지 막는다.

**5 (프로파일 분리)** — `permitAll` 목록을 프로파일별 빈으로 쪼갠다:

```java
@Bean
@Profile("dev")
SecurityFilterChain devChain(HttpSecurity http) throws Exception { /* + swagger, /uploads/** */ }

@Bean
@Profile("prod")
SecurityFilterChain prodChain(HttpSecurity http) throws Exception { /* /api/auth/** 만 */ }
```

공통 설정은 `private void applyCommon(HttpSecurity)`로 추출해 드리프트를 막는다.

**7 (레이트리밋)** — `RateLimiter` 인터페이스 + 프로파일별 구현(dev: 인메모리, prod: Redis)으로
관례를 지킨다. 키는 `publicId`가 아직 없는 로그인 단계에선 `이메일 + 클라이언트 IP` 복합키,
초과 시 `ErrorCode.TOO_MANY_REQUESTS` → 429.

**10 (업로드)** — Tika 검사 전에 바이트를 버퍼로 읽고, 그 버퍼로 검사·업로드를 모두 수행:

```java
byte[] bytes = file.getBytes();                       // 크기 제한 선행 확인
if (!FileUtils.isMIMEImage(new ByteArrayInputStream(bytes))) {
    throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE_TYPE);
}
String key = "%s/%s.%s".formatted(prefix, UUID.randomUUID(), ext); // 사용자 파일명 미사용
```

**14 (헤더)** — API 전용 최소 세트:

```java
.headers(h -> h
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentTypeOptions(Customizer.withDefaults())
    .frameOptions(f -> f.deny())
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
```

단, Swagger UI가 열린 dev 체인에는 CSP를 완화해 적용한다.

---

## Step 3 — Verify

패치 후 증거 없이 완료를 선언하지 않는다:

```bash
./gradlew test --tests '*SecurityConfigTest' --tests '*AuthServiceUT' --tests '*ControllerTest'
./gradlew build -x test   # CI/Dockerfile.dev와 동일 경로
```

- 거부 경로 테스트가 실제로 **실패를 재현한 뒤 통과**했는지 확인(패치 전 red → 후 green).
- 변경 요약: 계층 번호, 수정 파일, 추가된 `ErrorCode`, 추가된 마이그레이션 번호, 테스트 결과를 보고.

## Integration

- 깊은 취약점 분석·OWASP 매핑이 필요하면 `bkit:security-architect` 에이전트로 위임.
- 배포 전 전체 품질 게이트는 `bkit:phase-7-seo-security` → `bkit:phase-8-review` 순.
- 설계 문서와의 불일치는 `bkit:gap-detector`.
