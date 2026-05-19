package com.example.backend.auth;

import com.example.backend.auth.controller.AuthController;
import com.example.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class AuthControllerTest {
  @Test
  void refresh_callsAuthServiceRefresh() {
    AuthService authService = mock(AuthService.class);
    AuthController authController = new AuthController(authService);

    ResponseEntity<?> response = authController.refresh();

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    then(authService).should().refresh();
  }
}
