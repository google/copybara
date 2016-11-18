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

package com.google.copybara;

/**
 * The task type that can be performed by Copybara for a single execution.
 */
public enum Subcommand {
  /**
   * Executes the migration for the given config.
   */
  MIGRATE,
  /**
   * Validates that the configuration is correct.
   */
  VALIDATE,
  /**
   * Reads the last migrated reference in the origin and destination.
   */
  INFO
}
