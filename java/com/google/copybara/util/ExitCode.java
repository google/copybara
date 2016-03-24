package com.google.copybara.util;

/**
 * Exit codes to be used by the application
 */
public enum ExitCode {
  SUCCESS(0),
  COMMAND_LINE_ERROR(1),
  ENVIRONMENT_ERROR(30),
  INTERNAL_ERROR(31);

  private int code;

  ExitCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

}
