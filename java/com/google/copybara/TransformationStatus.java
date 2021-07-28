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

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.copybara.exception.VoidOperationException;
import com.google.copybara.util.console.Console;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** The status of a Transformation that was just run. Either a 'success' or a 'no-op'. */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = TransformationStatus.STARLARK_TYPE_NAME,
    doc = "The status of a Transformation that was just run. Either a 'success' or a 'no-op'.")
public final class TransformationStatus implements StarlarkValue {

  public static final String STARLARK_TYPE_NAME = "transformation_status";
  private final boolean isSuccess;
  private final String message;

  private TransformationStatus(boolean success, String message) {
    this.isSuccess = success;
    this.message = message;
  }

  public static TransformationStatus success() {
    return new TransformationStatus(true, null);
  }

  public static TransformationStatus noop(String message) {
    return new TransformationStatus(false, message);
  }

  @StarlarkMethod(
      name = "is_success",
      doc = "Whether this status has the value SUCCESS.",
      structField = true)
  public boolean isSuccess() {
    return isSuccess;
  }

  @StarlarkMethod(
      name = "is_noop",
      doc = "Whether this status has the value NO-OP.",
      structField = true)
  public boolean isNoop() {
    return !isSuccess;
  }

  public String getMessage() {
    Preconditions.checkState(isNoop(), "Can only get message if the Transform was a no-op.");
    return this.message;
  }

  public void warn(Console console) {
      Preconditions.checkState(isNoop(), "Can only warn if the Transform was a no-op.");
      console.warn("NOOP: " + this.message);
  }

  public void throwException(Console console, boolean ignoreNoop) throws VoidOperationException {
      Preconditions.checkState(isNoop(), "Can only throw if the Transform was a no-op.");
    if (ignoreNoop) {
      this.warn(console);
      return;
    }
    throw new VoidOperationException(
        String.format("%s. Use --ignore-noop if you want to ignore this error", this.message));
  }
}
