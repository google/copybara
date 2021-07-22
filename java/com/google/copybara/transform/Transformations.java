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

package com.google.copybara.transform;

import com.google.copybara.Transformation;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkThread;

/**
 * This class consists exclusively of static methods that operate on or return {@link
 * Transformation}s.
 */
public final class Transformations {

  private Transformations() {}

  /**
   * Cast a Starlark callable to a {@link Transformation}. If the input is already a {@link
   * Transformation}, it is returned unchanged.
   *
   * <p>Many functions in Copybara's Starlark API require {@link Transformation}s as input. In
   * nearly all cases, the user may instead choose to provide an ordinary Starlark function. This
   * utility method converts those functions into objects with the necessary Java type.
   *
   * @param element the object to cast to a Transformation
   * @param description the name of the field for which this object was provided by the user as a
   *     parameter, used for error messages
   * @param printHandler the {@link StarlarkThread.PrintHandler} to use for the thread which runs
   *     this Starlark function
   */
  public static Transformation toTransformation(
      Object element, String description, StarlarkThread.PrintHandler printHandler)
      throws EvalException {
    if (element instanceof StarlarkCallable) {
      return new SkylarkTransformation((StarlarkCallable) element, Dict.empty(), printHandler);
    }
    if (element instanceof Transformation) {
      return (Transformation) element;
    }
    throw Starlark.errorf(
        "for '%s' element, got %s, want function or transformation",
        description, Starlark.type(element));
  }
}
