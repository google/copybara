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

package com.google.copybara.config;

/**
 * Validates Copybara {@link Config}s and returns a {@link ValidationResult}.
 *
 * <p>Implementations of this interface should not throw exceptions for validation errors.
 */
public interface ConfigValidator {

  default ValidationResult validate(Config config, String migrationName) {
    ValidationResult.Builder resultBuilder = new ValidationResult.Builder();
    checkAtLeastOneMigration(resultBuilder, config);
    return resultBuilder.build();
  }

  default void checkAtLeastOneMigration(ValidationResult.Builder resultBuilder, Config config) {
    if (config.getMigrations().isEmpty()) {
      resultBuilder.error("At least one migration is required.");
    }
  }
}
