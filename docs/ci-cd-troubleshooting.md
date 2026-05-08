# CI/CD Troubleshooting Log

Ghi lại các lỗi gặp phải khi setup GitHub Actions + Docker build cho dự án này, nguyên nhân, các hướng giải quyết, lý do chọn, và kết quả.

---

## 1. Backend build: `cannot find symbol` — Lombok getters không được generate

### Triệu chứng
```
Error: cannot find symbol
  symbol:   method getVerificationCode()
  location: variable user of type com.example.backend.users.User
Error: cannot find symbol
  symbol:   method getEmail()
  ...
Error: Process completed with exit code 1
```

### Nguyên nhân
`docker buildx build` trên GitHub Actions runner dùng môi trường Maven hoàn toàn mới (không có `~/.m2` cache). Khi Dockerfile chạy:
```dockerfile
RUN mvn dependency:go-offline -B   # chỉ có pom.xml, chưa có src/
COPY src ./src
RUN mvn package -DskipTests -B
```

`dependency:go-offline` không cấu hình annotation processor path → Maven không biết dùng Lombok để generate getters/setters khi compile. Locally không xảy ra vì `~/.m2` đã có Lombok từ các build trước.

### Các hướng giải quyết

| Hướng | Ưu | Nhược |
|-------|-----|-------|
| Thêm `annotationProcessorPaths` vào `maven-compiler-plugin` | Đúng chuẩn Maven, rõ ràng, không phụ thuộc cache | Cần sửa pom.xml |
| Xóa `dependency:go-offline` layer trong Dockerfile | Đơn giản | Mất layer caching, mỗi build download lại toàn bộ deps |
| Pin Lombok version cụ thể | Kiểm soát version | Vẫn không fix được vấn đề annotation processor path |

### Giải pháp chọn
Thêm `maven-compiler-plugin` với `annotationProcessorPaths` vào `pom.xml`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

**Lý do chọn:** Đây là cách Maven khuyến nghị để đảm bảo annotation processor luôn được tìm thấy, không phụ thuộc vào classpath hay cache trạng thái. Không làm mất layer caching của Docker.

### Kết quả
Backend Docker build pass. ✅

---

## 2. `BackendApplicationTests.contextLoads` fail — không có database trong CI

### Triệu chứng
```
Error: BackendApplicationTests.contextLoads » IllegalState
  Failed to load ApplicationContext
Tests run: 3, Failures: 0, Errors: 1, Skipped: 0
BUILD FAILURE
```

### Nguyên nhân
`maven.yml` gốc (workflow có sẵn trong repo) dùng **Testcontainers Cloud** (dịch vụ trả phí) để cung cấp database cho tests. Secret `TC_CLOUD_TOKEN` không được cấu hình → Testcontainers không khởi động được MySQL → `@SpringBootTest` load full Spring context thất bại vì không kết nối được DB.

### Các hướng giải quyết

| Hướng | Ưu | Nhược |
|-------|-----|-------|
| GitHub Actions MySQL service container | Miễn phí, đơn giản, tích hợp sẵn | Cần cấu hình env vars |
| H2 in-memory cho tests | Rất nhanh, không cần service | SQL dialect khác MySQL, dễ che giấu bugs |
| Testcontainers local (không cloud) | Giống production nhất | Cần Docker trong runner (chậm hơn) |
| Bỏ qua `contextLoads` test | Nhanh | Mất coverage, anti-pattern |
| MySQL trả phí trên Testcontainers Cloud | Gần production | Tốn tiền, không phù hợp learning project |

### Giải pháp chọn
Thay Testcontainers Cloud bằng GitHub Actions MySQL service container:

```yaml
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
```

Thêm env vars vào build step để Spring kết nối đúng host:
```yaml
env:
  DB_HOST: 127.0.0.1
  DB_PORT: 3306
  DB_NAME: starter_kit_db
  DB_USERNAME: root
  DB_PASSWORD: password
```

Đồng thời đổi Java version từ `23` → `21` để khớp với Dockerfile (tránh behavior khác nhau giữa CI test và production build).

**Lý do chọn:** MySQL service container miễn phí, dùng đúng MySQL 8.4 (giống production), không thay đổi logic test. H2 bị loại vì dialect khác có thể che giấu SQL bugs. Testcontainers Cloud bị loại vì tốn phí.

