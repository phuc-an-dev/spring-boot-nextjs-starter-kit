package com.example.backend.auth;

import com.example.backend.token.CookieService;
import com.example.backend.token.TokenRevocationService;
import com.example.backend.token.TokenService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SecurityConfigurationTest {
  @Test
  void resolver_prefersAccessTokenCookieOverAuthorizationHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(CookieService.ACCESS_TOKEN, "cookie.jwt"));
    request.addHeader("Authorization", "Bearer header.jwt");
    CookieService cookieService = mock(CookieService.class);
    given(cookieService.getCookie(CookieService.ACCESS_TOKEN)).willReturn("cookie.jwt");

    CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(cookieService);

    assertThat(resolver.resolve(request)).isEqualTo("cookie.jwt");
  }

  @Test
  void decoder_rejectsRevokedJwt() {
    String token = "access.jwt";
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID("revoked-jti")
        .subject("user@email.com")
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        .claim("scope", "ROLE_USER user:self:read")
        .build();
    TokenService tokenService = mock(TokenService.class);
    TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
    given(tokenService.introspect(token)).willReturn(true);
    given(tokenService.getClaims(token)).willReturn(claims);
    given(tokenRevocationService.isRevoked("revoked-jti")).willReturn(true);

    CustomJwtDecoder decoder = new CustomJwtDecoder(tokenService, tokenRevocationService);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }
}
