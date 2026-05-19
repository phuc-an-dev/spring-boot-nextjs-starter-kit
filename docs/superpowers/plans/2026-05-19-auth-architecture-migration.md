# Auth Architecture Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Chuẩn hoá backend theo kiến trúc permission/response/Flyway/test của `authentication-service`, đồng thời migrate auth hiện tại sang JWT cookie + refresh token + token revocation mà vẫn giữ OAuth2 Google, endpoint hiện tại và `Long` ID.

**Architecture:** Migration giữ public API path hiện tại (`/api/auth`, `/api/users`, `/api/admin/...`) để giảm vỡ frontend, nhưng thay cơ chế xác thực session bằng JWT resource-server đọc token từ cookie. Permission model được thêm theo hướng scope authority, ban đầu map từ `Role.USER` và `Role.ADMIN`; response wrapper được áp dụng có kiểm soát để frontend có thể cập nhật theo từng lát.

**Tech Stack:** Spring Boot 3.4, Java 21, Spring Security OAuth2 Resource Server, Nimbus JWT, MySQL, Flyway, JobRunr, JPA/Hibernate, Next.js 14, Axios, SWR, TypeScript generated client.

---

## Completion Summary

Completed on branch `codex/auth-response-contract`.

- Task 1 commit: `30d308f feat: add standard api response contract`
- Task 2 commit: `f4ca76c feat: add permission-based authorization model`
- Task 3 commit: `ebf9d4e feat: add jwt token and auth cookie services`
- Task 4 commit: `ea553a5 feat: add revoked token tracking`
- Task 5 commit: `f2b5400 feat: migrate auth endpoints to jwt cookies`
- Task 6 commit: `1c5a87e feat: enable jwt cookie resource server`
- Task 7 commit: `e059f42 feat: harden email verification tokens`
- Task 8 commit: `00ffc7a feat: manage backend schema with flyway`
- Task 9 commit: `4aae0a2 feat: adapt frontend auth to jwt cookie responses`
- Task 10 verification fix commit: `4b714a0 fix: preserve oauth2 user profile data`

Final verification:

- Backend full test: passed, `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`.
- Backend compile: passed.
- Frontend typecheck, lint, and build: passed with Node `20.20.2`.
- Manual smoke on backend port `18080`: login issued access/refresh cookies, `me` succeeded, refresh succeeded, logout cleared cookies, revoked access token was rejected.

---

## Quyết Định Đã Chốt

- Giữ OAuth2 Google; không xoá OAuth2 flow hiện tại.
- Giữ endpoint hiện tại; không đổi sang `/api/v1/auths`.
- Giữ `Long` ID; không migrate entity ID sang UUID string.
- Chuẩn hoá backend theo kiến trúc mới: JWT cookie auth, refresh/revoke token, permission constants, response wrapper, Flyway, test conventions.
- Email verification giữ flow link-token hiện tại vì hợp hơn với web app và OAuth2 redirect. Nâng cấp token thành expiry rõ ràng, single-use, invalidate sau verify, tiếp tục dùng JobRunr + HTML template.

## File Structure

### Nhóm auth/token/security

- Modify: `backend/pom.xml`
  - Thêm `spring-boot-starter-oauth2-resource-server`, `flyway-core`, `flyway-mysql`, `caffeine` nếu cần cache revocation.
- Modify: `backend/src/main/java/com/example/backend/config/ApplicationProperties.java`
  - Thêm JWT/cookie/token expiry properties.
- Modify: `backend/src/main/resources/application.properties`
  - Thêm cấu hình `app.jwt-secret-key`, `app.access-token-expires-in`, `app.refresh-token-expires-in`, `app.cookie-domain`, `app.cookie-secure`.
- Create: `backend/src/main/java/com/example/backend/token/TokenService.java`
  - Generate/introspect JWT, build scope từ role/permission.
- Create: `backend/src/main/java/com/example/backend/token/CookieService.java`
  - Đọc/ghi/xoá `access_token` và `refresh_token` cookies.
- Create: `backend/src/main/java/com/example/backend/token/RevokedToken.java`
  - Entity lưu `jti`, `revokedAt`, `expiresAt`.
- Create: `backend/src/main/java/com/example/backend/token/RevokedTokenRepository.java`
  - Repository check token revoked và xoá token hết hạn.
- Create: `backend/src/main/java/com/example/backend/token/TokenRevocationService.java`
  - Revoke/logout/cleanup token.
- Create: `backend/src/main/java/com/example/backend/auth/CookieBearerTokenResolver.java`
  - Ưu tiên token trong cookie, fallback `Authorization: Bearer`.
- Create: `backend/src/main/java/com/example/backend/auth/CustomJwtDecoder.java`
  - Decode + verify signature + check revocation.
- Modify: `backend/src/main/java/com/example/backend/auth/SecurityConfiguration.java`
  - Chuyển request authentication sang OAuth2 resource server JWT, giữ OAuth2 Google login.
- Modify: `backend/src/main/java/com/example/backend/auth/service/AuthService.java`
  - Login set JWT cookies thay vì session; logout revoke cookies; add refresh; `me` đọc current authenticated JWT/user.
