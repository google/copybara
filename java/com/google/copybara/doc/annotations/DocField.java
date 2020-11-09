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

package com.google.copybara.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field documentation for a {@link DocElement} type.
 * TODO(malcon): Rename to DocEnum or similar (only used in enums...)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface DocField {

  String description();

  boolean required() default true;

  String defaultValue() default "none";

  boolean undocumented() default false;

  boolean deprecated() default false;

  /**
   * Use when the elements of a list fields are always of the same type so that we can avoid
   * using !FieldClass.
   */
  Class<?> listType() default Object.class;
}
