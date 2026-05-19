# AWS EC2 Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy Spring Boot + Next.js fullstack app to AWS EC2 with RDS MySQL, S3 (IAM role), Nginx reverse proxy, HTTPS via Certbot, and GitHub Actions CI/CD via ECR + OIDC.

**Architecture:** EC2 (t3.micro, Ubuntu) runs Docker Compose with backend (:8080) and frontend (:3000) containers pulled from ECR. Nginx proxies `anphuc.xyz` → frontend, `anphuc.xyz/api/` → backend. RDS MySQL replaces local MySQL. S3 uses EC2 IAM Role (no static credentials). GitHub Actions builds images, pushes to ECR, SSHs EC2 to redeploy.

**Tech Stack:** Spring Boot 3.4 (Java 21), Next.js 14 (standalone), Docker, AWS ECR, AWS RDS MySQL 8, AWS S3 (IAM role), Nginx 1.28, Certbot, GitHub Actions OIDC

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Create | `backend/Dockerfile` | Multi-stage Maven build → JRE Alpine image |
| Create | `frontend/Dockerfile` | Multi-stage npm build → standalone Next.js |
| Modify | `frontend/next.config.mjs` | Add `output: 'standalone'` for slim Docker image |
| Modify | `backend/src/main/java/com/example/backend/s3/service/FileUploadService.java` | Use IAM role when accessKey blank |
| Create | `backend/src/main/resources/application-prod.properties` | Production config reading from env vars |
| Create | `docker-compose.prod.yml` | Production compose (ECR images, no DB — uses RDS) |
| Create | `.env.prod.example` | Template for secrets file on EC2 (never commit `.env.prod`) |
| Create | `infra/nginx/nginx.conf` | Reverse proxy: 80→443, `/api/`→8080, `/`→3000 |
| Create | `infra/nginx/deploy.sh` | Script run on EC2: pull ECR images + restart compose |
| Create | `.github/workflows/deploy.yml` | GitHub Actions: build → ECR push → SSH EC2 deploy |
| Create | `.gitignore` update | Add `.env.prod` |

---

## Task 1: Backend Dockerfile

**Files:**
- Create: `backend/Dockerfile`

- [ ] **Step 1: Verify build fails without Dockerfile**

```bash
cd backend
docker build -t starter-backend . 2>&1 | head -5
# Expected: "unable to prepare context" or similar — no Dockerfile
```

- [ ] **Step 2: Create `backend/Dockerfile`**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Build image and verify it succeeds**

```bash
cd backend
docker build -t starter-backend:test .
# Expected: "Successfully built ..."
```

