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

package com.google.copybara.onboard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Predicate;
import com.google.copybara.onboard.ConfigTemplate.FieldClass;
import com.google.copybara.onboard.ConfigTemplate.Location;

/** An object that describes a OptionalField for a {@link ConfigTemplate} */
@AutoValue
public abstract class OptionalField {

  public static OptionalField create(
      String name,
      FieldClass fieldClass,
      Location location,
      String helpText,
      Predicate<String> predicate,
      String defaultValue) {
    return new com.google.copybara.onboard.AutoValue_OptionalField(
        name, fieldClass, location, helpText, predicate, defaultValue);
  }

  abstract String name();

  abstract FieldClass fieldClass();

  abstract Location location();

  abstract String helpText();

  abstract Predicate<String> predicate();

  abstract String defaultValue();
}
