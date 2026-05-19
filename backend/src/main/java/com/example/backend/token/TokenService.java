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
