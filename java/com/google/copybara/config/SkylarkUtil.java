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
import com.google.copybara.starlark.StarlarkUtil;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;

/**
 * Utilities for dealing with Skylark parameter objects and converting them to Java ones.
 * TODO(malcon): Move methods to StarlarkUtil and keep this one for config specific ones
 * (And rename to StarlarkConfigUtil)
 */
public final class SkylarkUtil {

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
  @FormatMethod
  public static void check(boolean condition, @FormatString String format, Object... args)
      throws EvalException {
    // TODO(malcon): Remove this method and inline this call:
    StarlarkUtil.check(condition, format, args);
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
  public static <K, V> Dict<K, StarlarkList<V>> castOfSequence(
      Object x, Class<K> keyType, Class<V> nestedValueType, String what) throws EvalException {
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
    Dict<K, StarlarkList<V>> res = (Dict<K, StarlarkList<V>>) x;
    return res;
  }

  /** Casts a Dict nested in another Dict */
  public static <K, W, V> Dict<K, Dict<W, V>> castOfDictNestedInDict(
      Object x, Class<K> keyType, Class<W> nestedKeyType, Class<V> nestedValueType, String what)
      throws EvalException {
    Preconditions.checkNotNull(x);
    if (!(x instanceof Dict)) {
      throw Starlark.errorf("got %s for '%s', want dict", Starlark.type(x), what);
    }
    for (Entry<?, ?> e : ((Map<?, ?>) x).entrySet()) {
      if (!keyType.isAssignableFrom(e.getKey().getClass())) {
        throw Starlark.errorf(
            "Key not assignable. Wanted %s, got %s",
            Starlark.classType(keyType), Starlark.type(e.getKey()));
      }
      for (Map.Entry<?, ?> n : ((Map<?, ?>) e.getValue()).entrySet()) {
        if (!nestedKeyType.isAssignableFrom(n.getKey().getClass())) {
          throw Starlark.errorf(
              "Nested key type not assignable. Wanted %s, got %s",
              Starlark.classType(nestedKeyType), Starlark.type(n.getKey()));
        }
        if (!nestedValueType.isAssignableFrom(n.getValue().getClass())) {
          throw Starlark.errorf(
              "Nested value type not assignable. Wanted %s, got %s",
              Starlark.classType(nestedValueType), Starlark.type(n.getValue()));
        }
      }
    }
    @SuppressWarnings("unchecked") // safe
    Dict<K, Dict<W, V>> res = (Dict<K, Dict<W, V>>) x;
    return res;
  }
}