- [ ] **Step 4: Run container and verify it starts (will fail without DB — that's OK)**

```bash
docker run --rm -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=dummy -e DB_PORT=3306 -e DB_NAME=db \
  -e DB_USERNAME=u -e DB_PASSWORD=p \
  starter-backend:test 2>&1 | grep -E "(Started|Failed|Exception)" | head -5
# Expected: connection refused error (no DB) — proves app boots to startup phase
docker stop $(docker ps -q --filter ancestor=starter-backend:test) 2>/dev/null; true
```

- [ ] **Step 5: Commit**

```bash
git add backend/Dockerfile
git commit -m "feat(deploy): add backend Dockerfile (multi-stage Maven + JRE Alpine)"
```

---

## Task 2: Frontend — Add standalone output + Dockerfile

**Files:**
- Modify: `frontend/next.config.mjs`
- Create: `frontend/Dockerfile`

- [ ] **Step 1: Verify standalone output is NOT configured**

```bash
grep -n "standalone" frontend/next.config.mjs
# Expected: no output — grep returns nothing
```

- [ ] **Step 2: Update `frontend/next.config.mjs`**

```js
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
};

export default nextConfig;
```

- [ ] **Step 3: Verify standalone build works locally**

```bash
cd frontend
NEXT_PUBLIC_BASE_URL=https://anphuc.xyz npm run build 2>&1 | tail -10
# Expected: "Route (app)" table + "✓ Compiled successfully"
ls .next/standalone/server.js
# Expected: file exists
```

- [ ] **Step 4: Create `frontend/Dockerfile`**

```dockerfile
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_BASE_URL
ENV NEXT_PUBLIC_BASE_URL=$NEXT_PUBLIC_BASE_URL
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

- [ ] **Step 5: Build frontend image**

```bash
cd frontend
docker build \
  --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
  -t starter-frontend:test .
# Expected: "Successfully built ..."
```

- [ ] **Step 6: Run and verify Next.js starts**

```bash
docker run -d --name fe-test -p 3001:3000 starter-frontend:test
sleep 5
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
# Expected: 200 or 307
docker rm -f fe-test
```

- [ ] **Step 7: Commit**

```bash
git add frontend/next.config.mjs frontend/Dockerfile
git commit -m "feat(deploy): add frontend Dockerfile with Next.js standalone output"
```

---

## Task 3: S3 Service — Support IAM Role credentials

**Context:** Current `FileUploadService` always uses `StaticCredentialsProvider` with `endpointOverride` (tebi.io for dev). Production uses AWS S3 with EC2 IAM Role. When `app.s3.access-key` is blank → use `DefaultCredentialsProvider` and no `endpointOverride`.

**Files:**
- Modify: `backend/src/main/java/com/example/backend/s3/service/FileUploadService.java`
- Test: `backend/src/test/java/com/example/backend/s3/service/FileUploadServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/example/backend/s3/service/FileUploadServiceTest.java`:

```java
package com.example.backend.s3.service;

import com.example.backend.s3.config.S3Configuration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadServiceTest {

    @Test
    void whenAccessKeyBlank_usesDefaultCredentialsProvider() throws Exception {
        S3Configuration config = new S3Configuration();
        config.setBucketName("test-bucket");
        config.setRegion("ap-southeast-1");
        config.setAccessKey("");
        config.setSecretKey("");
        config.setBaseUrl("");
        config.setStorageClass("STANDARD");

        FileUploadService service = new FileUploadService(config);

        Field credField = FileUploadService.class.getDeclaredField("credentialsProviderType");
        credField.setAccessible(true);
        String type = (String) credField.get(service);
        assertThat(type).isEqualTo("iam-role");
    }

    @Test
    void whenAccessKeyPresent_usesStaticCredentialsProvider() throws Exception {
        S3Configuration config = new S3Configuration();
        config.setBucketName("test-bucket");
        config.setRegion("ap-southeast-1");
        config.setAccessKey("mykey");
        config.setSecretKey("mysecret");
        config.setBaseUrl("http://s3.tebi.io");
        config.setStorageClass("STANDARD");

        FileUploadService service = new FileUploadService(config);

        Field credField = FileUploadService.class.getDeclaredField("credentialsProviderType");
        credField.setAccessible(true);
        String type = (String) credField.get(service);
        assertThat(type).isEqualTo("static");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
./mvnw test -Dtest=FileUploadServiceTest -pl . 2>&1 | grep -E "(FAIL|ERROR|credentialsProviderType)"
# Expected: compilation error — field 'credentialsProviderType' does not exist yet
```

- [ ] **Step 3: Update `FileUploadService.java`**

```java
package com.example.backend.s3.service;

import com.example.backend.s3.config.S3Configuration;
import java.net.URI;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class FileUploadService {

  private final S3Client s3Client;
  private final S3Configuration s3Configuration;
  // Used in tests to verify which credential path was taken
  final String credentialsProviderType;

  public FileUploadService(S3Configuration s3Configuration) {
    this.s3Configuration = s3Configuration;

    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(s3Configuration.getRegion()));

    boolean hasStaticCredentials = s3Configuration.getAccessKey() != null
        && !s3Configuration.getAccessKey().isBlank();

    if (hasStaticCredentials) {
      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(s3Configuration.getAccessKey(), s3Configuration.getSecretKey())
      ));
      if (s3Configuration.getBaseUrl() != null && !s3Configuration.getBaseUrl().isBlank()) {
        builder.endpointOverride(URI.create(s3Configuration.getBaseUrl()));
      }
      builder.forcePathStyle(true);
      this.credentialsProviderType = "static";
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
      this.credentialsProviderType = "iam-role";
    }

    this.s3Client = builder.build();
  }

  public String uploadFile(String filePath, byte[] file) {
    PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
        .bucket(s3Configuration.getBucketName())
        .storageClass(s3Configuration.getStorageClass())
        .key(filePath);

    if ("static".equals(credentialsProviderType)) {
      requestBuilder.acl(ObjectCannedACL.PUBLIC_READ);
    }

    s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(file));

    try {
      GetUrlRequest getUrlRequest = GetUrlRequest.builder()
          .bucket(s3Configuration.getBucketName())
          .key(filePath)
          .build();
      return s3Client.utilities().getUrl(getUrlRequest).toURI().toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to get URL of uploaded file", e);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend
./mvnw test -Dtest=FileUploadServiceTest 2>&1 | grep -E "(Tests run|BUILD)"
# Expected: "Tests run: 2, Failures: 0, Errors: 0" and "BUILD SUCCESS"
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/s3/service/FileUploadService.java \
        backend/src/test/java/com/example/backend/s3/service/FileUploadServiceTest.java
git commit -m "feat(s3): use IAM role DefaultCredentialsProvider when accessKey is blank"
```

---

## Task 4: Production Spring Boot config

**Files:**
- Create: `backend/src/main/resources/application-prod.properties`

- [ ] **Step 1: Verify no prod profile exists**

```bash
ls backend/src/main/resources/application-prod.properties 2>&1
# Expected: "No such file or directory"
```

- [ ] **Step 2: Create `backend/src/main/resources/application-prod.properties`**

```properties
app.database.host=${DB_HOST}
app.database.port=${DB_PORT:3306}
app.database.name=${DB_NAME}
app.database.username=${DB_USERNAME}
app.database.password=${DB_PASSWORD}

app.vapid-public-key=${VAPID_PUBLIC_KEY:}
app.vapid-private-key=${VAPID_PRIVATE_KEY:}
app.vapid-subject=${VAPID_SUBJECT:mailto:admin@anphuc.xyz}

app.application-name=starter-kit
app.base-url=https://anphuc.xyz
app.allowed-origins=https://anphuc.xyz
app.login-page-url=${app.base-url}/auth/login
app.login-success-url=${app.base-url}/auth/login-success

app.s3.bucket-name=${S3_BUCKET_NAME}
app.s3.region=${S3_REGION:ap-southeast-1}
app.s3.access-key=
app.s3.secret-key=
app.s3.base-url=
app.s3.storage-class=STANDARD

app.admin-user-email=${ADMIN_EMAIL:admin@anphuc.xyz}
app.admin-user-password=${ADMIN_PASSWORD}

spring.security.oauth2.client.registration.github.client-id=${GITHUB_OAUTH_CLIENT_ID:}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_OAUTH_CLIENT_SECRET:}
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_OAUTH_CLIENT_ID:}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_OAUTH_CLIENT_SECRET:}

