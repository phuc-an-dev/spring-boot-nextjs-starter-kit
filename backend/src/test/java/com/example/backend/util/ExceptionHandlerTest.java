package com.example.backend.util;

import com.example.backend.util.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlerTest {
  private final ExceptionHandler exceptionHandler = new ExceptionHandler();

  @Test
  void handleApiException_returnsStandardErrorResponse() {
    ApiException exception = ApiException.builder()
        .status(404)
        .message("User not found")
        .build();

    ResponseEntity<ErrorApiResponse<?>> response = exceptionHandler.handleApiException(exception);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).isEqualTo("User not found");
    assertThat(response.getBody().getStatus()).isEqualTo(404);
  }
}
