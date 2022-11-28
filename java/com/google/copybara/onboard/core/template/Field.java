/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.onboard.core.template;

import java.util.Objects;

/** An object that describes a Field for a {@code TemplateConfigGenerator} */
public class Field {

  private final String name;
  private final Location location;
  private final boolean required;

  private Field(String name, Location location, boolean required) {
    this.name = name;
    this.location = location;
    this.required = required;
  }

  public static Field required(String name) {
    return new Field(name, Location.NAMED, true);
  }

  public static Field requiredKeyword(String name) {
    return new Field(name, Location.KEYWORD, true);
  }

  public static Field optional(String name) {
    return new Field(name, Location.KEYWORD, true);
  }

  public String name() {
    return name;
  }

  public boolean required() {
    return required;
  }

  public Location location() {
    return location;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Field)) {
      return false;
    }
    Field field = (Field) o;
    return Objects.equals(name, field.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  /** Type of parameter */
  enum FieldClass {
    STRING,
    INT,
    STARLARK
  }

  /** Locations for parameters */
  enum Location {
    NAMED,
    KEYWORD,
  }
}
