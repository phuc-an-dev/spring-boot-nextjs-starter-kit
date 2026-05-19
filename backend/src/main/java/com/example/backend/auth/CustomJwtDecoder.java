package com.example.backend.auth;

import com.example.backend.token.TokenRevocationService;
import com.example.backend.token.TokenService;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {
  private final TokenService tokenService;
  private final TokenRevocationService tokenRevocationService;

  @Override
  public Jwt decode(String token) throws JwtException {
    if (!tokenService.introspect(token)) {
      throw new JwtException("Invalid JWT");
    }
    JWTClaimsSet claims = tokenService.getClaims(token);
    if (claims == null) {
      throw new JwtException("Invalid JWT claims");
    }
    String jwtId = claims.getJWTID();
    if (StringUtils.hasText(jwtId) && tokenRevocationService.isRevoked(jwtId)) {
      throw new JwtException("JWT has been revoked");
    }
    return Jwt.withTokenValue(token)
        .headers(headers -> headers.put("alg", "HS256"))
        .claims(jwtClaims -> jwtClaims.putAll(claims.getClaims()))
        .subject(claims.getSubject())
        .issuedAt(toInstant(claims.getIssueTime()))
        .expiresAt(toInstant(claims.getExpirationTime()))
        .build();
  }

  private Instant toInstant(Date date) {
    return date == null ? null : date.toInstant();
  }
}