### Kết quả lần 1
Workflow chạy được, nhưng tests vẫn fail (xem Issue #7 bên dưới).

---

## 7. `contextLoads` fail lần 2 — Spring profile override env vars

### Triệu chứng
Tests fail với cùng lỗi `Failed to load ApplicationContext` dù đã thêm MySQL service container và env vars `DB_HOST`/`DB_PORT`.

### Nguyên nhân
`application.properties` có `spring.profiles.active=dev` → Spring tự động load `application-dev.properties`. File này có hardcoded values:

```properties
app.database.host=localhost
app.database.port=3307
```

**Spring property resolution order:** profile-specific properties (`application-dev.properties`) có độ ưu tiên cao hơn env vars `DB_HOST`/`DB_PORT`. Vì vậy dù maven.yml set `DB_HOST=127.0.0.1, DB_PORT=3306`, Spring vẫn dùng `localhost:3307` từ dev profile → kết nối thất bại.

### Giải pháp chọn
Thay `DB_*` env vars bằng `SPRING_DATASOURCE_*` — loại env vars này có độ ưu tiên cao nhất trong Spring Boot, override mọi properties file kể cả profile-specific:

```yaml
env:
  SPRING_DATASOURCE_URL: "jdbc:mysql://127.0.0.1:3306/starter_kit_db?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
  SPRING_DATASOURCE_USERNAME: root
  SPRING_DATASOURCE_PASSWORD: password
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: com.mysql.cj.jdbc.Driver
```

**Lý do chọn:** `SPRING_DATASOURCE_*` là [externalized configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config) ở mức cao nhất — không bị bất kỳ properties file nào override. Đây là cách Spring Boot intended cho CI/CD environments.

### Kết quả
`Java CI with Maven` workflow pass. ✅

---

## 3. OIDC `Not authorized to perform sts:AssumeRoleWithWebIdentity`

### Triệu chứng
```
Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
Error: Process completed with exit code 1.
```

### Nguyên nhân
Khi tạo IAM Role `github-actions-deploy-role` qua AWS Console, wizard hỏi "GitHub branch" và người dùng nhập `main`. AWS Console tạo trust policy với condition:
```json
"token.actions.githubusercontent.com:sub": "repo:phuc-an-dev/spring-boot-nextjs-starter-kit:ref:refs/heads/main"
```

Nhưng repo dùng branch `master` → OIDC token của GitHub Actions có `sub: ...refs/heads/master` → không match condition → AWS từ chối assume role.

### Các hướng giải quyết

| Hướng | Ưu | Nhược |
|-------|-----|-------|
| Đổi condition sang `master` | Chính xác | Vẫn bị khóa nếu đổi branch sau này |
| Dùng wildcard `repo:org/repo:*` | Linh hoạt, match mọi branch | Cho phép bất kỳ branch nào assume role |
| Đổi branch repo sang `main` | Match với policy | Thay đổi không cần thiết |

### Giải pháp chọn
Cập nhật trust policy dùng wildcard trong IAM Console (Trust relationships → Edit trust policy):

```json
"StringLike": {
  "token.actions.githubusercontent.com:sub": "repo:phuc-an-dev/spring-boot-nextjs-starter-kit:*"
}
```

**Lý do chọn:** Wildcard `*` match cả `master`, `main`, tags, và pull requests. Phù hợp cho learning project. Nếu cần security chặt hơn có thể giới hạn lại sau.

### Kết quả
Deploy to EC2 workflow pass, OIDC auth thành công. ✅

---

## 4. SSH deploy: `ssh: no key found`

### Triệu chứng
```
2026/05/08 02:40:43 ssh.ParsePrivateKey: ssh: no key found
ssh: handshake failed: ssh: unable to authenticate
Error: Process completed with exit code 1.
```

### Nguyên nhân
`appleboy/ssh-action` parse trực tiếp nội dung PEM key từ GitHub Secret `EC2_SSH_KEY`. Key bị lỗi khi paste vào secret: thiếu newline ở cuối, có khoảng trắng thừa, hoặc format không đúng.

### Giải pháp chọn
Xóa và tạo lại secret `EC2_SSH_KEY` bằng cách copy chính xác toàn bộ nội dung file `.pem` (kể cả dòng `-----BEGIN...-----` và `-----END...-----`):

```bash
cat my-ec2-key-pair.pem
# Copy toàn bộ output, paste vào GitHub Secret
```

### Kết quả
SSH auth thành công, deploy script chạy trên EC2. ✅

---

## 5. MySQL JDBC driver deprecated — `ClassNotFoundException` (phát hiện qua code review)

### Triệu chứng
Phát hiện trong code review trước khi deploy, chưa crash production.

```
spring.datasource.driver-class-name=com.mysql.jdbc.Driver
```

### Nguyên nhân
Class `com.mysql.jdbc.Driver` bị xóa trong `mysql-connector-j` 8+. Spring Boot 3.4 resolve `mysql-connector-j` 9.x → sẽ gây `ClassNotFoundException` khi khởi động trên RDS.

### Giải pháp chọn
Đổi sang class mới:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Apply cho cả `application.properties` và `application-prod.properties`.

### Kết quả
Backend khởi động thành công, kết nối RDS MySQL 8.4. ✅

---

## 6. Next.js standalone bind `127.0.0.1` — container không nhận traffic (phát hiện qua code review)

### Triệu chứng
Phát hiện trong code review trước khi deploy. Docker port mapping `3000:3000` hoạt động nhưng Next.js server từ chối kết nối từ bên ngoài container.

### Nguyên nhân
Next.js 14 standalone (`server.js`) mặc định bind `127.0.0.1` (loopback) thay vì `0.0.0.0`. Docker mapping port `3000:3000` forward traffic vào interface ngoài của container, nhưng server chỉ lắng nghe loopback → connection refused.

### Giải pháp chọn
Thêm vào `frontend/Dockerfile` runner stage:
```dockerfile
ENV HOSTNAME=0.0.0.0
```

### Kết quả
Frontend container nhận traffic từ Nginx proxy. ✅
