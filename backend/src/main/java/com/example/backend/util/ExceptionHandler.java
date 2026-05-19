package com.example.backend.util;

import com.example.backend.util.exception.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Map<String, String> errors = new HashMap<>();
    List<String> generalErrors = new ArrayList<>();
    ex.getBindingResult().getAllErrors().forEach((error) -> {
      if (error instanceof FieldError fieldErr) {
        String fieldName = fieldErr.getField();
        String errorMessage = fieldErr.getDefaultMessage();
        errors.put(fieldName, errorMessage);
      } else {
        generalErrors.add(error.getDefaultMessage());
      }
    });

    ErrorApiResponse<?> response = new ErrorApiResponse<>(
        "Unprocessable entity",
        HttpStatus.UNPROCESSABLE_ENTITY.value(),
        ErrorCode.VALIDATION_ERROR.getCode()
    );
    response.setErrors(errors);
    response.setGeneralErrors(generalErrors);

    return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorApiResponse<?>> handleApiException(ApiException e) {
    log.info("Handling ApiException: {}", e.getMessage());
    var response = new ErrorApiResponse<>(
        e.getMessage(),
        e.getStatus(),
        errorCodeForStatus(e.getStatus())
    );
    response.setErrors(e.getErrors());
    return new ResponseEntity<>(response, HttpStatus.valueOf(e.getStatus()));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorApiResponse<?>> handleException(BadCredentialsException e) {
    log.info("Handling BadCredentialsException: {}", e.getMessage());
    var response = new ErrorApiResponse<>(
        e.getMessage(),
        HttpStatus.UNAUTHORIZED.value(),
        ErrorCode.UNAUTHORIZED.getCode()
    );
    return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ErrorApiResponse<?>> handleException(AuthorizationDeniedException e) {
    log.info("Handling AuthorizationDeniedException: {}", e.getMessage());
    var response = new ErrorApiResponse<>(
        e.getMessage(),
        HttpStatus.FORBIDDEN.value(),
        ErrorCode.PERMISSION_DENIED.getCode()
    );
    return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorApiResponse<?>> handleException(Exception e) {
    log.error("Unhandled exception", e);
    var response = new ErrorApiResponse<>(
        ErrorCode.UNKNOWN_ERROR.getMessage(),
        ErrorCode.UNKNOWN_ERROR.getHttpStatus().value(),
        ErrorCode.UNKNOWN_ERROR.getCode()
    );
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private int errorCodeForStatus(int status) {
    if (status == HttpStatus.UNAUTHORIZED.value()) {
      return ErrorCode.UNAUTHORIZED.getCode();
    }
    if (status == HttpStatus.FORBIDDEN.value()) {
      return ErrorCode.PERMISSION_DENIED.getCode();
    }
    if (status == HttpStatus.NOT_FOUND.value()) {
      return ErrorCode.ENTITY_NOT_FOUND.getCode();
    }
    return ErrorCode.UNKNOWN_ERROR.getCode();
  }
}