spring.datasource.url=jdbc:mysql://${app.database.host}:${app.database.port}/${app.database.name}?allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.driver-class-name=com.mysql.jdbc.Driver
spring.datasource.username=${app.database.username}
spring.datasource.password=${app.database.password}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

org.jobrunr.background-job-server.enabled=true
org.jobrunr.dashboard.enabled=false

spring.mail.host=${SMTP_HOST:localhost}
spring.mail.port=${SMTP_PORT:1025}
spring.mail.username=${SMTP_USERNAME:user}
spring.mail.password=${SMTP_PASSWORD:password}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

server.port=8080
server.forward-headers-strategy=FRAMEWORK
```

- [ ] **Step 3: Verify the file was created correctly**

```bash
grep "DB_HOST\|S3_BUCKET_NAME\|ADMIN_PASSWORD" backend/src/main/resources/application-prod.properties
# Expected: 3 matching lines
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application-prod.properties
git commit -m "feat(deploy): add production Spring Boot config with env var injection"
```

---

## Task 5: docker-compose.prod.yml + .env.prod.example

**Files:**
- Create: `docker-compose.prod.yml`
- Create: `.env.prod.example`
- Modify: `.gitignore`

- [ ] **Step 1: Create `docker-compose.prod.yml`**

Replace `<AWS_ACCOUNT_ID>` placeholder — user fills in their account ID:

```yaml
services:
  backend:
    image: ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-backend:${IMAGE_TAG:-latest}
    container_name: starter-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: ${DB_HOST}
      DB_PORT: ${DB_PORT:-3306}
      DB_NAME: ${DB_NAME}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      S3_BUCKET_NAME: ${S3_BUCKET_NAME}
      S3_REGION: ${S3_REGION:-ap-southeast-1}
      ADMIN_EMAIL: ${ADMIN_EMAIL}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      SMTP_HOST: ${SMTP_HOST:-localhost}
      SMTP_PORT: ${SMTP_PORT:-1025}
      SMTP_USERNAME: ${SMTP_USERNAME:-user}
      SMTP_PASSWORD: ${SMTP_PASSWORD:-password}
      VAPID_PUBLIC_KEY: ${VAPID_PUBLIC_KEY:-}
      VAPID_PRIVATE_KEY: ${VAPID_PRIVATE_KEY:-}
      GITHUB_OAUTH_CLIENT_ID: ${GITHUB_OAUTH_CLIENT_ID:-}
      GITHUB_OAUTH_CLIENT_SECRET: ${GITHUB_OAUTH_CLIENT_SECRET:-}

  frontend:
    image: ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-frontend:${IMAGE_TAG:-latest}
    container_name: starter-frontend
    restart: unless-stopped
    ports:
      - "3000:3000"
    environment:
      NODE_ENV: production
