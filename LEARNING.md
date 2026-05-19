# DevOps AWS Learning Path

> **Mục tiêu:** Deploy fullstack app lên AWS, làm quen AWS Console, đủ để ghi CV  
> **Stack:** EC2 · Docker Compose · Nginx · RDS · S3 · ECR · CloudFront · ALB · ECS Fargate · IAM · CloudWatch · Route 53 · ACM · GitHub Actions  
> **Timeline:** ~3–4 tháng · Free tier friendly  
> **Repo này dùng làm:** thực hành infra, không cần tự viết app — fork repo có sẵn

---

## Progress

| Phase | Status | Started | Completed |
|-------|--------|---------|-----------|
| Phase 1 — Foundation | `[ ] not started` | — | — |
| Phase 2 — Core Infra | `[ ] not started` | — | — |
| Phase 3 — CI/CD + CDN + Monitoring | `[ ] not started` | — | — |
| Phase 4 — Production hardening | `[ ] not started` | — | — |

---

## Phase 1 — Foundation: IAM, VPC, EC2 đúng cách

**Mục tiêu:** Hiểu Console không bị mù, setup network an toàn

### Project 1 — IAM hardening `[ ]`

- [ ] Tắt root account, bật MFA
- [ ] Tạo IAM user riêng với AdministratorAccess
- [ ] Tạo IAM Role cho EC2 (instance profile) — không dùng access key trên server
- [ ] Tạo policy custom với least-privilege cho S3 bucket cụ thể
- [ ] Dùng IAM Policy Simulator để test permission
- [ ] Tạo IAM user cho GitHub Actions (OIDC hoặc access key)

**CV note:** "Configured IAM roles, least-privilege policies, and MFA for AWS account security"

---

### Project 2 — VPC từ đầu `[ ]`

- [ ] Tạo VPC mới (không dùng default VPC)
- [ ] Tạo public subnet + private subnet trong 2 AZ
- [ ] Cấu hình Internet Gateway, Route Table
- [ ] Tạo NAT Gateway cho private subnet ra internet
- [ ] Tạo Security Group riêng cho web (80/443), app (8080), db (5432)
- [ ] Launch EC2 vào private subnet, SSH qua Bastion host
- [ ] Test kết nối: bastion → app → db

> ⚠️ Nhớ tắt NAT Gateway khi không dùng (~$0.045/giờ, không nằm trong free tier)

**CV note:** "Designed custom VPC with public/private subnets, NAT Gateway, and Security Groups"

---

## Phase 2 — Core Infra: EC2 + Docker + RDS + Domain

**Mục tiêu:** Deploy app thật lên AWS, có domain, có HTTPS  
**Repo gợi ý:** `docker/awesome-compose` (nextjs + spring-boot) hoặc tìm `spring-boot-nextjs-docker` trên GitHub

### Project 3 — Deploy fullstack với Docker Compose trên EC2 `[ ]`

- [ ] Fork repo NextJS + Spring Boot từ GitHub
- [ ] Launch EC2 t2.micro, SSH, cài Docker + Docker Compose
- [ ] Push Docker images lên ECR (tạo repo, docker tag, push)
- [ ] Pull image từ ECR về EC2, chạy `docker-compose up`
- [ ] Cấu hình Nginx làm reverse proxy (80 → Next.js, /api → Spring Boot)
- [ ] Mount S3 bucket cho static files hoặc uploads
- [ ] Gắn Elastic IP để địa chỉ IP không đổi khi restart

**CV note:** "Deployed containerized fullstack app on EC2 with Docker Compose, Nginx reverse proxy, and ECR"

---

### Project 4 — RDS + domain + HTTPS `[ ]`

