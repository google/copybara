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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
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
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;

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
    if (Starlark.isNullOrNone(obj)) {
      return defaultValue;
    }
    return (T) obj; // wildly unsound cast!
  }

  /**
   * Converts a string to the corresponding enum or fail if invalid value.
   *
   * @param fieldName name of the field to convert
   * @param value value to convert
   * @param enumType the type class of the enum to use for conversion
   * @param <T> the enum class
   */
  public static <T extends Enum<T>> T stringToEnum(
      String fieldName, String value, Class<T> enumType) throws EvalException {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw Starlark.errorf(
          "Invalid value '%s' for field '%s'. Valid values are: %s",
          value, fieldName, Joiner.on(", ").join(enumType.getEnumConstants()));
    }
  }

  /** Checks that a mandatory string field is not empty. */
  public static String checkNotEmpty(@Nullable String value, String name) throws EvalException {
    check(!Strings.isNullOrEmpty(value), "Invalid empty field '%s'.", name);
    return value;
  }

  /** Checks a condition or throw {@link EvalException}. */
   /** Checks a condition or throw {@link EvalException}. */
  @FormatMethod
  public static void check(boolean condition, @FormatString String format, Object... args)
      throws EvalException {
    if (!condition) {
      throw Starlark.errorf(format, args);
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
    // TODO(adonovan): replace all calls to this function with:
    //  Sequence.cast(x, String.class, message).
    // But beware its result should not be modified.
    if (!(x instanceof Sequence)) {
      throw Starlark.errorf("%s: got %s, want sequence", message, Starlark.type(x));
    }

    ArrayList<String> result = new ArrayList<>();
    for (Object elem : (Sequence<?>) x) {
      if (!(elem instanceof String)) {
        throw Starlark.errorf(
            "%s: at index #%d, got %s, want string", message, result.size(), Starlark.type(elem));
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
    // TODO(adonovan): replace all calls to this function with:
    //    Dict.cast(x, String.class, String.class, message)
    // and fix up tests. Beware: its result is not to be modified.
    if (!(x instanceof Dict)) {
      throw Starlark.errorf("%s: got %s, want dict", message, Starlark.type(x));
    }
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<?, ?> e : ((Dict<?, ?>) x).entrySet()) {
      if (!(e.getKey() instanceof String)) {
        throw Starlark.errorf(
            "%s: in dict key, got %s, want string", message, Starlark.type(e.getKey()));
      }
      if (!(e.getValue() instanceof String)) {
        throw Starlark.errorf(
            "%s: in value for dict key '%s', got %s, want string",
            message, e.getKey(), Starlark.type(e.getValue()));
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

  /** Casts nested sequence type in Dict */
  public static <K, V> Dict<K, V> castOfSequence(
      Object x, Class<K> keyType, Class<K> nestedValueType, String what) throws EvalException {
    Preconditions.checkNotNull(x);
    if (!(x instanceof Dict)) {
      throw Starlark.errorf("got %s for '%s', want dict", Starlark.type(x), what);
    }

    for (Map.Entry<?, ?> e : ((Map<?, ?>) x).entrySet()) {
      if (!keyType.isAssignableFrom(e.getKey().getClass())
          && Sequence.cast(e.getValue(), nestedValueType, what) != null) {
        throw Starlark.errorf(
            "got dict<%s, %s> for '%s', want dict<%s, Sequence<%s>>",
            Starlark.type(e.getKey()),
            Starlark.type(e.getValue()),
            what,
            Starlark.classType(keyType),
            Starlark.classType(nestedValueType));
      }
    }

    @SuppressWarnings("unchecked") // safe
    Dict<K, V> res = (Dict<K, V>) x;
    return res;
  }
}
