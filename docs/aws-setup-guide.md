# AWS Deployment Setup Guide

> Step-by-step guide to deploy Spring Boot + Next.js on AWS EC2 with RDS, S3, Nginx, HTTPS, and GitHub Actions CI/CD.
> Target: người đã biết Linux cơ bản, chưa quen AWS/Docker.

---

## Prerequisites

- AWS account (free tier)
- Domain name (guide này dùng `anphuc.xyz` qua Pavietnam)
- GitHub account + repo public
- Local machine có `aws` CLI cài và configured

---

## Phase 1 — EC2 Setup

### 1.1 Launch EC2

**WHY:** EC2 là máy chủ chạy app của bạn. t3.micro đủ RAM để chạy Spring Boot + Next.js.

1. AWS Console → EC2 → **Launch Instance**
2. Điền:
   - Name: `starter-kit-server`
   - AMI: `Ubuntu Server 24.04 LTS` (hoặc 26.04 nếu available)
   - Instance type: `t3.micro`
   - Key pair: tạo mới → download file `.pem` → lưu an toàn
   - Security Group: tạo mới tên `ec2-web-sg`, inbound rules:

   | Type | Port | Source | Purpose |
   |------|------|--------|---------|
   | SSH | 22 | My IP | SSH access |
   | HTTP | 80 | 0.0.0.0/0 | Web traffic |
   | HTTPS | 443 | 0.0.0.0/0 | HTTPS traffic |

3. Launch instance

### 1.2 Allocate Elastic IP

**WHY:** IP public của EC2 thay đổi mỗi lần restart. Elastic IP giữ IP cố định — DNS cần điều này.

1. EC2 → **Elastic IPs** → **Allocate Elastic IP address** → Allocate
2. Chọn IP vừa tạo → **Actions** → **Associate Elastic IP address**
3. Resource type: Instance → chọn EC2 instance vừa tạo → Associate
4. Ghi lại IP (ví dụ: `52.74.184.78`)

### 1.3 Trỏ Domain về EC2

**WHY:** Domain cần trỏ về Elastic IP trước khi cài SSL.

Vào Pavietnam (hoặc DNS provider):
- Tạo A record: `@` → `52.74.184.78`
- Tạo A record: `www` → `52.74.184.78`
- TTL: 300 giây

Verify sau 5 phút:
```bash
nslookup anphuc.xyz
# Expected: Address: 52.74.184.78
```

### 1.4 SSH vào EC2

```bash
chmod 400 "/path/to/my-ec2-key-pair.pem"
ssh -i "/path/to/my-ec2-key-pair.pem" ubuntu@52.74.184.78
```

### 1.5 Cài Docker + Docker Compose + Nginx

**WHY:** Docker chạy app trong container. Nginx làm reverse proxy (nhận request từ internet, forward vào container).

```bash
# Update system
sudo apt update -y && sudo apt upgrade -y

# Install Docker
sudo apt install -y docker.io
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ubuntu

# Install Docker Compose standalone binary (v2)
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install Nginx
sudo apt install -y nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# Install MySQL client (để test RDS connection)
sudo apt install -y mysql-client
```

Verify Docker Compose:
```bash
docker-compose --version
```

**Bắt buộc logout rồi SSH lại** để docker group có hiệu lực:
```bash
exit
ssh -i "/path/to/my-ec2-key-pair.pem" ubuntu@52.74.184.78
```

Verify sau khi SSH lại:
```bash
docker --version
docker compose version
nginx -v
```

---

## Phase 2 — RDS Setup

### 2.1 Tạo RDS MySQL

**WHY:** RDS là managed database — AWS lo backup, patching, failover. Không cần cài MySQL thủ công.

