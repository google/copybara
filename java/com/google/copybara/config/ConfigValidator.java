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

import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.Message;
import java.util.List;

/**
 * Validates Copybara {@link Config}s and returns a list of {@link Message}s.
 *
 * <p>Implementations of this interface should not throw exceptions for validation errors.
 */
public interface ConfigValidator {

  default List<Message> validate(Config config, String migrationName) {
    ImmutableList.Builder<Message> messages = ImmutableList.builder();
    if (config.getMigrations().isEmpty()) {
      messages.add(Message.error("At least one migration is required."));
    }
    return messages.build();
  }
}
