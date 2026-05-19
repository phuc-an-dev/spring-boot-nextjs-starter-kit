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
