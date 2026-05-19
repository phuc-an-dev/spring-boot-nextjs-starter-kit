package com.example.backend.auth;

import com.example.backend.auth.data.LoginRequest;
import com.example.backend.auth.service.AuthService;
import com.example.backend.token.CookieService;
import com.example.backend.token.TokenRevocationService;
import com.example.backend.token.TokenService;
import com.example.backend.users.Role;
import com.example.backend.users.User;
import com.example.backend.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository userRepository;
  @Mock AuthenticationManager authenticationManager;
  @Mock TokenService tokenService;
  @Mock CookieService cookieService;
  @Mock TokenRevocationService tokenRevocationService;
  @Mock HttpServletRequest httpRequest;
  @Mock HttpServletResponse httpResponse;

  @Test
  void login_authenticatesUserAndWritesJwtCookies() {
    User user = new User();
    user.setRole(Role.USER);
    LoginRequest request = new LoginRequest();
    request.setEmail("user@email.com");
    request.setPassword("Password123");
    Authentication authentication = new UsernamePasswordAuthenticationToken(
        user,
        null,
        user.getAuthorities()
    );
    given(authenticationManager.authenticate(any())).willReturn(authentication);
    given(tokenService.generateAccessToken(user)).willReturn("access.jwt");
    given(tokenService.generateRefreshToken(user)).willReturn("refresh.jwt");
    AuthService authService = new AuthService(
        userRepository,
        authenticationManager,
        tokenService,
        cookieService,
        tokenRevocationService
    );

    authService.login(httpRequest, httpResponse, request);

    then(cookieService).should().addAuthenticationCookies("access.jwt", "refresh.jwt");
  }
}