- [ ] Tạo RDS PostgreSQL t3.micro trong private subnet (free tier)
- [ ] Config Security Group: chỉ cho phép EC2 security group kết nối port 5432
- [ ] Update app kết nối RDS thay vì localhost DB
- [ ] Mua domain (hoặc dùng Route 53 subdomain test)
- [ ] Tạo A record trỏ về Elastic IP
- [ ] Cấp SSL cert với AWS ACM hoặc Certbot (Let's Encrypt) trên EC2
- [ ] Cấu hình Nginx HTTPS 443 + redirect 80 → 443

> ⚠️ RDS t3.micro free 12 tháng đầu, chỉ 1 instance — không tạo Multi-AZ

**CV note:** "Provisioned RDS PostgreSQL, configured Route 53 DNS, and SSL/TLS with ACM"

---

## Phase 3 — CI/CD + CloudFront + CloudWatch

**Mục tiêu:** Tự động hóa deploy, tối ưu static assets, có monitoring — phần nặng CV nhất

### Project 5 — GitHub Actions CI/CD pipeline `[ ]`

- [ ] Tạo workflow: push main → build Docker image → push ECR
- [ ] Dùng OIDC để GitHub Actions auth với AWS (không dùng long-lived access key)
- [ ] SSH vào EC2 từ GitHub Actions, pull image mới, restart container
- [ ] Thêm step chạy test trước khi build
- [ ] Thêm environment protection (require approval trước khi deploy prod)
- [ ] Test: push code → tự động deploy, truy cập domain thấy thay đổi

**CV note:** "Built GitHub Actions CI/CD pipeline: build → ECR push → automated EC2 deployment"

---

### Project 6 — CloudFront CDN cho static assets `[ ]`

- [ ] Deploy Next.js static export lên S3 bucket (static website hosting)
- [ ] Tạo CloudFront distribution trỏ về S3 bucket
- [ ] Gắn domain + SSL cert vào CloudFront distribution
- [ ] Cấu hình cache behavior: HTML no-cache, JS/CSS cache dài
- [ ] Thêm bước vào GitHub Actions: sau build → sync S3 → invalidate CloudFront
- [ ] So sánh tốc độ load trước/sau CloudFront

**CV note:** "Configured CloudFront CDN with S3 origin, custom domain, SSL, and cache invalidation in CI/CD"

---

### Project 7 — CloudWatch monitoring + alerting `[ ]`

- [ ] Cài CloudWatch Agent trên EC2, đẩy system logs + app logs
- [ ] Tạo Log Group, xem logs từ Docker containers
- [ ] Tạo metric filter: đếm số lần HTTP 500 error
- [ ] Tạo CloudWatch Alarm: CPU > 80% hoặc error count > 10 → trigger SNS
- [ ] Setup SNS → gửi email alert khi alarm
- [ ] Tạo dashboard tổng hợp: CPU, memory, request count, error rate

**CV note:** "Set up CloudWatch logging, metrics, alarms, and SNS notifications for production monitoring"

---

## Phase 4 — Production hardening

**Mục tiêu:** Setup đủ vững để nói "production-ready" trên CV

### Project 8 — ALB + ECS Fargate migration `[ ]`

- [ ] Tạo Application Load Balancer, Target Group, Listener rule
- [ ] Tạo ECS Cluster (Fargate), Task Definition từ ECR image
- [ ] Tạo ECS Service, gắn vào ALB Target Group
- [ ] Update GitHub Actions: sau push ECR → update ECS service (force new deployment)
- [ ] Test rolling update: deploy version mới không downtime
- [ ] So sánh chi phí ECS Fargate vs EC2 t2.micro

> ⚠️ ECS Fargate không có free tier — chạy thử xong nên terminate để tránh bill

**CV note:** "Migrated to ECS Fargate with ALB, automated zero-downtime deployments via GitHub Actions"

---

## CV Summary (sau khi hoàn thành)

```
AWS: EC2 · VPC · RDS · S3 · ECR · ECS Fargate · CloudFront · ALB
     IAM · CloudWatch · Route 53 · ACM · SNS
DevOps: Docker · Docker Compose · Nginx · GitHub Actions CI/CD
Other: SSL/TLS · Domain management · Container registry
```

---

## Session Notes

> Dùng section này để ghi lại context khi làm việc với Claude Code  
> Format: `## YYYY-MM-DD — [topic]`

### Template

```
## YYYY-MM-DD — Project X: [tên task]

**Đang làm:** 
**Vấn đề gặp:** 
**Đã thử:** 
**Cần Claude giúp:** 
```
