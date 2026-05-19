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
  void generateAccessToken_containsSubjectRoleAndPermissionScope() throws Exception {
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
