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

# Make it work as Docker CLI plugin (enables `docker compose` syntax)
mkdir -p ~/.docker/cli-plugins
ln -s /usr/local/bin/docker-compose ~/.docker/cli-plugins/docker-compose

# Install Nginx
sudo apt install -y nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# Install MySQL client (to test RDS connection)
sudo apt install -y mysql-client

# Install AWS CLI
sudo apt install -y awscli
```

Logout rồi SSH lại (bắt buộc để docker group có hiệu lực):
```bash
exit
ssh -i "/path/to/my-ec2-key-pair.pem" ubuntu@52.74.184.78
```

Verify:
```bash
docker --version           # Docker version 29.x
docker compose version     # Docker Compose version v2.x
nginx -v                   # nginx version: nginx/1.x
aws --version              # aws-cli/x.x.x
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

Verify từ EC2:
```bash
aws sts get-caller-identity
# Expected: JSON với "Arn": "...EC2-S3AppRole..."

aws s3 ls s3://s3-starter-ap-se-1 --region ap-southeast-1
# Expected: empty list (không có lỗi = IAM role hoạt động)
```

---

## Phase 4 — ECR + Docker Images

### 4.1 Tạo ECR Repositories

**WHY:** ECR là private Docker registry trên AWS. EC2 pull image từ đây — không cần Docker Hub.

Chạy từ local machine:
```bash
aws ecr create-repository --repository-name starter-backend --region ap-southeast-1
aws ecr create-repository --repository-name starter-frontend --region ap-southeast-1
```

Ghi lại `repositoryUri` từ output — sẽ có dạng:
`123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/starter-backend`

Lấy AWS Account ID:
```bash
aws sts get-caller-identity --query Account --output text
# Ghi lại 12 chữ số này
```

### 4.2 Build và Push Images lên ECR

Chạy từ root của repo trên local machine:
```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-southeast-1
ECR=${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

# Authenticate
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR

# Build & push backend
docker build -t $ECR/starter-backend:latest ./backend
docker push $ECR/starter-backend:latest

# Build & push frontend
docker build \
  --build-arg NEXT_PUBLIC_BASE_URL=https://anphuc.xyz \
  -t $ECR/starter-frontend:latest ./frontend
docker push $ECR/starter-frontend:latest
```

---

## Phase 5 — Deploy lên EC2

### 5.1 Chuẩn bị file trên EC2

SSH vào EC2:
```bash
mkdir -p ~/app
cd ~/app
```

Copy `docker-compose.prod.yml` và `deploy.sh` từ local:
```bash
# Chạy từ local machine (root của repo):
scp -i "/path/to/key.pem" docker-compose.prod.yml ubuntu@52.74.184.78:~/app/
scp -i "/path/to/key.pem" infra/nginx/deploy.sh ubuntu@52.74.184.78:~/app/
chmod +x ~/app/deploy.sh
```

### 5.2 Tạo `.env.prod` trên EC2

**WHY:** File này chứa secrets — KHÔNG commit vào git.

SSH vào EC2:
```bash
cd ~/app
nano .env.prod
```

Điền nội dung (xem `.env.prod.example` trong repo để biết danh sách đầy đủ):
```bash
AWS_ACCOUNT_ID=<12-digit-account-id>
IMAGE_TAG=latest

DB_HOST=app-db-dev.cns4swu4ccs9.ap-southeast-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=starter_kit_db
DB_USERNAME=dbadmin
DB_PASSWORD=<your-rds-password>

S3_BUCKET_NAME=s3-starter-ap-se-1
S3_REGION=ap-southeast-1

ADMIN_EMAIL=admin@anphuc.xyz
ADMIN_PASSWORD=<strong-password>
```

### 5.3 Pull images và chạy

```bash
cd ~/app
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Auth ECR
aws ecr get-login-password --region ap-southeast-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.ap-southeast-1.amazonaws.com

# Pull và start
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d

# Verify
docker ps
```

Verify backend (chờ ~20 giây Spring Boot khởi động):
```bash
sleep 20 && curl -s http://localhost:8080/api/auth/csrf
# Expected: JSON response
```

---

## Phase 6 — Nginx + HTTPS

### 6.1 Cài Nginx config tạm (HTTP only)

**WHY:** Certbot cần port 80 hoạt động để verify domain trước khi issue SSL cert.

