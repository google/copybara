/*
 * Copyright (C) 2023 Google Inc.
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


package com.google.copybara.toml;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.StarlarkDateTimeModule;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.exception.ValidationException;
import java.time.OffsetDateTime;
import java.util.Map.Entry;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Represents parsed TOML content.
 */
@StarlarkBuiltin(name = "TomlContent", doc = "Object containing parsed TOML values.")
public class TomlContent implements StarlarkValue {
  private final TomlParseResult parsedToml;

  public TomlContent(TomlParseResult parsedToml) {
    this.parsedToml = parsedToml;
  }

  @StarlarkMethod(
      name = "get",
      doc = "Retrieve the value from the parsed TOML for the given key. "
          + "If the key is not defined, this will return None.",
      parameters = {
          @Param(
              name = "key",
              doc = "The dotted key expression",
              allowedTypes = {@ParamType(type = String.class)},
              named = true),
      })
  @Example(
      title = "Get the value for a key",
      before = "Pass in the name of the key. This will return the value.",
      code = "TomlContent.get(\"foo\")")
  public Object get(String key) throws ValidationException, EvalException {
    try {
      return convertToStarlarkValue(parsedToml.get(key));
    } catch (IllegalArgumentException | TomlInvalidTypeException e) {
      throw new EvalException(
          String.format("There was an error retrieving the value for the given key %s", key), e);
    }
  }

  /**
   * Converts the value to an object that can cast to a StarlarkValue.
   *
   * <p>This method return type is "Object" because Strings and Booleans are
   * valid Starlark values, despite them not implementing StarlarkValue.
   */
  private Object convertToStarlarkValue(Object value) throws ValidationException, EvalException {
    Object starlarkValue;

    if (value instanceof OffsetDateTime) {
      starlarkValue = new StarlarkDateTimeModule().createFromEpochSeconds(
          StarlarkInt.of(((OffsetDateTime) value).toEpochSecond()),
          ((OffsetDateTime) value).getOffset().toString());
    } else if (value instanceof TomlArray) {
      ImmutableList.Builder<Object> builder = ImmutableList.builder();
      for (Object item : ((TomlArray) value).toList()) {
        builder.add(convertToStarlarkValue(item));
      }
      starlarkValue = StarlarkList.immutableCopyOf(builder.build());
    } else if (value instanceof TomlTable) {
      ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder();
      for (Entry entry : ((TomlTable) value).entrySet()) {
        builder.put(entry.getKey(), convertToStarlarkValue(entry.getValue()));
      }
      starlarkValue = Dict.immutableCopyOf(builder.build());
    } else {
      starlarkValue = Starlark.fromJava(value, Mutability.IMMUTABLE);
    }

    return starlarkValue;
  }
}