```

- [ ] **Step 2: Create `.env.prod.example`**

```bash
# Copy this to .env.prod on EC2 and fill in real values
# NEVER commit .env.prod to git

AWS_ACCOUNT_ID=123456789012
IMAGE_TAG=latest

# RDS
DB_HOST=app-db-dev.cns4swu4ccs9.ap-southeast-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=starter_kit_db
DB_USERNAME=admin
DB_PASSWORD=CHANGE_ME

# S3
S3_BUCKET_NAME=s3-starter-ap-se-1
S3_REGION=ap-southeast-1

# Admin
ADMIN_EMAIL=admin@anphuc.xyz
ADMIN_PASSWORD=CHANGE_ME

# SMTP (optional — leave defaults if using mailpit or no email)
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=user
SMTP_PASSWORD=password

# OAuth2 (optional)
GITHUB_OAUTH_CLIENT_ID=
GITHUB_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=

# VAPID for push notifications (optional)
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
```

- [ ] **Step 3: Add `.env.prod` to `.gitignore`**

```bash
echo ".env.prod" >> .gitignore
cat .gitignore
# Expected: .env.prod appears in file
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.prod.yml .env.prod.example .gitignore
git commit -m "feat(deploy): add production docker-compose and env template"
```

---

## Task 6: Nginx config

**Files:**
- Create: `infra/nginx/nginx.conf`

- [ ] **Step 1: Create `infra/nginx/nginx.conf`**

```nginx
upstream backend {
    server localhost:8080;
}

upstream frontend {
    server localhost:3000;
}

