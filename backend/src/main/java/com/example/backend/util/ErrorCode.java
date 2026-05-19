package com.example.backend.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  UNKNOWN_ERROR(99, "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR),
  VALIDATION_ERROR(102, "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),
  ENTITY_NOT_FOUND(103, "Entity not found", HttpStatus.NOT_FOUND),
  UNAUTHORIZED(1007, "Unauthorized", HttpStatus.UNAUTHORIZED),
  PERMISSION_DENIED(1005, "Permission denied", HttpStatus.FORBIDDEN),
  REFRESH_TOKEN_INVALID(204, "Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED);

  private final int code;
  private final String message;
  private final HttpStatus httpStatus;
}
