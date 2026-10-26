# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 3.5.6-SNAPSHOT / Java 24 REST backend for "똑똑하개 건강하개" — a pet (dog/cat) health app.
Core domains: user auth (local + Kakao social), pets, walk tracking with ambient-light samples,
AI image diagnosis (eye disease / urine strip), and shelter search.
Package root: `com.smarthealthdog.backend`. Gradle project name is `backend`.

## Commands

```bash
# Run (a profile is MANDATORY — see "Profiles" below)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Infrastructure (PostgreSQL 18 + Redis) — needs DB_*/REDIS_* env vars set
docker-compose up -d
# App + infra together, all env vars passed through
docker compose -f docker-compose-dev.yml up --build

./gradlew test                                     # full suite
./gradlew test --tests 'UserServiceUT'             # one class
./gradlew test --tests 'UserServiceUT.someMethod'  # one method
./gradlew test --tests '*ServiceUT'                # pattern
./gradlew build -x test                            # what CI and Dockerfile.dev do
```

There is no lint/format task configured. Test report: `build/reports/tests/test/index.html`.

## Profiles — important

There is **no `application.yml`**, only `application-{dev,prod}.yml` (+ `application-test.yml` in test
resources). Starting without `--spring.profiles.active` leaves the datasource and every `${...}`
placeholder unresolved, so always pass a profile. Every config value comes from environment
variables with no defaults; README.md lists the full required set per feature area.

Profile-conditional beans (grep `@Profile`) differ in ways that change behavior, not just config:

| Concern | dev | prod | test |
|---|---|---|---|
| Quartz triggers (`config/QuartzConfig`) | registered | **not registered** — the AI inference jobs do not run | `auto-startup: false` |
| `RedisConfig` | dev only | — | — |
| Email (`services/*EmailService`) | `DevEmailService` (dev, test) | `ProdEmailService` | `DevEmailService` |
| Image URLs (`utils/ImgUtils`) | `DevImgUtils` → 1h S3 presigned URLs | `ProdImgUtils` → CloudFront | `TestImageConfig.TestImgUtils` (in src/test) |
| Upload target (`utils/ImageUploader`) | `S3Uploader` | `S3Uploader` | — (`LocalImageUploader` is `@Profile("local")`, effectively unused) |

Tests use H2 in-memory (`ddl-auto: create-drop`, Flyway disabled) via `@ActiveProfiles("test")`.

## Architecture

**Layering:** `controllers` → `services` → `repositories` (Spring Data JPA) over `domain` entities;
`dto` holds request/response types, `clients`/`utils` hold outbound integrations. Controllers are
thin: they unwrap the principal, call one service method, and return `ResponseEntity`.

**Database is migration-owned.** `ddl-auto: none` in dev/prod; the schema lives in
`src/main/resources/db/migration/V*__*.sql` (Flyway, 52 migrations, `baseline-on-migrate: true`).
Any entity change needs a new numbered migration — never edit an applied one. Migrations also seed
roles/permissions/conditions and the AI service account, which is injected through Flyway
placeholders `${ai.user.email}` / `${ai.user.password-hash}` from the `AI_MODEL_SERVICE_*` env vars.
Quartz's own tables come from `V33__create_tables_for_quartz.sql` (`initialize-schema: never`).

**Auth and the two user identifiers.** Stateless JWT; `SecurityConfig` permits only
`/api/auth/**`, `/uploads/**`, `/swagger-ui/**`, `/v3/api-docs/**`. The subtle part:

- the JWT **subject is `user.publicId` (UUID)**;
- `CustomUserDetailsService.loadUserByUsername` is overloaded by argument format — a UUID means
  "already-authenticated request", loads role+permissions and rejects banned/deleted users; a
  non-UUID is treated as an **email** for password login and additionally rejects
  `SOCIAL_ACCOUNT_USER`;
- it then returns a `UserDetails` whose username is the **internal numeric `user.id`**, which is why
  controllers do `Long.parseLong(userDetails.getUsername())`.

Authorization is permission-based, not role-based: `@PreAuthorize("hasAuthority('can_edit_pet')")`
using the snake_case strings from `domain/PermissionEnum` (`PermissionConverter` is an
`@Converter(autoApply = true)`, so the DB stores `can_edit_pet`, not `CAN_EDIT_PET`). Roles
(`RoleEnum`) are only permission bundles; `AI_MODEL_SERVICE` is the role the Python worker logs in
as. Two `PasswordEncoder` beans exist — `passwordEncoder` (`@Primary`, BCrypt 12) for user
passwords and `tokenEncoder` (BCrypt 4) for hashing refresh/verification tokens.

