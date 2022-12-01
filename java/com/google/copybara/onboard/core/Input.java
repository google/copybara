/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A {@code Input} object represents a named object that can be populated by calling a
 * {@code InputProvider} or by asking the user in the console to give a value
 */
public final class Input<T> {

  private static final Map<String, Input<?>> INPUTS = new HashMap<>();
  private final String name;
  private final String description;

  @Nullable
  private final T defaultValue;
  private final Class<T> type;
  private final Converter<? extends T> converter;

  private Input(String name, String description, @Nullable T defaultValue, Class<T> type,
      Converter<? extends T> converter) {
    this.name = checkNotNull(name);
    this.description = checkNotNull(description);
    this.defaultValue = defaultValue;
    this.type = checkNotNull(type);
    this.converter = converter;
  }

  /**
   * Create an Input object. This factory method ensures that there is only one instance of the
   * same Input for the same name. equals/hashcode is not implemented on purpose.
   */
  public static <T> Input<T> create(String name, String description, @Nullable T defaultValue,
      Class<T> type, Converter<? extends T> converter) {

    Input<T> result = new Input<>(name, description, defaultValue, type, converter);
    if (INPUTS.put(name, result) != null) {
      throw new IllegalStateException("Two calls for the same Input name '" + name + "'");
    }
    return result;
  }

  /** Name of the Input object */
  public String name() {
    return name;
  }

  /** Description of the Input object. Can be used to give context to users */
  public String description() {
    return description;
  }

  /**
   * Default value if any. We keep the default value as a String on purpose, so that it can
   * be represented when printed to ask the user for a value. As the Converter might not be
   * bidirectional.
   */
  @Nullable
  public T defaultValue() {
    return defaultValue;
  }

  public Class<T> type() {
    return type;
  }

  public T convert(String value, InputProviderResolver resolver) throws CannotConvertException {
    return converter.convert(value, resolver);
  }

  /**
   * Return all registered inputs
   */
  public static Map<String, Input<?>> registeredInputs() {
    return ImmutableMap.copyOf(INPUTS);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("description", description)
        .add("defaultValue", defaultValue)
        .add("type", type)
        .add("converter", converter)
        .toString();
  }
}
