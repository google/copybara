/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkDict;

/**
 * A context object that can be enhanced with Skylark information.
 */
public interface SkylarkContext<T> {

  /**
   * Create a copy instance with Skylark function parameters.
   */
  T withParams(SkylarkDict<?, ?> params);

  /**
   * Validates the result returned by an {@link com.google.copybara.feedback.Action}.
   */
  default void validateResult(Object result, BaseFunction function) throws ValidationException {
      checkCondition(
          result == null || result.equals(Runtime.NONE),
          "Function '%s' cannot return any result but returned: %s", function.getName(), result);
  }
}
