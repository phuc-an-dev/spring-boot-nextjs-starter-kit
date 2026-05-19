package com.example.backend.auth.service;

import com.example.backend.auth.SecurityUtil;
import com.example.backend.auth.data.LoginRequest;
import com.example.backend.token.CookieService;
import com.example.backend.token.TokenRevocationService;
import com.example.backend.token.TokenService;
import com.example.backend.users.User;
import com.example.backend.users.data.UserResponse;
import com.example.backend.users.repository.UserRepository;
import com.example.backend.util.exception.ApiException;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final CookieService cookieService;
  private final TokenRevocationService tokenRevocationService;

  /**
   * Sets the cookie for the user if the username and password are correct
   */
  public void login(HttpServletRequest request,
      HttpServletResponse response,
      LoginRequest body
  ) throws AuthenticationException {
    UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(body.getEmail(), body.getPassword());
    Authentication authentication = authenticationManager.authenticate(token);
    User user = (User) authentication.getPrincipal();
    String accessToken = tokenService.generateAccessToken(user);
    String refreshToken = tokenService.generateRefreshToken(user);
    cookieService.addAuthenticationCookies(accessToken, refreshToken);
  }

  @Transactional
  public UserResponse getSession(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    User user = getAuthenticatedUser(authentication);
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
    return new UserResponse(user, authorities);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    revokeToken(cookieService.getCookie(CookieService.ACCESS_TOKEN));
    revokeToken(cookieService.getCookie(CookieService.REFRESH_TOKEN));
    cookieService.removeAuthenticationCookies();
    SecurityContextHolder.clearContext();
  }

  @Transactional
  public void refresh() {
    String refreshToken = cookieService.getCookie(CookieService.REFRESH_TOKEN);
    if (!tokenService.introspect(refreshToken)) {
      throw unauthorized("Refresh token is invalid or expired");
    }

    JWTClaimsSet claims = tokenService.getClaims(refreshToken);
    if (claims == null || tokenRevocationService.isRevoked(claims.getJWTID())) {
      throw unauthorized("Refresh token is invalid or expired");
    }

    User user = userRepository.findByEmail(claims.getSubject())
        .orElseThrow(() -> unauthorized("Refresh token is invalid or expired"));

    revokeToken(cookieService.getCookie(CookieService.ACCESS_TOKEN));
    revokeClaims(claims);
    cookieService.addAuthenticationCookies(
        tokenService.generateAccessToken(user),
        tokenService.generateRefreshToken(user)
    );
  }

  private User getAuthenticatedUser(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof User user) {
      return user;
    }
    if (principal instanceof Jwt jwt) {
      return userRepository.findByEmail(jwt.getSubject())
          .orElseThrow(() -> unauthorized("User not found"));
    }
    return SecurityUtil.getAuthenticatedUser();
  }

  private void revokeToken(String token) {
    JWTClaimsSet claims = tokenService.getClaims(token);
    revokeClaims(claims);
  }

  private void revokeClaims(JWTClaimsSet claims) {
    if (claims == null || claims.getJWTID() == null || claims.getExpirationTime() == null) {
      return;
    }
    Date expirationTime = claims.getExpirationTime();
    tokenRevocationService.revoke(claims.getJWTID(), expirationTime.toInstant());
  }

  private ApiException unauthorized(String message) {
    return ApiException.builder()
        .status(401)
        .message(message)
        .build();
  }
}
