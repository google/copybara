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
import com.google.common.collect.Iterables;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Starlark;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    check(location, !Strings.isNullOrEmpty(value), "Invalid empty field '%s'.", name);
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

  public static String mapLabels(Function<String, ? extends Collection<String>> labelsMapper,
      String template) throws ValidationException {
    return mapLabels(labelsMapper, template, null);
  }

  public static String mapLabels(Function<String, ? extends Collection<String>> labelsMapper,
      String template, String fieldName)
      throws ValidationException {
    try {
      return new LabelTemplate(template).resolve(labelsMapper.andThen(
          e ->  e == null ? null : Iterables.getFirst(e, null)));
    } catch (LabelNotFoundException e) {
      throw new ValidationException(
          String.format("Cannot find '%s' label for template '%s' defined in field '%s'",
              e.getLabel(), template, fieldName), e);
    }
  }

  /**
   * convertStringList converts a Starlark sequence value (such as a list or tuple) to a Java list
   * of strings. The result is a new, mutable copy. It throws EvalException if x is not a Starlark
   * iterable or if any of its elements are not strings. The message argument is prefixed to any
   * error message.
   */
  public static List<String> convertStringList(Object x, String message) throws EvalException {
    if (!(x instanceof SkylarkList)) {
      throw new EvalException(
          null, String.format("%s: got %s, want sequence", message, EvalUtils.getDataTypeName(x)));
    }

    ArrayList<String> result = new ArrayList<>();
    for (Object elem : (SkylarkList<?>) x) {
      if (!(elem instanceof String)) {
        throw new EvalException(
            null, String.format("%s: at index #%d, got %s, want string",
                message, result.size(), EvalUtils.getDataTypeName(elem)));
      }
      result.add((String) elem);
    }
    return result;
  }

  /**
   * convertStringMap converts a Starlark dict value to a Java map of strings to strings. The result
   * is a new, mutable copy. It throws EvalException if x is not a Starlark dict or if any of its
   * keys or values are not strings. The message argument is prefixed to any error message.
   */
  public static Map<String, String> convertStringMap(Object x, String message)
      throws EvalException {
    // TODO(adonovan): support mappings other than dict.
    if (!(x instanceof SkylarkDict)) {
      throw new EvalException(
          null, String.format("%s: got %s, want dict", message, EvalUtils.getDataTypeName(x)));
    }
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<?, ?> e : ((SkylarkDict<?, ?>) x).entrySet()) {
      if (!(e.getKey() instanceof String)) {
        throw new EvalException(
            null,
            String.format(
                "%s: in dict key, got %s, want string",
                message, EvalUtils.getDataTypeName(e.getKey())));
      }
      if (!(e.getValue() instanceof String)) {
        throw new EvalException(
            null,
            String.format(
                "%s: in value for dict key '%s', got %s, want string",
                message, e.getKey(), EvalUtils.getDataTypeName(e.getValue())));
      }
      result.put((String) e.getKey(), (String) e.getValue());
    }
    return result;
  }

  /**
   * convertOptionalString converts a Starlark optional string value (string or None) to a Java
   * String reference, which may be null. It throws ClassCastException if called with any other
   * value.
   */
  @Nullable
  public static String convertOptionalString(Object x) {
    return x == Starlark.NONE ? null : (String) x;
  }
}
