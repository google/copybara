/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
   * Execution resulted in no-op, which means that no changes were made in the destination.
   */
  NO_OP(4),
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

  public static ExitCode forCode(int code) {
    for (ExitCode value : ExitCode.values()) {
      if (value.getCode() == code) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid exit code: " + code);
  }
}
