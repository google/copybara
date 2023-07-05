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

import static com.google.common.base.Preconditions.checkArgument;
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
  private final boolean inferOnly;

  private Input(String name, String description, @Nullable T defaultValue, Class<T> type,
      Converter<? extends T> converter, boolean inferOnly) {
    this.name = checkNotNull(name);
    this.description = checkNotNull(description);
    this.defaultValue = defaultValue;
    this.type = checkNotNull(type);
    this.converter = converter;
    this.inferOnly = inferOnly;
  }

  /**
   * Create an Input object. This factory method ensures that there is only one instance of the
   * same Input for the same name. equals/hashcode is not implemented on purpose.
   */
  public static <T> Input<T> create(String name, String description, @Nullable T defaultValue,
      Class<T> type, Converter<? extends T> converter) {

    Input<T> result = new Input<>(name, description, defaultValue, type, converter, false);
    if (INPUTS.put(name, result) != null) {
      throw new IllegalStateException("Two calls for the same Input name '" + name + "'");
    }
    return result;
  }

  /**
   * Create an Input object that can only be inferred, never asked to the user.
   */
  public static <T> Input<T> createInfer(String name, String description, @Nullable T defaultValue,
      Class<T> type) {

    Input<T> result = new Input<>(name, description, defaultValue, type,
        (value, resolver) -> {
          throw new CannotConvertException(
              String.format(
                  "Input of type '%s' (%s) could not be inferred. Cannot convert user input: %s",
                  name, description, value));
        }, true);
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

  /**
   * Validate that the value is of the Input type and cast it to the generic T of InputProvider
   * interface.
   *
   * <p>This class can be used mainly to validate that the type is the correct one and a convenient
   * and safe way of doing the cast required by the interface.
   */
  // The cast is safe as we are checking with the input type.
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  public <V> V asValue(T value) {
    if (value == null) {
      throw new IllegalStateException("Null value for " + this);
    }
    checkArgument(
        type.isAssignableFrom(value.getClass()),
        "Incorrect type for Input %s: expecting %s type but got %s",
        name,
        type.getName(),
        value.getClass().getName());
    return (V) value;
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

  public boolean inferOnly() {
    return inferOnly;
  }
}