1. AWS Console → RDS → **Create database**
2. Điền:
   - Engine: MySQL 8.x
   - Template: **Free tier**
   - DB instance identifier: `app-db-dev`
   - Master username: `dbadmin`
   - Master password: đặt mạnh, lưu lại
   - Instance class: `db.t4g.micro`
   - Storage: 20 GB gp2
   - **Public access: No** (quan trọng — không expose ra internet)
   - VPC security group: tạo mới tên `rds-ec2-1`
   - Initial database name: `starter_kit_db`
3. Create database (mất ~5 phút)

### 2.2 Fix Security Group cho RDS

**WHY:** RDS mặc định không cho phép kết nối từ bên ngoài. Cần allow EC2 kết nối vào port 3306.

1. AWS Console → EC2 → **Security Groups** → chọn `rds-ec2-1`
2. **Inbound rules** → **Edit inbound rules** → **Add rule**:
   - Type: MySQL/Aurora
   - Port: 3306
   - Source: chọn **Custom** → gõ tên security group của EC2 (`ec2-web-sg`) → chọn SG ID
3. Save rules

**Lý do dùng SG thay vì IP:** EC2 có thể đổi IP private nhưng SG không đổi.

### 2.3 Test kết nối RDS từ EC2

SSH vào EC2:
```bash
mysql -h app-db-dev.cns4swu4ccs9.ap-southeast-1.rds.amazonaws.com \
      -u dbadmin -p
# Nhập password → Expected: "mysql>" prompt
```

Nếu connect được:
```sql
SHOW DATABASES;
-- Expected: starter_kit_db hiện trong list
USE starter_kit_db;
EXIT;
```

---

## Phase 3 — IAM + S3 Setup

### 3.1 Tạo S3 Bucket

**WHY:** S3 lưu file upload từ app (profile pictures, attachments...).

1. AWS Console → S3 → **Create bucket**
2. Điền:
   - Bucket name: `s3-starter-ap-se-1` (phải unique toàn AWS)
   - Region: `ap-southeast-1`
   - Block all public access: **Uncheck** (app cần upload public files)
3. Create bucket

### 3.2 Tạo IAM Role cho EC2

**WHY:** IAM Role cho phép EC2 access S3 mà không cần lưu access key trong code — an toàn hơn nhiều.

1. AWS Console → IAM → **Roles** → **Create role**
2. Trusted entity: **AWS service** → EC2 → Next
3. Create policy (inline) với nội dung:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3BucketAccess",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetObjectAcl",
        "s3:PutObjectAcl",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::s3-starter-ap-se-1",
        "arn:aws:s3:::s3-starter-ap-se-1/*"
      ]
    }
  ]
}
```

4. Role name: `EC2-S3AppRole` → Create role

### 3.3 Attach IAM Role vào EC2

1. EC2 → chọn instance → **Actions** → **Security** → **Modify IAM role**
2. Chọn `EC2-S3AppRole` → Update IAM role

Verify từ EC2 (dùng IMDSv2):
```bash
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/iam/security-credentials/
# Expected: "EC2-S3AppRole"
```

---

## Phase 4 — ECR + Docker Images

### 4.1 Tạo ECR Repositories

**WHY:** ECR là private Docker registry trên AWS. EC2 pull image từ đây — không cần Docker Hub.

Chạy từ local machine:
```bash
aws ecr create-repository --repository-name starter-backend --region ap-southeast-1
aws ecr create-repository --repository-name starter-frontend --region ap-southeast-1

# Verify
aws ecr describe-repositories --region ap-southeast-1 --query 'repositories[].repositoryName'
```

Lấy AWS Account ID:
```bash
aws sts get-caller-identity --query Account --output text
# Ghi lại 12 chữ số này
```

### 4.2 Authenticate ECR

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com
```

### 4.3 Build và Push Images lên ECR

**Quan trọng:** Nếu máy là Mac M1/M2, bắt buộc dùng `--platform linux/amd64` — EC2 chạy x86.

