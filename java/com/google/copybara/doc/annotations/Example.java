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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to associate an example snippet with a configuration element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Repeatable(Examples.class)
public @interface Example {

  /**
   * Title of the example.
   */
  String title();

  /**
   * Description to show before the snippet. For example {@code "Move the root to foo directory"}
   */
  String before();

  /**
   * The code of the snippet. For example {@code "core.move('', 'foo')"}
   */
  String code();

  /**
   * Description to show after the code snippet.
   */
  String after() default "";

  /**
   * If the test should check for an existing variable in {@link #code()}. Otherwise it is assumed
   * to be an expression.
   */
  String testExistingVariable() default "";
}
