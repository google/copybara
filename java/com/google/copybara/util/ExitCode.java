// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

/**
 * Exit codes to be used by the application
 */
public enum ExitCode {
  /**
   * Everything went well and the migration was successful.
   */
  SUCCESS(0),
  /**
   * An error parsing the command line. For example wrong arguments/options.
   */
  COMMAND_LINE_ERROR(1),
  /**
   * An error in the configuration, flags values or in general an error attributable to the user
   */
  CONFIGURATION_ERROR(2),
  /**
   * An error that happened during repository manipulation.
   */
  REPOSITORY_ERROR(3),
  /**
   * Execution was interrupted.
   */
  INTERRUPTED(8),
  /**
   * Any error transient or permanent due to the environment (Error accessing the network,
   * filesystem errors, etc.)
   */
  ENVIRONMENT_ERROR(30),
  /**
   * Any error that was unexpected. This would be a Copybara bug.
   */
  INTERNAL_ERROR(31);

  private final int code;

  ExitCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

}