- Modify: `backend/src/main/java/com/example/backend/auth/controller/AuthController.java`
  - Giữ `/api/auth/login`, `/api/auth/logout`, `/api/auth/me`; add `/api/auth/refresh`.
- Modify: `backend/src/main/java/com/example/backend/auth/Oauth2LoginSuccessHandler.java`
  - Sau Google login, phát JWT cookies rồi redirect về login-success.

### Nhóm permission/response/error

- Create: `backend/src/main/java/com/example/backend/auth/Permission.java`
  - Permission enum dạng `user:self:read`, `admin:user:filter`, `notification:send`.
- Modify: `backend/src/main/java/com/example/backend/users/Role.java`
  - Thêm `Set<Permission>` và helper `getPermissionSet()`.
- Create: `backend/src/main/java/com/example/backend/auth/SecurityPermissions.java`
  - Constants dùng trong `@PreAuthorize`.
- Create: `backend/src/main/java/com/example/backend/util/ApiResponse.java`
  - Base response: `message`, `success`.
- Create: `backend/src/main/java/com/example/backend/util/SuccessApiResponse.java`
  - Response wrapper success.
- Create: `backend/src/main/java/com/example/backend/util/ErrorApiResponse.java`
  - Response wrapper error.
- Create: `backend/src/main/java/com/example/backend/util/ErrorCode.java`
  - Error taxonomy có HTTP status.
- Modify: `backend/src/main/java/com/example/backend/util/ExceptionHandler.java`
  - Trả về `ErrorApiResponse`, giữ thông tin validation field errors.
- Modify: `backend/src/main/java/com/example/backend/admin/controller/AdminUsersController.java`
  - Chuyển `hasRole('ADMIN')` sang `hasAuthority(SecurityPermissions.ADMIN_USER_FILTER)`.

### Nhóm Flyway/schema

- Create: `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`
  - Baseline schema tương thích entity hiện tại với `Long` ID.
- Create: `backend/src/main/resources/db/migration/V2__create_revoked_tokens.sql`
  - Tạo bảng `revoked_tokens`.
- Create: `backend/src/main/resources/db/migration/V3__harden_verification_and_reset_tokens.sql`
  - Thêm/đảm bảo expiry và consumed fields nếu entity hiện tại chưa có.
- Modify: `backend/src/main/resources/application.properties`
  - Đổi `spring.jpa.hibernate.ddl-auto=validate` sau khi Flyway baseline ổn.

### Nhóm email verification

- Modify: `backend/src/main/java/com/example/backend/users/VerificationCode.java`
  - Bảo đảm token có expiry và single-use state.
- Modify: `backend/src/main/java/com/example/backend/users/service/UserService.java`
  - Verify email xoá/invalidate token sau khi dùng, check expired rõ ràng.
- Modify: `backend/src/main/java/com/example/backend/users/jobs/handlers/SendWelcomeEmailJobHandler.java`
  - Giữ link verify hiện tại, không đổi sang 6-digit code.

### Nhóm frontend/generated client

- Modify: `frontend/lib/httpClient.ts`
  - Add response unwrap helper hoặc typed wrapper handling.
- Modify: `frontend/lib/auth/use-auth.ts`
  - Update login/logout/me/refresh behavior theo JWT cookie.
- Modify: `frontend/models/http/HttpErrorResponse.ts`
  - Đồng bộ error shape mới.
- Run: `cd frontend && npm run update-types`
  - Sau khi backend annotations/client contract ổn.

### Nhóm tests

- Create: `backend/src/test/java/com/example/backend/token/TokenServiceTest.java`
- Create: `backend/src/test/java/com/example/backend/token/CookieServiceTest.java`
- Create: `backend/src/test/java/com/example/backend/token/TokenRevocationServiceTest.java`
- Create: `backend/src/test/java/com/example/backend/auth/AuthControllerTest.java`
- Create: `backend/src/test/java/com/example/backend/auth/AuthServiceTest.java`
- Create: `backend/src/test/java/com/example/backend/auth/SecurityPermissionConventionTest.java`
- Create: `backend/src/test/java/com/example/backend/users/UserServiceEmailVerificationTest.java`

---

## Task 1: Thêm Nền Tảng Response Và Error Contract

**Files:**
- Create: `backend/src/main/java/com/example/backend/util/ApiResponse.java`
- Create: `backend/src/main/java/com/example/backend/util/SuccessApiResponse.java`
- Create: `backend/src/main/java/com/example/backend/util/ErrorApiResponse.java`
- Create: `backend/src/main/java/com/example/backend/util/ErrorCode.java`
- Modify: `backend/src/main/java/com/example/backend/util/ExceptionHandler.java`
- Test: `backend/src/test/java/com/example/backend/util/ExceptionHandlerTest.java`

- [x] **Step 1: Write the failing controller advice test**

Create `backend/src/test/java/com/example/backend/util/ExceptionHandlerTest.java`:

