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

package com.google.copybara.starlark;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;

/** Utilities for dealing with Starlark language. */
public final class StarlarkUtil {

  /** Checks a condition or throw {@link EvalException}. */
  @FormatMethod
  public static void check(boolean condition, @FormatString String format, Object... args)
      throws EvalException {
    if (!condition) {
      throw Starlark.errorf(format, args);
    }
  }

private StarlarkUtil() {}
}
