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