```java
package com.example.backend.util;

import com.example.backend.util.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlerTest {
  private final ExceptionHandler exceptionHandler = new ExceptionHandler();

  @Test
  void handleApiException_returnsStandardErrorResponse() {
    ApiException exception = ApiException.builder()
        .status(404)
        .message("User not found")
        .build();

    ResponseEntity<HttpErrorResponse> legacyResponse = exceptionHandler.handleException(exception);

    assertThat(legacyResponse.getStatusCode().value()).isEqualTo(404);
    assertThat(legacyResponse.getBody()).isNotNull();
    assertThat(legacyResponse.getBody().getMessage()).isEqualTo("User not found");
  }
}
```

- [x] **Step 2: Run test to verify current behavior**

Run:

```bash
cd backend
./mvnw -Dtest=ExceptionHandlerTest test
```

Expected: PASS before refactor, giving a safety baseline for existing behavior.

- [x] **Step 3: Add standard response classes**

Create `ApiResponse`, `SuccessApiResponse`, `ErrorApiResponse`, and `ErrorCode` with this shape:

```java
package com.example.backend.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {
  private String message;
  private boolean success;
}
```

```java
package com.example.backend.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessApiResponse<T> extends ApiResponse {
  private T data;

  public SuccessApiResponse() {
    super("Success", true);
  }

  public SuccessApiResponse(T data) {
    super("Success", true);
    this.data = data;
  }

  public SuccessApiResponse(String message, T data) {
    super(message, true);
    this.data = data;
  }
}
```

```java
package com.example.backend.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorApiResponse<T> extends ApiResponse {
  private int status;
  private int errorCode;
  private Map<String, String> errors;
  private List<String> generalErrors;
  private T details;

  public ErrorApiResponse(String message, int status, int errorCode) {
    super(message, false);
    this.status = status;
    this.errorCode = errorCode;
  }
}
```

```java
package com.example.backend.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  UNKNOWN_ERROR(99, "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR),
  VALIDATION_ERROR(102, "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),
  ENTITY_NOT_FOUND(103, "Entity not found", HttpStatus.NOT_FOUND),
  UNAUTHORIZED(1007, "Unauthorized", HttpStatus.UNAUTHORIZED),
  PERMISSION_DENIED(1005, "Permission denied", HttpStatus.FORBIDDEN),
  REFRESH_TOKEN_INVALID(204, "Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED);

  private final int code;
  private final String message;
  private final HttpStatus httpStatus;
}
```

- [x] **Step 4: Refactor exception handler without changing endpoint paths**

Update `ExceptionHandler` so `ApiException`, validation, bad credentials, authorization denied and fallback exceptions return `ErrorApiResponse<?>`. Keep status codes currently used by frontend: validation remains `422`, bad credentials remains `401`, authorization remains `403`.

- [x] **Step 5: Update and run test**

Update the test to assert `ErrorApiResponse`:

```java
ResponseEntity<ErrorApiResponse<?>> response = exceptionHandler.handleApiException(exception);
assertThat(response.getBody().isSuccess()).isFalse();
assertThat(response.getBody().getMessage()).isEqualTo("User not found");
assertThat(response.getBody().getStatus()).isEqualTo(404);
```

Run:

```bash
cd backend
./mvnw -Dtest=ExceptionHandlerTest test
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/util backend/src/test/java/com/example/backend/util/ExceptionHandlerTest.java
git commit -m "feat: add standard api response contract"
```

---

## Task 2: Thêm Permission Model Không Đổi Endpoint

**Files:**
- Create: `backend/src/main/java/com/example/backend/auth/Permission.java`
- Create: `backend/src/main/java/com/example/backend/auth/SecurityPermissions.java`
- Modify: `backend/src/main/java/com/example/backend/users/Role.java`
- Modify: `backend/src/main/java/com/example/backend/admin/controller/AdminUsersController.java`
- Test: `backend/src/test/java/com/example/backend/auth/SecurityPermissionConventionTest.java`

- [x] **Step 1: Write failing convention test**

Create `SecurityPermissionConventionTest`:

```java
package com.example.backend.auth;

import com.example.backend.admin.controller.AdminUsersController;
import com.example.backend.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPermissionConventionTest {
  @Test
  void adminRole_containsAdminUserFilterPermission() {
    assertThat(Role.ADMIN.getPermissionSet())
        .extracting(Permission::getPermission)
        .contains(SecurityPermissions.ADMIN_USER_FILTER);
  }

  @Test
  void adminUsersEndpoint_usesAuthorityPermissionInsteadOfRawRole() throws NoSuchMethodException {
    PreAuthorize annotation = AdminUsersController.class
        .getMethod("admin_getUsers", int.class)
        .getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value())
        .isEqualTo("hasAuthority('" + SecurityPermissions.ADMIN_USER_FILTER + "')");
  }
}
```

- [x] **Step 2: Run test to verify it fails**

```bash
cd backend
./mvnw -Dtest=SecurityPermissionConventionTest test
```

Expected: FAIL because `Permission`, `SecurityPermissions`, and `Role.getPermissionSet()` do not exist yet.

- [x] **Step 3: Add permission enum and constants**

Create `Permission.java`:

