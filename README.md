# 똑똑하개 건강하개 벡엔드

## Database 셋업 및 실행 (Docker 사용)
현재 프로젝트는 PostgreSQL을 사용합니다. 기본적으로 Docker를 사용하여 데이터베이스를 실행하는 방법을 안내합니다.

### Docker 설치
Docker가 설치되어 있지 않다면, [Docker 공식 사이트](https://www.docker.com/get-started)에서 설치 방법을 참고하여 설치합니다.

### 환경변수 설정
#### MacOS / Linux
터미널에서 다음 명령어를 사용하여 환경변수를 설정합니다. (예시 값은 필요에 따라 변경하세요)

```bash
export DB_USERNAME=postgres # 데이터베이스 사용자 이름
export DB_PASSWORD=your_password # 원하는 비밀번호로 변경
export DB_HOST=localhost # 데이터베이스 호스트
export DB_PORT=5432 # 데이터베이스 포트
export DB_NAME=smart_health_dog # 데이터베이스 이름
export PG_DATABASE_PATH=~/database # 데이터베이스 데이터가 저장될 경로
export JWT_SECRET=your_jwt_secret # JWT 비밀 키. https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
export EMAIL_VERIFICATION_SECRET=your_email_verification_secret # 이메일 인증 비밀 키. https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
```

#### Windows (PowerShell)
PowerShell에서 다음 명령어를 사용하여 환경변수를 설정합니다. (예시 값은 필요에 따라 변경하세요)

```powershell
[System.Environment]::SetEnvironmentVariable('DB_HOST', 'localhost', 'User')
[System.Environment]::SetEnvironmentVariable('DB_PORT', '5432', 'User')
[System.Environment]::SetEnvironmentVariable('DB_NAME', 'smart_health_dog', 'User')
[System.Environment]::SetEnvironmentVariable('DB_USERNAME', 'postgres', 'User') # 데이터베이스 사용자 이름
[System.Environment]::SetEnvironmentVariable('DB_PASSWORD', 'your_password', 'User') # 원하는 비밀번호로 변경
[System.Environment]::SetEnvironmentVariable('PG_DATABASE_PATH', "$env:USERPROFILE\database", 'User')
[System.Environment]::SetEnvironmentVariable('JWT_SECRET', 'your_jwt_secret', 'User') # JWT 비밀 키로 변경. https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
[System.Environment]::SetEnvironmentVariable('EMAIL_VERIFICATION_SECRET', 'your_email_verification_secret', 'User') # 이메일 인증 비밀 키로 변경. https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
```

그 후, 컴퓨터를 재시작하여 환경변수가 적용되도록 합니다.

### Docker Compose로 PostgreSQL 실행
환경변수를 설정한 후, 프로젝트 루트 디렉토리에서 다음 명령어를 실행하여 PostgreSQL 컨테이너를 시작합니다.

```bash
docker-compose up -d
```

## 로컬 스토리지 설정
프로젝트에서 사용하는 로컬 스토리지는 이미지 파일을 저장하는 데 사용됩니다. 로컬 스토리지 설정에 필요한 환경변수 값을 다음과 같이 설정합니다.

### 환경변수 설정
#### MacOS / Linux
```bash
export LOCAL_STORAGE_URL_PREFIX="http://localhost:8080"
export AI_MODEL_SERVICE_URL_PREFIX="http://localhost:8080"
export LOCAL_RESOURCE_PATH="file:uploads/"
```

#### Windows (PowerShell)
```powershell
[System.Environment]::SetEnvironmentVariable('LOCAL_STORAGE_URL_PREFIX', 'http://localhost:8080', 'User')
[System.Environment]::SetEnvironmentVariable('AI_MODEL_SERVICE_URL_PREFIX', 'http://localhost:8080', 'User')
[System.Environment]::SetEnvironmentVariable('LOCAL_RESOURCE_PATH', 'file:uploads/', 'User')
```

## AWS 클라우드 설정
프로젝트에서 사용하는 AWS 서비스는 **S3(Simple Storage Service)**, **SES(Simple Email Service)**, **CloudFront**입니다. 각 서비스에 대한 설정 방법은 다음과 같습니다.

### AWS S3 설정
이미지를 클라우드에 업로드하기 위해 AWS S3 버킷 설정이 필요합니다. AWS S3 설정에 필요한 환경변수 값을 이 프로젝트 관리자에게 문의한 다음, 다음과 같이 설정합니다.

#### 환경변수 설정
##### MacOS / Linux
```bash
export AWS_ACCESS_KEY=[AWS 액세스 키]
export AWS_SECRET_KEY=[AWS 비밀 키]
export AWS_REGION=[AWS 리전]
export AWS_S3_BUCKET=[S3 버킷 이름]
```

##### Windows (PowerShell)
```powershell
[System.Environment]::SetEnvironmentVariable('AWS_ACCESS_KEY', '[AWS 액세스 키]', 'User')
[System.Environment]::SetEnvironmentVariable('AWS_SECRET_KEY', '[AWS 비밀 키]', 'User')
[System.Environment]::SetEnvironmentVariable('AWS_REGION', '[AWS 리전]', 'User')
[System.Environment]::SetEnvironmentVariable('AWS_S3_BUCKET', '[S3 버킷 이름]', 'User')
```

### 이메일 전송 서비스 설정
프로젝트에서 사용하는 SMTP 서비스는 AWS SES(Simple Email Service)입니다. 이메일 전송을 위해 AWS SES 설정이 필요하며, 환경변수로 설정할 수 있습니다. AWS SES 설정에 필요한 환경변수 값을 이 프로젝트 관리자에게 문의한 다음, 다음과 같이 설정합니다.

#### 환경변수 설정
##### MacOS / Linux
```bash
export SMTP_HOST=[SMTP 서버 호스트]
export SMTP_USER=[SMTP 사용자 이름]
export SMTP_PASSWORD=[SMTP 비밀번호]
export SMTP_FROM=[보내는 사람 이메일 주소]
export ALLOWED_EMAILS_COMMA_SEPARATED=[허용된 이메일 주소들 (콤마로 구분)]
```

##### Windows (PowerShell)
```powershell
[System.Environment]::SetEnvironmentVariable('SMTP_HOST', '[SMTP 서버 호스트]', 'User')
[System.Environment]::SetEnvironmentVariable('SMTP_USER', '[SMTP 사용자 이름]', 'User')
[System.Environment]::SetEnvironmentVariable('SMTP_PASSWORD', '[SMTP 비밀번호]', 'User')
[System.Environment]::SetEnvironmentVariable('SMTP_FROM', '[보내는 사람 이메일 주소]', 'User')
[System.Environment]::SetEnvironmentVariable('ALLOWED_EMAILS_COMMA_SEPARATED', '[허용된 이메일 주소들 (콤마로 구분)]', 'User')
```

### AWS CloudFront 설정
AWS CloudFront는 S3 버킷에 업로드된 이미지를 빠르게 제공하기 위해 사용됩니다. AWS CloudFront 설정에 필요한 환경변수 값을 이 프로젝트 관리자에게 문의한 다음, 다음과 같이 설정합니다.

#### 환경변수 설정
##### MacOS / Linux
```bash
export AWS_CLOUDFRONT_DOMAIN=[CloudFront 도메인 이름]
```

##### Windows (PowerShell)
```powershell
[System.Environment]::SetEnvironmentVariable('AWS_CLOUDFRONT_DOMAIN', '[CloudFront 도메인 이름]', 'User')
```

윈도우즈 사용자는 컴퓨터를 재시작하여 환경변수가 적용되도록 합니다.

## AI 서비스 설정
로컬 환경에서 AI 기반 진단 서비스를 사용하려면, AI 서비스가 실행 중이어야 합니다. AI 서비스는 별도의 프로젝트로 관리되며, 해당 프로젝트의 README.md 파일을 참고하여 AI 서비스를 실행합니다. 서비스가 성공적으로 실행되면, 환경변수로 다음과 같이 설정합니다.
#### MacOS / Linux
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=[Redis 비밀번호] # Redis 비밀번호 (필요시 설정)
export AI_MODEL_SERVICE_EMAIL=[아무 이메일 주소]
export AI_MODEL_SERVICE_PASSWORD=[아무 비밀번호]
export AI_MODEL_SERVICE_PASSWORD_HASH=[AI 서비스 비밀번호 해시] # 해시 인증 시크릿 값. https://bcrypt-generator.com/ 에서 AI_MODEL_SERVICE_PASSWORD 값을 해시하여 생성 (12 라운드 필수)
export AI_MODEL_SERVICE_SECRET=[AI 서비스 시크릿 키] # https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
export INFERENCE_SERVICE_RETRY_MAX_ATTEMPTS=[AI 서비스 재시도 최대 횟수]
export INFERENCE_SERVICE_TIMEOUT_SECONDS=[AI 서비스 타임아웃 시간 (초)]
export INFERENCE_SERVICE_BATCH_SIZE=[AI 서비스 배치 사이즈]
export INFERENCE_SERVICE_INTERVAL_SECONDS=[AI 서비스 요청 간격 (초)]
```

#### Windows (PowerShell)
```powershell
[System.Environment]::SetEnvironmentVariable('REDIS_HOST', 'localhost', 'User')
[System.Environment]::SetEnvironmentVariable('REDIS_PORT', '6379', 'User')
[System.Environment]::SetEnvironmentVariable('REDIS_PASSWORD', '[Redis 비밀번호]', 'User') # Redis 비밀번호 (필요시 설정)
[System.Environment]::SetEnvironmentVariable('AI_MODEL_SERVICE_EMAIL', '[아무 이메일 주소]', 'User')
[System.Environment]::SetEnvironmentVariable('AI_MODEL_SERVICE_PASSWORD', '[아무 비밀번호]', 'User')
[System.Environment]::SetEnvironmentVariable('AI_MODEL_SERVICE_PASSWORD_HASH', '[AI 서비스 비밀번호 해시]', 'User') # 해시 인증 시크릿 값. https://bcrypt-generator.com/ 에서 AI_MODEL_SERVICE_PASSWORD 값을 해시하여 생성 (12 라운드 필수)
[System.Environment]::SetEnvironmentVariable('AI_MODEL_SERVICE_SECRET', '[AI 서비스 시크릿 키]', 'User') # https://jwtsecrets.com/#generator 에서 256 비트로 설정 후 생성
[System.Environment]::SetEnvironmentVariable('INFERENCE_SERVICE_RETRY_MAX_ATTEMPTS', '[AI 서비스 재시도 최대 횟수]', 'User')
[System.Environment]::SetEnvironmentVariable('INFERENCE_SERVICE_TIMEOUT_SECONDS', '[AI 서비스 타임아웃 시간 (초)]', 'User')
[System.Environment]::SetEnvironmentVariable('INFERENCE_SERVICE_BATCH_SIZE', '[AI 서비스 배치 사이즈]', 'User')
[System.Environment]::SetEnvironmentVariable('INFERENCE_SERVICE_INTERVAL_SECONDS', '[AI 서비스 요청 간격 (초)]', 'User')
```

## 빌드 및 서버 실행

프로젝트에서 사용하는 자바 버전은 24입니다. 자바 24가 설치되어 있는지 확인하세요. 설치되어 있지 않다면, [링크](https://www.oracle.com/java/technologies/javase/jdk24-archive-downloads.html)에서 설치할 수 있습니다. 본인의 OS에 맞는 버전을 다운로드하여 설치합니다.

위에 설정이 완료되면, 다음 명령어로 프로젝트를 빌드하고 실행할 수 있습니다.

#### MacOS / Linux
```bash
./gradlew bootRun --args='--spring.profiles.active=dev' 
```

#### Windows (PowerShell)
```powershell
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 테스트 실행
프로젝트의 테스트 코드를 실행하려면 다음 명령어를 사용합니다.
#### MacOS / Linux
```bash
./gradlew test 
```

#### Windows (PowerShell)
```powershell
./gradlew test
```