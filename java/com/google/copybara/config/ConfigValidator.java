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
import com.google.copybara.Config;
import com.google.copybara.util.console.Message;
import java.util.List;

/**
 * A default validator of Copybara {@link Config}s.
 */
public class ConfigValidator {

  public List<Message> validate(Config config, String migrationName) {
    ImmutableList.Builder<Message> messages = ImmutableList.builder();
    if (config.getMigrations().isEmpty()) {
      messages.add(Message.error("At least one migration is required."));
    }
    return messages.build();
  }
}