```java
package com.example.backend.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Permission {
  USER_SELF_READ("user:self:read"),
  USER_SELF_UPDATE("user:self:update"),
  ADMIN_USER_FILTER("admin:user:filter"),
  ADMIN_USER_READ("admin:user:read"),
  NOTIFICATION_SEND("notification:send"),
  NOTIFICATION_STATS_READ("notification:stats:read");

  private final String permission;
}
```

Create `SecurityPermissions.java`:

```java
package com.example.backend.auth;

public interface SecurityPermissions {
  String USER_SELF_READ = "user:self:read";
  String USER_SELF_UPDATE = "user:self:update";
  String ADMIN_USER_FILTER = "admin:user:filter";
  String ADMIN_USER_READ = "admin:user:read";
  String NOTIFICATION_SEND = "notification:send";
  String NOTIFICATION_STATS_READ = "notification:stats:read";
}
```

- [x] **Step 4: Extend Role with permission sets**

Update `Role.java`:

```java
package com.example.backend.users;

import com.example.backend.auth.Permission;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.example.backend.auth.Permission.*;

@Getter
@RequiredArgsConstructor
public enum Role {
  USER(Set.of(USER_SELF_READ, USER_SELF_UPDATE)),
  ADMIN(Set.of(Permission.values()));

  private final Set<Permission> permissionSet;
}
```

- [x] **Step 5: Update admin authorization**

Change `AdminUsersController.admin_getUsers` to:

```java
@GetMapping
@PreAuthorize("hasAuthority('" + SecurityPermissions.ADMIN_USER_FILTER + "')")
public ResponseEntity<PagedResponse<UserResponse>> admin_getUsers(
    @RequestParam(value = "page", defaultValue = "0") int page
) {
  PagedResponse<UserResponse> users = userService.getUsers(page);
  return ResponseEntity.ok(users);
}
```

Add import:

```java
import com.example.backend.auth.SecurityPermissions;
```

- [x] **Step 6: Run permission test**

```bash
cd backend
./mvnw -Dtest=SecurityPermissionConventionTest test
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/auth backend/src/main/java/com/example/backend/users/Role.java backend/src/main/java/com/example/backend/admin/controller/AdminUsersController.java backend/src/test/java/com/example/backend/auth/SecurityPermissionConventionTest.java
git commit -m "feat: add permission-based authorization model"
```

---

## Task 3: Thêm JWT Token Service Và Cookie Service

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/example/backend/config/ApplicationProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/example/backend/token/TokenService.java`
- Create: `backend/src/main/java/com/example/backend/token/CookieService.java`
- Test: `backend/src/test/java/com/example/backend/token/TokenServiceTest.java`
- Test: `backend/src/test/java/com/example/backend/token/CookieServiceTest.java`

- [x] **Step 1: Write failing TokenService test**

Create `TokenServiceTest`:

```java
package com.example.backend.token;

import com.example.backend.config.ApplicationProperties;
import com.example.backend.users.Role;
import com.example.backend.users.User;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {
  @Test
  void generateAccessToken_containsSubjectRoleAndPermissionScope() {
    ApplicationProperties properties = new ApplicationProperties();
    properties.setJwtSecretKey("12345678901234567890123456789012");
    properties.setAccessTokenExpiresIn(900);
    properties.setRefreshTokenExpiresIn(3600);

    User user = new User();
    ReflectionTestUtils.setField(user, "email", "admin@email.com");
    user.setRole(Role.ADMIN);

    TokenService tokenService = new TokenService(properties);
    String token = tokenService.generateAccessToken(user);
    JWTClaimsSet claims = tokenService.getClaims(token);

    assertThat(tokenService.introspect(token)).isTrue();
    assertThat(claims.getSubject()).isEqualTo("admin@email.com");
    assertThat(claims.getStringClaim("email")).isEqualTo("admin@email.com");
    assertThat(claims.getStringClaim("scope")).contains("ROLE_ADMIN", "admin:user:filter");
    assertThat(claims.getJWTID()).isNotBlank();
  }
}
```

- [x] **Step 2: Run test to verify it fails**

```bash
cd backend
./mvnw -Dtest=TokenServiceTest test
```

Expected: FAIL because token package and JWT properties do not exist.

- [x] **Step 3: Add dependencies and properties**

Add to `backend/pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Add fields to `ApplicationProperties`:

```java
private String jwtSecretKey;
private int accessTokenExpiresIn;
private int refreshTokenExpiresIn;
private String cookieDomain;
private boolean cookieSecure;
```

Add to `application.properties`:

```properties
app.jwt-secret-key=${JWT_SECRET_KEY:12345678901234567890123456789012}
app.access-token-expires-in=${ACCESS_TOKEN_EXPIRES_IN:43200}
app.refresh-token-expires-in=${REFRESH_TOKEN_EXPIRES_IN:2592000}
app.cookie-domain=${COOKIE_DOMAIN:localhost}
app.cookie-secure=${COOKIE_SECURE:false}
```

- [x] **Step 4: Implement TokenService**

Create `backend/src/main/java/com/example/backend/token/TokenService.java`:

