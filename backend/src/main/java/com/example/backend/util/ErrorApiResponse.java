package com.example.backend.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorApiResponse<T> extends ApiResponse {
  private int status;
  private int errorCode;
  private Map<String, String> errors;
  private List<String> generalErrors;
  private T details;

  public ErrorApiResponse(String message, int status, int errorCode) {
    super(message, false);
    this.status = status;
    this.errorCode = errorCode;
  }
}