SSH vào EC2:
```bash
sudo tee /etc/nginx/sites-available/anphuc.xyz > /dev/null <<'EOF'
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
EOF

sudo ln -sf /etc/nginx/sites-available/anphuc.xyz /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

Verify HTTP:
```bash
curl -s -o /dev/null -w "%{http_code}" http://anphuc.xyz
# Expected: 200
```

### 6.2 Cài SSL với Certbot

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d anphuc.xyz -d www.anphuc.xyz \
  --non-interactive --agree-tos -m admin@anphuc.xyz
# Expected: "Congratulations! Your certificate..."
```

### 6.3 Deploy Nginx config đầy đủ (HTTPS)

Copy config từ repo:
```bash
# Từ local machine:
scp -i "/path/to/key.pem" infra/nginx/nginx.conf ubuntu@52.74.184.78:/tmp/nginx.conf

# Trên EC2:
sudo cp /tmp/nginx.conf /etc/nginx/sites-available/anphuc.xyz
sudo nginx -t && sudo systemctl reload nginx
```

Verify HTTPS:
```bash
curl -s -o /dev/null -w "%{http_code}" https://anphuc.xyz
# Expected: 200

curl -s https://anphuc.xyz/api/auth/csrf
# Expected: JSON

# Test login với admin account:
curl -s -X POST https://anphuc.xyz/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@anphuc.xyz","password":"<ADMIN_PASSWORD>"}' | python3 -m json.tool
# Expected: user JSON
```

---

## Phase 7 — GitHub Actions CI/CD

### 7.1 Setup OIDC (chạy một lần từ local)

**WHY:** OIDC cho phép GitHub Actions authenticate với AWS mà không cần lưu long-lived access key.

```bash
# Tạo OIDC provider
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### 7.2 Tạo IAM Role cho GitHub Actions

Tạo file `/tmp/trust-policy.json` (thay `YOUR_GITHUB_USERNAME/YOUR_REPO_NAME`):
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

aws iam attach-role-policy \
  --role-name github-actions-deploy \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser

# Ghi lại ARN:
aws iam get-role --role-name github-actions-deploy --query Role.Arn --output text
```

### 7.3 Thêm GitHub Secrets

GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret | Giá trị |
|--------|---------|
| `AWS_ROLE_ARN` | ARN từ bước 7.2 |
| `AWS_ACCOUNT_ID` | 12-digit account ID |
| `EC2_HOST` | `52.74.184.78` |
| `EC2_SSH_KEY` | Toàn bộ nội dung file `.pem` |

### 7.4 Kích hoạt CI/CD

Push code lên `master`:
```bash
git add .
git commit -m "trigger first CI/CD deploy"
git push origin master
```

Vào GitHub → **Actions** tab → xem workflow chạy.

Expected: cả 2 jobs `build-and-push` và `deploy` đều green ✓

---

## Troubleshooting

| Lỗi | Nguyên nhân | Fix |
|-----|-------------|-----|
| `Connection refused :8080` | Spring Boot chưa khởi động xong | Chờ 20-30 giây |
| `Access denied for user 'root'` | Sai credentials trong `.env.prod` | Kiểm tra `DB_USERNAME`, `DB_PASSWORD` |
| `docker: permission denied` | Chưa logout/login lại sau `usermod -aG docker` | `exit` rồi SSH lại |
| `docker-compose-plugin not found` | Ubuntu 26.04 chưa có trong Docker repo | Dùng symlink: `ln -s /usr/local/bin/docker-compose ~/.docker/cli-plugins/docker-compose` |
| SSL cert fail | Domain chưa propagate hoặc port 80 bị block | Kiểm tra Security Group có mở port 80, đợi DNS propagate |
| ECR auth fail trên EC2 | IAM Role chưa có ECR permissions | Attach `AmazonEC2ContainerRegistryReadOnly` policy vào `EC2-S3AppRole` |

---

## Checklist cuối

- [ ] EC2 t3.micro chạy Ubuntu, có Elastic IP `52.74.184.78`
- [ ] Docker, Docker Compose v2, Nginx cài xong
- [ ] RDS MySQL `starter_kit_db` kết nối được từ EC2
- [ ] S3 bucket `s3-starter-ap-se-1`, IAM Role attach vào EC2
- [ ] ECR repos `starter-backend`, `starter-frontend` có images
- [ ] Containers chạy trên EC2 (`docker ps` show both Up)
- [ ] `https://anphuc.xyz` trả 200, cert hợp lệ
- [ ] GitHub Actions deploy tự động khi push master
