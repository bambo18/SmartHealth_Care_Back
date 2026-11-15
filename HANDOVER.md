# 프로젝트 인수인계서

> 이 문서는 프로젝트를 이어받는 사람을 위한 안내입니다. 담당자, 배포 정보, 진행 상황은 인수인계 시점에 맞게 갱신하세요.

## 프로젝트 개요

- 프로젝트: 똑똑하개 건강하개 백엔드
- 저장소: `bambo18/SmartHealth_Care_Back`
- 주요 기능: 회원·반려동물 관리, 건강 진단, 산책 기록, 보호소 정보 및 검색 API
- 기술 스택: Java 24, Spring Boot, PostgreSQL, Redis, Gradle

## 개발 환경 시작하기

개발 환경 변수와 PostgreSQL, 로컬 스토리지, AWS 및 AI 서비스 설정은 [README.md](./README.md)를 참고하세요.

Windows PowerShell에서 개발 서버 실행:

```powershell
./gradlew bootRun --args='--spring.profiles.active=dev'
```

테스트 실행:

```powershell
./gradlew test
```

## 주요 문서와 코드 위치

| 항목 | 위치 | 설명 |
|---|---|---|
| 로컬 환경 설정 및 실행 | [README.md](./README.md) | 환경 변수, 의존 서비스, 서버 실행 및 테스트 |
| 기여 및 Git 협업 규칙 | [CONTRIBUTING.md](./CONTRIBUTING.md) | 브랜치, 커밋, PR 규칙 |
| API 컨트롤러 | `src/main/java/com/smarthealthdog/backend/controllers/` | HTTP 엔드포인트 |
| 서비스 로직 | `src/main/java/com/smarthealthdog/backend/services/` | 업무 로직 |
| 데이터 접근 | `src/main/java/com/smarthealthdog/backend/repositories/` | 저장소 계층 |
| DB 마이그레이션 | `src/main/resources/db/migration/` | Flyway 마이그레이션 |
| 테스트 | `src/test/java/com/smarthealthdog/backend/` | 테스트 코드 |

API 명세를 확인할 때는 문서에 정리된 범위를 확인하고, 다른 API는 컨트롤러와 DTO 구현도 함께 살펴보세요.

## 인수인계 시 확인할 항목

- [ ] 인수자 / 인계자:
- [ ] 인수인계 날짜:
- [ ] 현재 배포 환경과 배포 절차:
- [ ] 운영·개발 환경의 접근 권한 전달 위치:
- [ ] 진행 중인 작업 및 관련 이슈/PR:
- [ ] 알려진 문제와 주의할 점:
- [ ] 다음 우선 작업:

## 보안 유의사항

비밀번호, 토큰, 비밀 키, 실제 사용자 데이터는 이 저장소나 문서에 기록하지 마세요. 필요한 접근 정보는 조직에서 승인한 비밀 정보 관리 경로로 별도 전달하고, 인수자에게 필요한 권한만 부여하세요.
