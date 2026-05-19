package com.example.backend.auth;

import com.example.backend.token.CookieService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CookieBearerTokenResolver implements BearerTokenResolver {
  private final CookieService cookieService;
  private final DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    String cookieToken = cookieService.getCookie(CookieService.ACCESS_TOKEN);
    if (StringUtils.hasText(cookieToken)) {
      return cookieToken;
    }
    return defaultResolver.resolve(request);
  }
}
