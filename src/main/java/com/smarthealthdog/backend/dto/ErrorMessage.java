package com.smarthealthdog.backend.dto;

import java.util.List;

public class ErrorMessage {
  private List<String> code;
  private List<String> descriptions;

  public ErrorMessage(List<String> code, List<String> descriptions) {
    this.code = code;
    this.descriptions = descriptions;
  }

  public List<String> getCode() {
    return code;
  }

  public List<String> getDescriptions() {
    return descriptions;
  }
}