```java
package com.example.backend.token;

import com.example.backend.auth.Permission;
import com.example.backend.config.ApplicationProperties;
import com.example.backend.users.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {
  private final ApplicationProperties applicationProperties;

  public String generateAccessToken(User user) {
    return generateToken(user, applicationProperties.getAccessTokenExpiresIn());
  }

  public String generateRefreshToken(User user) {
    return generateToken(user, applicationProperties.getRefreshTokenExpiresIn());
  }

  public String generateToken(User user, long expiryInSeconds) {
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .subject(user.getEmail())
        .issuer("backend")
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(expiryInSeconds, ChronoUnit.SECONDS)))
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("scope", buildScope(user))
        .build();
    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      signedJWT.sign(new MACSigner(applicationProperties.getJwtSecretKey().getBytes()));
      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to generate JWT", e);
    }
  }

  public String buildScope(User user) {
    StringJoiner scope = new StringJoiner(" ");
    scope.add("ROLE_" + user.getRole().name());
    for (Permission permission : user.getRole().getPermissionSet()) {
      scope.add(permission.getPermission());
    }
    return scope.toString();
  }

  public JWTClaimsSet getClaims(String token) {
    try {
      return SignedJWT.parse(token).getJWTClaimsSet();
    } catch (ParseException e) {
      return null;
    }
  }

  public boolean introspect(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
      if (expirationTime == null || expirationTime.before(new Date())) {
        return false;
      }
      JWSVerifier verifier = new MACVerifier(applicationProperties.getJwtSecretKey().getBytes());
      return jwt.verify(verifier);
    } catch (ParseException | JOSEException e) {
      return false;
    }
  }
}
```

- [x] **Step 5: Write and implement CookieService test**

Create `CookieServiceTest`:

```java
package com.example.backend.token;

import com.example.backend.config.ApplicationProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServiceTest {
  @Test
  void addAuthenticationCookies_writesHttpOnlyAccessAndRefreshCookies() {
    ApplicationProperties properties = new ApplicationProperties();
    properties.setAccessTokenExpiresIn(900);
    properties.setRefreshTokenExpiresIn(3600);
    properties.setCookieSecure(false);
    properties.setCookieDomain("localhost");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    CookieService cookieService = new CookieService(properties, request, response);
    cookieService.addAuthenticationCookies("access.jwt", "refresh.jwt");

    assertThat(response.getHeaders("Set-Cookie")).anySatisfy(cookie ->
        assertThat(cookie).contains("access_token=access.jwt", "HttpOnly", "SameSite=Lax"));
    assertThat(response.getHeaders("Set-Cookie")).anySatisfy(cookie ->
        assertThat(cookie).contains("refresh_token=refresh.jwt", "HttpOnly", "SameSite=Lax"));
  }

  @Test
  void getCookie_returnsCookieValueByName() {
    ApplicationProperties properties = new ApplicationProperties();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("access_token", "access.jwt"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    CookieService cookieService = new CookieService(properties, request, response);

    assertThat(cookieService.getCookie(CookieService.ACCESS_TOKEN)).isEqualTo("access.jwt");
  }
}
```

Create `CookieService.java`:

```java
package com.example.backend.token;

import com.example.backend.config.ApplicationProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CookieService {
  public static final String ACCESS_TOKEN = "access_token";
  public static final String REFRESH_TOKEN = "refresh_token";

  private final ApplicationProperties applicationProperties;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  public void addAuthenticationCookies(String accessToken, String refreshToken) {
    addCookie(ACCESS_TOKEN, accessToken, applicationProperties.getAccessTokenExpiresIn());
    addCookie(REFRESH_TOKEN, refreshToken, applicationProperties.getRefreshTokenExpiresIn());
  }

  public void removeAuthenticationCookies() {
    removeCookie(ACCESS_TOKEN);
    removeCookie(REFRESH_TOKEN);
  }

  public String getCookie(String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void addCookie(String name, String value, int maxAge) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value == null ? "" : value)
        .maxAge(maxAge)
        .path("/")
        .httpOnly(true)
        .secure(applicationProperties.isCookieSecure())
        .sameSite("Lax");
    if (StringUtils.hasText(applicationProperties.getCookieDomain())) {
      builder.domain(applicationProperties.getCookieDomain());
    }
    response.addHeader("Set-Cookie", builder.build().toString());
  }

  private void removeCookie(String name) {
    addCookie(name, null, 0);
  }
}
```

- [x] **Step 6: Run token and cookie tests**

```bash
cd backend
./mvnw -Dtest=TokenServiceTest,CookieServiceTest test
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.properties backend/src/main/java/com/example/backend/config/ApplicationProperties.java backend/src/main/java/com/example/backend/token backend/src/test/java/com/example/backend/token
git commit -m "feat: add jwt token and auth cookie services"
```

---

## Task 4: Thêm Token Revocation

**Files:**
- Create: `backend/src/main/java/com/example/backend/token/RevokedToken.java`
- Create: `backend/src/main/java/com/example/backend/token/RevokedTokenRepository.java`
- Create: `backend/src/main/java/com/example/backend/token/TokenRevocationService.java`
- Create: `backend/src/main/resources/db/migration/V2__create_revoked_tokens.sql`
- Test: `backend/src/test/java/com/example/backend/token/TokenRevocationServiceTest.java`