Chạy từ root của repo:
```bash
AWS_ACCOUNT_ID=<12-digit-account-id>

# Build + push backend
docker buildx build --platform linux/amd64 \
  -t ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-backend:latest \
  --push ./backend

# Build + push frontend
docker buildx build --platform linux/amd64 \
  --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
  -t ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com/starter-frontend:latest \
  --push ./frontend
```

---

## Phase 5 — Deploy lên EC2

### 5.1 Chuẩn bị thư mục app trên EC2

SSH vào EC2:
```bash
mkdir -p ~/app
```

### 5.2 Copy files từ local lên EC2

Chạy trên **local machine**, từ root của repo:
```bash
cd "/path/to/spring-boot-nextjs-starter-kit"

scp -i "/path/to/my-ec2-key-pair.pem" docker-compose.prod.yml ubuntu@52.74.184.78:~/app/
scp -i "/path/to/my-ec2-key-pair.pem" .env.prod.example ubuntu@52.74.184.78:~/app/.env.prod.example
scp -i "/path/to/my-ec2-key-pair.pem" -r infra ubuntu@52.74.184.78:~/app/
```

### 5.3 Tạo `.env.prod` trên EC2

**WHY:** File này chứa secrets — KHÔNG commit vào git. Copy từ example rồi điền thật.

SSH vào EC2:
```bash
cp ~/app/.env.prod.example ~/app/.env.prod
nano ~/app/.env.prod
```

Điền các giá trị thật:
```bash
AWS_ACCOUNT_ID=019714369701
IMAGE_TAG=latest

DB_HOST=app-db-dev.cns4swu4ccs9.ap-southeast-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=starter_kit_db
DB_USERNAME=dbadmin
DB_PASSWORD=<mật khẩu RDS>

S3_BUCKET_NAME=s3-starter-ap-se-1
S3_REGION=ap-southeast-1

ADMIN_EMAIL=admin@anphuc.xyz
ADMIN_PASSWORD=<password muốn dùng>

SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=user
SMTP_PASSWORD=password

GITHUB_OAUTH_CLIENT_ID=<github client id>
GITHUB_OAUTH_CLIENT_SECRET=<github client secret>
GOOGLE_OAUTH_CLIENT_ID=<google client id>
GOOGLE_OAUTH_CLIENT_SECRET=<google client secret>

VAPID_PUBLIC_KEY=<generated vapid public key>
VAPID_PRIVATE_KEY=<generated vapid private key>
```

Generate VAPID keys nếu chưa có (chạy local):
```bash
npx web-push generate-vapid-keys
```

### 5.4 Verify IAM Role và ECR auth trên EC2

IMDSv2 required:
```bash
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/iam/security-credentials/
# Expected: "EC2-S3AppRole"
```

Test login ECR:
```bash
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin \
  019714369701.dkr.ecr.ap-southeast-1.amazonaws.com
# Expected: "Login Succeeded"
```

### 5.5 Chạy deploy script

```bash
chmod +x ~/app/infra/nginx/deploy.sh
cd ~/app && bash infra/nginx/deploy.sh
```

Verify containers:
```bash
docker ps
curl http://localhost:3000
docker logs starter-backend --tail 20
```

Check logs nếu có lỗi:
```bash
docker logs starter-backend 2>&1 | grep -i "error\|exception\|failed" | head -20
docker compose -f ~/app/docker-compose.prod.yml --env-file ~/app/.env.prod logs -f
```

---

## Phase 6 — Nginx + HTTPS

### 6.1 Cài Certbot

```bash
sudo apt install -y certbot python3-certbot-nginx
```

### 6.2 Stop Nginx trước khi lấy cert

**WHY:** `--standalone` mode dùng port 80 trực tiếp — Nginx phải nhả port 80 trước.

```bash
sudo systemctl stop nginx
```

### 6.3 Lấy SSL cert