**AI diagnosis pipeline** — the most distributed flow here, asynchronous end to end:

1. `AIDiagnosisClientService` / `PetController` creates a `Submission` (`PENDING`) after a rate check
   against `inference-service.interval.seconds`, then `FileUploadService` publishes a `*UploadEvent`
   that an `ImageUploader` consumes (`AppConfig`: `@EnableAsync` + `taskExecutor`).
2. Quartz job `jobs/PushAIInferenceTasks` (every 20s in dev) pulls a batch, resolves an image URL via
   `ImgUtils.getImgUrlForAIWorker`, and `CeleryTaskSender` hand-builds **Celery protocol messages**
   and `RPUSH`es them onto the Redis list `celery`; submissions move to `PROCESSING` and the
   serialized message is stored on the row (`celeryTaskString`) so it can be removed later.
3. The external Python worker calls back as the `AI_MODEL_SERVICE` user:
   `PATCH /api/submissions/{id}/eye|urine|status` (`can_update_health_records`). Completion is only
   accepted from `PROCESSING`; `COMPLETED`/`DELETED` are terminal.
4. `jobs/RetryAIInferenceTasks` (every minute) re-queues `PROCESSING` rows older than
   `inference-service.timeout.seconds` up to `retry.max-attempts`, else marks `FAILED`/`TIMEOUT`;
   `jobs/RemoveServiceFailureAIInferenceTasks` (every 10 min) soft-deletes `SERVICE_ERROR`/`TIMEOUT`
   failures to `DELETED`. All three jobs are `@DisallowConcurrentExecution`; the JDBC job store is
   clustered.

Talk to the queue through the `DiagnosisTaskRequestClient` interface, not `CeleryTaskSender`
directly — the task-name constants and the Celery envelope format are an external contract with the
Python project.

**Errors are coded, not free-text.** `validation/ErrorCode` is the single enum of
`CODE("한국어 메시지")` pairs; `GlobalExceptionHandler` (`@RestControllerAdvice`) converts every
exception into `dto/ErrorMessage(List<String> codes, List<String> messages)`. Custom exceptions
(`exceptions/*`) all carry an `ErrorCode`. Bean-validation failures take an extra hop:
`ValidErrorCodeFinder` switches on the DTO's simple class name and delegates to a per-DTO
`*ErrorCode` component that maps **field name → ErrorCode**. Adding a validated DTO therefore means
adding both a `<Dto>ErrorCode` component and a `case` in `ValidErrorCodeFinder`.
`JWTTokenFilter` runs before the advice, so it serializes `ErrorMessage` itself.

**Other integration points:** `WebClient` (WebFlux) for Kakao OAuth; AWS SDK v2 S3 + CloudFront
for images; `StaticResourceConfig` serves
`/uploads/**` from `local-storage.local-resource-path`; Swagger UI at `/swagger-ui/index.html` with a
bearer-JWT scheme. Timestamps are stored and handled as UTC `Instant` (migrations V6/V7 converted
them); `utils/DateUtils` handles timezone-aware week boundaries.

## Conventions

- **Tests come in pairs:** `XxxUT.java` = pure unit test (`@ExtendWith(MockitoExtension.class)`,
  `@Mock`/`@InjectMocks`); `XxxTest.java` = integration test (`@SpringBootTest`,
  `@ActiveProfiles("test")`, `@MockitoBean` for externals). Match the existing pair when adding
  coverage for a class.
- Lombok `@RequiredArgsConstructor` + `private final` constructor injection in new code (older
  classes still use `@Autowired` fields).
- Javadoc on service methods is Korean and documents `@throws` for the `ErrorCode`-carrying
  exceptions; inline comments are Korean too.
- Hide an external dependency behind an interface with profile-selected implementations
  (`ImgUtils`, `ImageUploader`, `EmailService`, `DiagnosisTaskRequestClient`) rather than branching
  on the active profile inside business code.

## Git workflow (from CONTRIBUTING.md)

GitFlow: `develop` is the integration branch, `main` is deploy-only — never commit or merge directly
to `main` (a push to `main` triggers `.github/workflows/backend-cicd.yml`, which builds and
SSH-deploys to the VPS). Branch names are `type/CODE-ID-description` (e.g. `feat/AUTH-03-회원가입`),
lowercase with hyphens, spec ID required. Commit subjects use capitalized types: `Start`, `Feat`,
`Fix`, `Design`, `Refactor`, `Settings`, `Comment`, `Dependency/Plugin`, `Docs`, `Merge`, `Deploy`,
`Rename`, `Remove`, `Revert` — e.g. `Feat: 사용자 프로필 페이지 추가`. PRs need ≥1 approval and
passing tests.
