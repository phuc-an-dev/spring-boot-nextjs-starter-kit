package com.example.backend.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessApiResponse<T> extends ApiResponse {
  private T data;

  public SuccessApiResponse() {
    super("Success", true);
  }

  public SuccessApiResponse(T data) {
    super("Success", true);
    this.data = data;
  }

  public SuccessApiResponse(String message, T data) {
    super(message, true);
    this.data = data;
  }
}