- [x] **Step 1: Write failing revocation service test**

Create `TokenRevocationServiceTest`:

```java
package com.example.backend.token;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {
  @Mock RevokedTokenRepository revokedTokenRepository;
  @InjectMocks TokenRevocationService tokenRevocationService;

  @Test
  void revoke_savesTokenWhenJtiDoesNotExist() {
    Instant expiresAt = Instant.now().plusSeconds(60);
    given(revokedTokenRepository.existsById("jti-1")).willReturn(false);

    tokenRevocationService.revoke("jti-1", expiresAt);

    ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
    then(revokedTokenRepository).should().save(captor.capture());
    assertThat(captor.getValue().getJti()).isEqualTo("jti-1");
    assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
  }
}
```

- [x] **Step 2: Run test to verify it fails**

```bash
cd backend
./mvnw -Dtest=TokenRevocationServiceTest test
```

Expected: FAIL because revocation classes do not exist.

- [x] **Step 3: Implement revocation entity/repository/service**

Create `RevokedToken.java` with `String jti` primary key, `Instant revokedAt`, `Instant expiresAt`.

Create repository:

```java
package com.example.backend.token;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
  boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

  @Modifying
  @Query("delete from RevokedToken token where token.expiresAt <= :now")
  void deleteExpired(Instant now);
}
```

Create service:

```java
package com.example.backend.token;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {
  private final RevokedTokenRepository revokedTokenRepository;

  @Transactional
  public void revoke(String jti, Instant expiresAt) {
    if (revokedTokenRepository.existsById(jti)) {
      return;
    }
    revokedTokenRepository.save(new RevokedToken(jti, Instant.now(), expiresAt));
  }

  public boolean isRevoked(String jti) {
    return revokedTokenRepository.existsByJtiAndExpiresAtAfter(jti, Instant.now());
  }

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void cleanupExpired() {
    revokedTokenRepository.deleteExpired(Instant.now());
  }
}
```

- [x] **Step 4: Add migration**

Create `V2__create_revoked_tokens.sql`:

```sql
create table revoked_tokens
(
    jti        varchar(255) not null,
    revoked_at datetime(6) not null,
    expires_at datetime(6) not null,
    primary key (jti)
) engine = InnoDB;

create index idx_revoked_tokens_expires_at on revoked_tokens (expires_at);
```

- [x] **Step 5: Run tests**

```bash
cd backend
./mvnw -Dtest=TokenRevocationServiceTest test
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/token backend/src/test/java/com/example/backend/token/TokenRevocationServiceTest.java backend/src/main/resources/db/migration/V2__create_revoked_tokens.sql
git commit -m "feat: add revoked token tracking"
```

---

## Task 5: Migrate Login/Logout/Refresh Sang JWT Cookies

**Files:**
- Modify: `backend/src/main/java/com/example/backend/auth/service/AuthService.java`
- Modify: `backend/src/main/java/com/example/backend/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/example/backend/auth/AuthServiceTest.java`
- Test: `backend/src/test/java/com/example/backend/auth/AuthControllerTest.java`

- [x] **Step 1: Write failing service test for login cookies**

Create `AuthServiceTest` covering:

```java
@Test
void login_authenticatesUserAndWritesJwtCookies() {
  LoginRequest request = new LoginRequest();
  request.setEmail("user@email.com");
  request.setPassword("Password123");
  Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
  given(authenticationManager.authenticate(any())).willReturn(authentication);
  given(tokenService.generateAccessToken(user)).willReturn("access.jwt");
  given(tokenService.generateRefreshToken(user)).willReturn("refresh.jwt");

  authService.login(httpRequest, httpResponse, request);

  then(cookieService).should().addAuthenticationCookies("access.jwt", "refresh.jwt");
}
```

- [x] **Step 2: Run test to verify it fails**

```bash
cd backend
./mvnw -Dtest=AuthServiceTest test
```

Expected: FAIL because `AuthService.login` still saves session context and does not use token services.

- [x] **Step 3: Update AuthService constructor dependencies**

Inject:

```java
private final TokenService tokenService;
private final CookieService cookieService;
private final TokenRevocationService tokenRevocationService;
```

Keep `AuthenticationManager` because username/password login still uses current `UserDetailsService`.

- [x] **Step 4: Update login implementation**

After successful `authenticationManager.authenticate(token)`, cast principal to `User`, generate access and refresh tokens, and call:

```java
cookieService.addAuthenticationCookies(accessToken, refreshToken);
```

Do not save `SecurityContext` into `HttpSessionSecurityContextRepository`.

- [x] **Step 5: Add refresh endpoint**

Add to `AuthController`:

```java
@PostMapping("/refresh")
public ResponseEntity<?> refresh() {
  authService.refresh();
  return ResponseEntity.ok().build();
}
```

Implement `AuthService.refresh()`:

- read `refresh_token`
- introspect token
- reject revoked/expired token
- load user by email subject
- revoke old access and refresh tokens when claims contain `jti` and expiry
- write new access/refresh cookies

- [x] **Step 6: Update logout**

`logout` should revoke token cookies when present and remove both cookies. It should no longer depend on session logout.