```bash
sudo certbot certonly --standalone -d anphuc.xyz -d www.anphuc.xyz \
  --non-interactive --agree-tos -m admin@anphuc.xyz
# Cert lưu tại /etc/letsencrypt/live/anphuc.xyz/
# Tự renew mỗi 90 ngày qua cronjob
```

**Lưu ý DNS:** `www.anphuc.xyz` cần có A record trỏ về `52.74.184.78` trên Pavietnam trước khi chạy Certbot, không thì sẽ lỗi NXDOMAIN.

### 6.4 Deploy Nginx config đầy đủ (HTTPS)

```bash
sudo cp ~/app/infra/nginx/nginx.conf /etc/nginx/sites-available/anphuc.xyz
sudo ln -s /etc/nginx/sites-available/anphuc.xyz /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
```

Test config và start:
```bash
sudo nginx -t
sudo systemctl start nginx
sudo systemctl enable nginx
```

Verify:
```bash
curl https://anphuc.xyz
curl https://anphuc.xyz/api/auth/csrf
# Expected: JSON response
```

---

## Phase 7 — GitHub Actions CI/CD

### 7.1 Tạo OIDC Provider (AWS Console)

**WHY:** OIDC cho phép GitHub Actions authenticate với AWS mà không cần lưu long-lived access key.

1. AWS Console → IAM → **Identity providers** → **Add provider**
2. Điền:
   - Provider type: **OpenID Connect**
   - Provider URL: `https://token.actions.githubusercontent.com` → click **Get thumbprint**
   - Audience: `sts.amazonaws.com`
3. **Add provider**

### 7.2 Tạo IAM Role cho GitHub Actions (AWS Console)

1. AWS Console → IAM → **Roles** → **Create role**
2. Trusted entity type: **Web identity**
3. Điền:
   - Identity provider: `token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`
   - GitHub organization: `phuc-an-dev`
   - GitHub repository: `spring-boot-nextjs-starter-kit`
   - GitHub branch: (để trống hoặc điền `master`)
4. Attach policy: `AmazonEC2ContainerRegistryPowerUser`
5. Role name: `github-actions-deploy-role` → **Create role**
6. Copy **Role ARN** sau khi tạo xong

**Quan trọng — Trust Policy:** Sau khi tạo, vào role → **Trust relationships** → **Edit trust policy**.
Đổi `StringEquals` thành `StringLike` và dùng wildcard để match mọi branch:

```json
"StringLike": {
  "token.actions.githubusercontent.com:sub": "repo:phuc-an-dev/spring-boot-nextjs-starter-kit:*"
}
```

**Lý do:** AWS Console wizard tạo condition với branch cụ thể (e.g. `main`). Nếu repo dùng branch `master`, OIDC token sẽ không match → lỗi `Not authorized to perform sts:AssumeRoleWithWebIdentity`.

### 7.3 Thêm GitHub Secrets

GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret | Giá trị |
|--------|---------|
| `AWS_ROLE_ARN` | ARN của role vừa tạo |
| `AWS_ACCOUNT_ID` | 12-digit account ID |
| `EC2_HOST` | `52.74.184.78` |
| `EC2_SSH_KEY` | Toàn bộ nội dung file `.pem` |

Copy SSH key chính xác:
```bash
cat "/path/to/my-ec2-key-pair.pem"
# Copy toàn bộ output kể cả -----BEGIN...----- và -----END-----
```

**Lưu ý `AWS_REGION`:** Là env var trong workflow, không cần là secret.

### 7.4 Workflow Files

`maven.yml` — chạy tests với MySQL service container:
```yaml
name: Java CI with Maven

on:
  push:
    branches: ["master"]
  pull_request:
    branches: ["master"]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.4
        env:
          MYSQL_ROOT_PASSWORD: password
          MYSQL_DATABASE: starter_kit_db
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping -h localhost"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build and test with Maven
        run: mvn -B package --file ./backend/pom.xml
        env:
          SPRING_DATASOURCE_URL: "jdbc:mysql://127.0.0.1:3306/starter_kit_db?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: password
          SPRING_DATASOURCE_DRIVER_CLASS_NAME: com.mysql.cj.jdbc.Driver

      - name: Update dependency graph
        uses: advanced-security/maven-dependency-submission-action@4f64ddab9d742a4806eeb588d238e4c311a8397d
        continue-on-error: true
```