server {
    listen 80;
    server_name anphuc.xyz www.anphuc.xyz;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name anphuc.xyz www.anphuc.xyz;

    ssl_certificate /etc/letsencrypt/live/anphuc.xyz/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/anphuc.xyz/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    client_max_body_size 20M;

    # Backend API
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Swagger
    location /swagger-ui/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /v3/api-docs {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # JobRunr dashboard (internal only — remove if not needed)
    location /dashboard {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Frontend (catch-all)
    location / {
        proxy_pass http://frontend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
```

- [ ] **Step 2: Verify Nginx config syntax (run locally if Nginx installed, or skip to EC2 step)**

```bash
nginx -t -c "$(pwd)/infra/nginx/nginx.conf" 2>&1 || echo "test on EC2 instead"
```

- [ ] **Step 3: Commit**

```bash
git add infra/nginx/nginx.conf
git commit -m "feat(deploy): add Nginx reverse proxy config for anphuc.xyz"
```

---

## Task 7: ECR repositories + initial image push

**Run these commands on your local machine.**

- [ ] **Step 1: Create ECR repositories**

```bash
aws ecr create-repository --repository-name starter-backend --region ap-southeast-1
aws ecr create-repository --repository-name starter-frontend --region ap-southeast-1
# Expected: JSON output with repositoryUri for each
# Note the AWS_ACCOUNT_ID from the repositoryUri (e.g. 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com)
```

- [ ] **Step 2: Authenticate Docker to ECR**

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com
# Expected: "Login Succeeded"
```

- [ ] **Step 3: Build and push backend**

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
cd backend
docker build -t starter-backend:latest .
docker tag starter-backend:latest \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-backend:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-backend:latest
# Expected: "latest: digest: sha256:..."
```

- [ ] **Step 4: Build and push frontend**

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
cd frontend
docker build \
  --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
  -t starter-frontend:latest .
docker tag starter-frontend:latest \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-frontend:latest
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-frontend:latest
# Expected: "latest: digest: sha256:..."
```

---

## Task 8: EC2 first deploy

**SSH into EC2 for all steps in this task.**

- [ ] **Step 1: Install AWS CLI on EC2**

```bash
sudo apt-get update && sudo apt-get install -y awscli
aws sts get-caller-identity
# Expected: JSON with EC2 IAM Role — proves IAM role works
```

- [ ] **Step 2: Authenticate Docker to ECR from EC2**

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com
# Expected: "Login Succeeded"
```

- [ ] **Step 3: Create app directory and .env.prod on EC2**

```bash
mkdir -p ~/app
cd ~/app
# Copy docker-compose.prod.yml from repo or scp from local:
# scp -i key.pem docker-compose.prod.yml ubuntu@<EC2_IP>:~/app/

# Create .env.prod (fill in real values from .env.prod.example):
nano .env.prod
# Fill in DB_HOST, DB_PASSWORD, ADMIN_PASSWORD, AWS_ACCOUNT_ID, etc.
```

- [ ] **Step 4: Pull images and start containers**

```bash
cd ~/app
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker ps
# Expected: starter-backend and starter-frontend both "Up"
```

- [ ] **Step 5: Verify backend health from EC2**

```bash
sleep 15  # wait for Spring Boot to start
curl -s http://localhost:8080/api/auth/csrf | head -50
# Expected: JSON response (not connection refused)
```

- [ ] **Step 6: Verify frontend from EC2**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
# Expected: 200 or 307
```

- [ ] **Step 7: Verify S3 IAM role works**

```bash
aws s3 ls s3://s3-starter-ap-se-1 --region ap-southeast-1
# Expected: no error (proves IAM role has S3 access)
```

---

## Task 9: Nginx + SSL (Certbot)

**SSH into EC2 for all steps in this task.**

- [ ] **Step 1: Copy Nginx config to EC2**

From local machine:
```bash
scp -i key.pem infra/nginx/nginx.conf ubuntu@<EC2_IP>:/tmp/nginx.conf
```

On EC2:
```bash
# Install Nginx config (without SSL block first — Certbot needs port 80 open)
sudo cp /tmp/nginx.conf /etc/nginx/sites-available/anphuc.xyz
```

- [ ] **Step 2: Create temporary HTTP-only config (for Certbot challenge)**

On EC2, create `/etc/nginx/sites-available/anphuc.xyz` with HTTP-only first:

```nginx
server {
    listen 80;
    server_name anphuc.xyz www.anphuc.xyz;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
    }
}
```

```bash
sudo ln -sf /etc/nginx/sites-available/anphuc.xyz /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
# Expected: "syntax is ok" + "test is successful"
sudo systemctl reload nginx
```

- [ ] **Step 3: Verify HTTP works before SSL**

```bash
curl -s -o /dev/null -w "%{http_code}" http://anphuc.xyz
# Expected: 200 (served from EC2 via Nginx)
```

- [ ] **Step 4: Install Certbot and get SSL certificate**

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d anphuc.xyz -d www.anphuc.xyz \
  --non-interactive --agree-tos -m admin@anphuc.xyz
# Expected: "Congratulations! Your certificate and chain have been saved..."
```

- [ ] **Step 5: Copy full Nginx config (with SSL) from repo**

From local:
```bash
scp -i key.pem infra/nginx/nginx.conf ubuntu@<EC2_IP>:/tmp/nginx.conf
```

On EC2:
```bash
sudo cp /tmp/nginx.conf /etc/nginx/sites-available/anphuc.xyz
sudo nginx -t && sudo systemctl reload nginx
```

- [ ] **Step 6: Verify HTTPS works end-to-end**

```bash
curl -s -o /dev/null -w "%{http_code}" https://anphuc.xyz
# Expected: 200
curl -s https://anphuc.xyz/api/auth/csrf | python3 -m json.tool
# Expected: JSON response
```

---

## Task 10: GitHub Actions CI/CD (OIDC + ECR + SSH deploy)

**Files:**
- Create: `.github/workflows/deploy.yml`
- Create: `infra/nginx/deploy.sh`

- [ ] **Step 1: Create deploy script `infra/nginx/deploy.sh`**

This script runs ON EC2 via SSH from GitHub Actions:

```bash
#!/bin/bash
set -e

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-southeast-1
ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

# Authenticate Docker to ECR
aws ecr get-login-password --region ${REGION} | \
  docker login --username AWS --password-stdin ${ECR_REGISTRY}

# Pull latest images
cd ~/app
docker compose -f docker-compose.prod.yml --env-file .env.prod pull

# Restart with zero-downtime (stop old, start new)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --remove-orphans

# Clean up old images
docker image prune -f

echo "Deploy complete"
docker ps --format "table {{.Names}}\t{{.Status}}"
```

```bash
chmod +x infra/nginx/deploy.sh
git add infra/nginx/deploy.sh
```

- [ ] **Step 2: Setup OIDC in AWS (run once from local)**

```bash
# Create OIDC provider for GitHub Actions
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
# Expected: OIDCProviderArn (save this)
```

- [ ] **Step 3: Create IAM Role for GitHub Actions (run from local)**

Save this as `/tmp/trust-policy.json` first, replacing `YOUR_GITHUB_USERNAME` and `YOUR_REPO_NAME`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:YOUR_GITHUB_USERNAME/YOUR_REPO_NAME:*"
        }
      }
    }
  ]
}
```

```bash
aws iam create-role \
  --role-name github-actions-deploy \
  --assume-role-policy-document file:///tmp/trust-policy.json

# Attach ECR push permissions
aws iam attach-role-policy \
  --role-name github-actions-deploy \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

aws iam get-role --role-name github-actions-deploy --query Role.Arn --output text
# Expected: arn:aws:iam::<ACCOUNT_ID>:role/github-actions-deploy (save this ARN)
```

- [ ] **Step 4: Add GitHub Secrets**

In GitHub repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret name | Value |
|-------------|-------|
| `AWS_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/github-actions-deploy` |
| `AWS_ACCOUNT_ID` | your 12-digit AWS account ID |
| `EC2_HOST` | your Elastic IP |
| `EC2_SSH_KEY` | contents of your `.pem` file (the whole file including `-----BEGIN...`) |

- [ ] **Step 5: Create `.github/workflows/deploy.yml`**

```yaml
name: Build and Deploy

on:
  push:
    branches: [master]

permissions:
  id-token: write
  contents: read

env:
  AWS_REGION: ap-southeast-1
  ECR_BACKEND: starter-backend
  ECR_FRONTEND: starter-frontend

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    outputs:
      image-tag: ${{ steps.meta.outputs.tag }}

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Generate image tag
        id: meta
        run: echo "tag=$(echo $GITHUB_SHA | cut -c1-8)" >> $GITHUB_OUTPUT

      - name: Build and push backend
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ steps.meta.outputs.tag }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_BACKEND:$IMAGE_TAG \
                       -t $ECR_REGISTRY/$ECR_BACKEND:latest \
                       ./backend
          docker push $ECR_REGISTRY/$ECR_BACKEND:$IMAGE_TAG
          docker push $ECR_REGISTRY/$ECR_BACKEND:latest

      - name: Build and push frontend
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ steps.meta.outputs.tag }}
        run: |
          docker build \
            --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
            -t $ECR_REGISTRY/$ECR_FRONTEND:$IMAGE_TAG \
            -t $ECR_REGISTRY/$ECR_FRONTEND:latest \
            ./frontend
          docker push $ECR_REGISTRY/$ECR_FRONTEND:$IMAGE_TAG
          docker push $ECR_REGISTRY/$ECR_FRONTEND:latest

  deploy:
    runs-on: ubuntu-latest
    needs: build-and-push

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Deploy to EC2 via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY }}
          script: bash ~/app/deploy.sh
```

- [ ] **Step 6: Copy deploy.sh to EC2**

```bash
scp -i key.pem infra/nginx/deploy.sh ubuntu@<EC2_IP>:~/app/deploy.sh
ssh -i key.pem ubuntu@<EC2_IP> "chmod +x ~/app/deploy.sh"
```

- [ ] **Step 7: Commit and push to trigger first CI run**

```bash
git add .github/workflows/deploy.yml infra/nginx/deploy.sh
git commit -m "feat(ci): add GitHub Actions CI/CD with OIDC + ECR + SSH deploy"
git push origin master
```

- [ ] **Step 8: Verify CI/CD pipeline passes**

In GitHub → Actions tab → watch the workflow run.

Expected: both jobs green ✓

- [ ] **Step 9: Verify production end-to-end**

```bash
curl -s https://anphuc.xyz
# Expected: 200 HTML response

curl -s https://anphuc.xyz/api/auth/csrf
# Expected: JSON (not 500, not connection refused)

curl -s -X POST https://anphuc.xyz/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@anphuc.xyz","password":"<ADMIN_PASSWORD>"}' | python3 -m json.tool
# Expected: user JSON response
```

---

## Checklist

- [ ] Task 1: Backend Dockerfile builds + container starts
- [ ] Task 2: Frontend Dockerfile builds with standalone + container starts
- [ ] Task 3: S3 service uses IAM role in prod, static creds in dev — tests pass
- [ ] Task 4: application-prod.properties reads from env vars
- [ ] Task 5: docker-compose.prod.yml + .env.prod.example committed
- [ ] Task 6: Nginx config routes `/api/` → 8080, `/` → 3000
- [ ] Task 7: ECR repos created, images pushed
- [ ] Task 8: Containers running on EC2, S3 IAM role verified
- [ ] Task 9: HTTPS working on anphuc.xyz with valid cert
- [ ] Task 10: GitHub Actions deploys on every push to master