- [x] **Step 7: Run auth tests**

```bash
cd backend
./mvnw -Dtest=AuthServiceTest,AuthControllerTest test
```

Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add backend/src/main/java/com/example/backend/auth backend/src/test/java/com/example/backend/auth
git commit -m "feat: migrate auth endpoints to jwt cookies"
```

---

## Task 6: Migrate Security Filter Chain Sang JWT Resource Server Và Giữ Google OAuth2

**Files:**
- Create: `backend/src/main/java/com/example/backend/auth/CookieBearerTokenResolver.java`
- Create: `backend/src/main/java/com/example/backend/auth/CustomJwtDecoder.java`
- Modify: `backend/src/main/java/com/example/backend/auth/SecurityConfiguration.java`
- Modify: `backend/src/main/java/com/example/backend/auth/Oauth2LoginSuccessHandler.java`
- Test: `backend/src/test/java/com/example/backend/auth/SecurityConfigurationTest.java`

- [x] **Step 1: Write failing resolver test**

Create test asserting cookie token wins over bearer header:

```java
@Test
void resolver_prefersAccessTokenCookieOverAuthorizationHeader() {
  MockHttpServletRequest request = new MockHttpServletRequest();
  request.setCookies(new Cookie("access_token", "cookie.jwt"));
  request.addHeader("Authorization", "Bearer header.jwt");
  CookieService cookieService = mock(CookieService.class);
  given(cookieService.getCookie(CookieService.ACCESS_TOKEN)).willReturn("cookie.jwt");

  CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(cookieService);

  assertThat(resolver.resolve(request)).isEqualTo("cookie.jwt");
}
```

- [x] **Step 2: Implement resolver and decoder**

Implement `CookieBearerTokenResolver` and `CustomJwtDecoder` based on `authentication-service`, adapted to package `com.example.backend.auth`.

- [x] **Step 3: Update SecurityConfiguration**

Keep permitted endpoints:

- `POST /api/users`
- `GET /api/users/verify-email`
- `POST /api/users/forgot-password`
- `PATCH /api/users/reset-password`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/csrf`
- OAuth2 endpoints
- push notification delivery public endpoints
- Swagger endpoints

Configure:

```java
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.decoder(customJwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
    .bearerTokenResolver(cookieBearerTokenResolver)
);
```

Keep:

```java
http.oauth2Login(customizer -> customizer.successHandler(oauth2LoginSuccessHandler));
```

- [x] **Step 4: Update OAuth2 success handler**

After Google success creates/loads user, generate access/refresh tokens, add cookies, then redirect to `app.login-success-url`.

- [x] **Step 5: Run security tests and compile**

```bash
cd backend
./mvnw -Dtest=SecurityConfigurationTest test
./mvnw compile
```

Expected: PASS and compile succeeds.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/auth backend/src/test/java/com/example/backend/auth/SecurityConfigurationTest.java
git commit -m "feat: authenticate requests with jwt resource server"
```

---

## Task 7: Giữ Email Verification Link Và Làm Cứng Token

**Files:**
- Modify: `backend/src/main/java/com/example/backend/users/VerificationCode.java`
- Modify: `backend/src/main/java/com/example/backend/users/service/UserService.java`
- Modify: `backend/src/main/java/com/example/backend/users/jobs/handlers/SendWelcomeEmailJobHandler.java`
- Create: `backend/src/main/resources/db/migration/V3__harden_verification_tokens.sql`
- Test: `backend/src/test/java/com/example/backend/users/UserServiceEmailVerificationTest.java`

- [x] **Step 1: Write failing email verification tests**

Create tests for:

- valid token verifies user and deletes/invalidate token
- expired token returns 400
- reused token returns 400

Use `ApiException` assertions:

```java
assertThatThrownBy(() -> userService.verifyEmail("expired-token"))
    .isInstanceOf(ApiException.class)
    .hasMessageContaining("Verification token is expired");
```

- [x] **Step 2: Run tests to verify failures**

```bash
cd backend
./mvnw -Dtest=UserServiceEmailVerificationTest test
```

Expected: FAIL where expiry/single-use behavior is missing.

- [x] **Step 3: Harden VerificationCode**

Ensure `VerificationCode` has:

```java
private String code;
private Instant expiresAt;
private Instant consumedAt;

public boolean isExpired() {
  return expiresAt != null && expiresAt.isBefore(Instant.now());
}

public boolean isConsumed() {
  return consumedAt != null;
}
```

- [x] **Step 4: Update verifyEmail behavior**

In `UserService.verifyEmail`:

- find token by code
- reject consumed token
- reject expired token
- set user verified
- set `consumedAt`
- save user and token, or delete token after successful verification

- [x] **Step 5: Keep email link UX**

Ensure `SendWelcomeEmailJobHandler` still sends link:

```text
${app.base-url}/api/users/verify-email?token=<token>
```

No 6-digit code UI is introduced in this migration.

- [x] **Step 6: Run tests**

```bash
cd backend
./mvnw -Dtest=UserServiceEmailVerificationTest test
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend/users backend/src/main/resources/db/migration/V3__harden_verification_tokens.sql backend/src/test/java/com/example/backend/users/UserServiceEmailVerificationTest.java
git commit -m "feat: harden email verification tokens"
```

---

## Task 8: Introduce Flyway Baseline Và Chuyển DDL Sang Validate

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/example/backend/BackendApplicationTests.java`

