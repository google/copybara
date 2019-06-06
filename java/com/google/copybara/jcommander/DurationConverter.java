/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import com.google.common.primitives.Ints;
import java.time.Duration;

/**
 * Converts strings like 10s/10m/10h to a Duration
 */
public class DurationConverter implements IStringConverter<Duration> {

  @Override
  public Duration convert(String value) {
    if (value.length() < 2) {
      return durationException(value);
    }

    Integer num = Ints.tryParse(value.substring(0, value.length() - 1));
    if (num == null || num < 0) {
      return durationException(value);
    }
    char unit = value.charAt(value.length() - 1);
    switch (unit) {
      case 's':
        return Duration.ofSeconds(num);
      case 'm':
        return Duration.ofMinutes(num);
      case 'h':
        return Duration.ofHours(num);
      case 'd':
        return Duration.ofDays(num);
      default:
        return durationException(value);
    }
  }

  private Duration durationException(String value) {
    throw new ParameterException(String.format(
        "Invalid value for duration '%s', valid value examples: 10s, 10m, 10h or 10d", value));
  }
}
