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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.LabelFinder;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utilities for dealing with Skylark parameter objects and converting them to Java ones.
 */
public final class SkylarkUtil {
  private static final Pattern LABEL_VAR = Pattern
      .compile("\\$\\{(" + LabelFinder.VALID_LABEL.pattern() + ")}");

  private SkylarkUtil() {
  }

  /**
   * Converts an object that can be the NoneType to the actual object if it is not
   * or returns the default value if none.
   */
  @SuppressWarnings("unchecked")
  public static <T> T convertFromNoneable(Object obj, @Nullable T defaultValue) {
    if (EvalUtils.isNullOrNone(obj)) {
      return defaultValue;
    }
    return (T) obj;
  }

  /**
   * Converts a string to the corresponding enum or fail if invalid value.
   *
   * @param location location of the skylark element requesting the conversion
   * @param fieldName name of the field to convert
   * @param value value to convert
   * @param enumType the type class of the enum to use for conversion
   * @param <T> the enum class
   */
  public static <T extends Enum<T>> T stringToEnum(Location location, String fieldName,
      String value, Class<T> enumType) throws EvalException {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new EvalException(location,
          String.format("Invalid value '%s' for field '%s'. Valid values are: %s", value, fieldName,
              Joiner.on(", ").join(enumType.getEnumConstants())));
    }
  }

  /**
   * Checks that a mandatory string field is not empty.
   */
  public static String checkNotEmpty(@Nullable String value, String name, Location location)
      throws EvalException {
    if (Strings.isNullOrEmpty(value)) {
      throw new EvalException(location, String.format("Invalid empty field '%s'.", name));
    }
    return value;
  }

  /**
   * Checks a condition or throw {@link EvalException}.
   */
  public static void check(Location location, boolean condition, String errorFmt,
      Object... params)
      throws EvalException {
    if (!condition) {
      throw new EvalException(location, String.format(errorFmt, (Object[]) params));
    }
  }

  /**
   * A utility for resolving list of string labels to values
   */
  public static ImmutableList<String> mapLabels(
      Function<String, ? extends Collection<String>> labelsMapper, List<String> list) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (String element : list) {
      Matcher matcher = LABEL_VAR.matcher(element);
      if (!matcher.matches()) {
        result.add(element);
        continue;
      }
      String label = matcher.group(1);
      Collection<String> values = labelsMapper.apply(label);
      if (values == null) {
        continue;
      }
      result.addAll(Objects.requireNonNull(values));
    }
    return result.build();
  }
}