- [x] **Step 1: Add Flyway dependencies**

Add:

```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

- [x] **Step 2: Create baseline migration**

Create `V1__baseline_existing_schema.sql` that matches existing entities:

- `user`
- `verification_code`
- `password_reset_token`
- `user_connected_account`
- uploaded files
- push notification tables
- JobRunr tables remain managed by JobRunr unless already created by app schema

Preserve `bigint` auto-increment IDs for current entities.

- [x] **Step 3: Configure Flyway**

Set:

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.jpa.hibernate.ddl-auto=validate
```

- [x] **Step 4: Run application context test**

```bash
cd backend
./mvnw -Dtest=BackendApplicationTests test
```

Expected: PASS with schema validation against H2 or configured test datasource. If H2-specific schema issues appear, add `backend/src/test/resources/application-test.properties` and activate test profile for context tests.

- [x] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.properties backend/src/main/resources/db/migration backend/src/test
git commit -m "feat: manage backend schema with flyway"
```

---

## Task 9: Update Frontend Auth Client Cho JWT Cookie Và Response Wrapper

**Files:**
- Modify: `frontend/lib/httpClient.ts`
- Modify: `frontend/lib/auth/use-auth.ts`
- Modify: `frontend/models/http/HttpErrorResponse.ts`
- Run generated update: `frontend/models/backend.ts`

- [x] **Step 1: Update error model**

Ensure `HttpErrorResponse` supports:

```ts
export interface HttpErrorResponse {
  message: string;
  status: number;
  errorCode?: number;
  errors?: Record<string, string>;
  generalErrors?: string[];
  success?: boolean;
}
```

- [x] **Step 2: Add API unwrap helper**

In `frontend/lib/httpClient.ts`, configure generated client interceptor so it returns `response.data.data ?? response.data`. This lets old UI continue reading raw data while backend can return wrappers endpoint by endpoint.

- [x] **Step 3: Update auth hook**

In `use-auth.ts`:

- `login` calls `restClient.login(props)`, then `mutate()`
- `logout` calls `restClient.logout()`, then clears SWR and redirects
- `me` remains `GET /api/auth/me`
- optional: if `me` returns 401 and refresh endpoint exists, call `/api/auth/refresh` once then retry `me`

- [x] **Step 4: Regenerate types**

```bash
cd frontend
npm run update-types
```

Expected: `frontend/models/backend.ts` includes `/api/auth/refresh` and any updated response shapes.

- [x] **Step 5: Run frontend checks**

```bash
cd frontend
npm run lint
npm run build
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add frontend/lib frontend/models frontend/app frontend/components
git commit -m "feat: adapt frontend auth to jwt cookie responses"
```

---

## Task 10: Final Verification Và Architecture Review

**Files:**
- Modify if needed: files touched by previous tasks only.
- Test: backend and frontend full verification.

- [x] **Step 1: Run backend full tests**

```bash
cd backend
./mvnw test
```

Expected: PASS.

- [x] **Step 2: Run backend compile**

```bash
cd backend
./mvnw compile
```

Expected: PASS.

- [x] **Step 3: Run frontend lint/build**

```bash
cd frontend
npm run lint
npm run build
```

Expected: PASS.

- [x] **Step 4: Manual smoke test**

Start infra:

```bash
cd infra
docker compose up -d
```

Start backend:

```bash
cd backend
./mvnw spring-boot:run
```

Start frontend:

```bash
cd frontend
npm run dev
```

Verify:

- Register creates user and sends verification email.
- Verification link verifies user once and rejects reuse.
- Login sets `access_token` and `refresh_token` cookies.
- `/api/auth/me` works after page reload.
- Logout removes cookies and revoked token cannot be reused.
- Google OAuth2 login still redirects to login-success and creates JWT cookies.
- Admin users page still loads for admin.

- [x] **Step 5: Request code review**

Spawn a code-reviewer agent or run the project’s review workflow against:

- plan file
- all commits from this migration
- auth/security/token files
- frontend auth client
- migrations

Fix critical issues before merging.

- [x] **Step 6: Commit verification fixes if any**

```bash
git add <files changed by verification fixes>
git commit -m "fix: address auth migration verification issues"
```

Expected: commit only if verification produced fixes.

---

## Self-Review

**Spec coverage:** Plan preserves Google OAuth2, endpoint paths, `Long` IDs, and link-based email verification. It adds JWT cookie auth, refresh token rotation, token revocation, permission model, response wrapper, Flyway, tests, and frontend adaptation.

**Placeholder scan:** No task uses unresolved “TBD” work. Large schema baseline is intentionally described by exact table responsibility because it must be derived from current entities during execution without changing ID strategy.

**Type consistency:** Token service uses `User`, `Role`, `Permission`, `ApplicationProperties`; cookie service uses `access_token` and `refresh_token`; security configuration consumes the same token/cookie services.