`deploy.yml` — build Docker images + deploy lên EC2 (chỉ chạy sau khi tests pass):
```yaml
name: Deploy to EC2

on:
  push:
    branches: [master]
  workflow_run:
    workflows: ["Java CI with Maven"]
    types: [completed]
    branches: [master]

permissions:
  id-token: write
  contents: read

env:
  AWS_REGION: ap-southeast-1

jobs:
  deploy:
    runs-on: ubuntu-latest
    if: ${{ github.event_name == 'push' || github.event.workflow_run.conclusion == 'success' }}
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push backend
        run: |
          docker buildx build --platform linux/amd64 \
            -t ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/starter-backend:latest \
            -t ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/starter-backend:${{ github.sha }} \
            --push ./backend

      - name: Build and push frontend
        run: |
          docker buildx build --platform linux/amd64 \
            --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
            -t ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/starter-frontend:latest \
            -t ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/starter-frontend:${{ github.sha }} \
            --push ./frontend

      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd ~/app && bash infra/nginx/deploy.sh
```

### 7.5 Kích hoạt CI/CD

Commit và push — GitHub Actions tự chạy:
```bash
git push origin master
```

Vào GitHub → **Actions** tab xem 2 workflows: `Java CI with Maven` và `Deploy to EC2` đều green ✓.

---

## Troubleshooting

Xem chi tiết từng lỗi tại [docs/ci-cd-troubleshooting.md](ci-cd-troubleshooting.md).

| Lỗi | Nguyên nhân | Fix |
|-----|-------------|-----|
| `cannot find symbol` (Lombok) | Không có `annotationProcessorPaths` trong pom.xml | Thêm `maven-compiler-plugin` với Lombok path |
| `contextLoads` fail trong CI | `SPRING_DATASOURCE_*` env vars bị profile override | Dùng `SPRING_DATASOURCE_*` thay `DB_*` vars — priority cao nhất |
| `Not authorized sts:AssumeRoleWithWebIdentity` | Trust policy branch `main` nhưng repo dùng `master` | Đổi trust policy sang `StringLike` + wildcard `*` |
| `ssh: no key found` | PEM key paste vào Secret bị lỗi format | Xóa secret, paste lại từ `cat key.pem` |
| `Connection refused :8080` | Spring Boot chưa khởi động xong | Chờ 20-30 giây |
| `docker: permission denied` | Chưa logout/login lại sau `usermod -aG docker` | `exit` rồi SSH lại |
| SSL cert fail | Domain chưa propagate hoặc Nginx chưa stop | Stop Nginx trước `certbot --standalone` |
| ECR auth fail trên EC2 | IAM Role chưa có ECR permissions | Attach `AmazonEC2ContainerRegistryReadOnly` vào `EC2-S3AppRole` |

---

## Checklist cuối

- [ ] EC2 t3.micro chạy Ubuntu, có Elastic IP `52.74.184.78`
- [ ] Docker, Docker Compose v2, Nginx cài xong
- [ ] RDS MySQL `starter_kit_db` kết nối được từ EC2
- [ ] S3 bucket `s3-starter-ap-se-1`, IAM Role attach vào EC2
- [ ] ECR repos `starter-backend`, `starter-frontend` có images
- [ ] `~/app/infra/` và `~/app/.env.prod` có trên EC2
- [ ] Containers chạy trên EC2 (`docker ps` show both Up)
- [ ] `https://anphuc.xyz` trả 200, cert hợp lệ
- [ ] GitHub Actions 2 workflows đều green khi push master
