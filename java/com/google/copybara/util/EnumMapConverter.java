/*
 * Copyright (C) 2026 Google LLC.
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

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/** A JCommander converter for maps of enums. */
public abstract class EnumMapConverter<T extends Enum<T>>
    implements IStringConverter<ImmutableMap<String, T>> {

  private final Class<T> enumClass;

  protected EnumMapConverter(Class<T> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public ImmutableMap<String, T> convert(String value) {
    Map<String, String> split =
        Splitter.on(';').omitEmptyStrings().trimResults().withKeyValueSeparator(':').split(value);
    ImmutableMap.Builder<String, T> builder = ImmutableMap.builder();
    for (Entry<String, String> entry : split.entrySet()) {
      try {
        builder.put(
            entry.getKey(), Enum.valueOf(enumClass, entry.getValue().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        throw new ParameterException(
            String.format(
                "Invalid value '%s' for field '%s'. Valid values are: %s",
                entry.getValue(),
                entry.getKey(),
                Joiner.on(", ").join(enumClass.getEnumConstants())),
            e);
      }
    }
    return builder.buildOrThrow();
  }
}
