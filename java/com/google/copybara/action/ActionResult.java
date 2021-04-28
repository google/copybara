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

package com.google.copybara.action;

import static com.google.copybara.action.ActionResult.Result.ERROR;
import static com.google.copybara.action.ActionResult.Result.NO_OP;
import static com.google.copybara.action.ActionResult.Result.SUCCESS;

import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** Represents the result returned by an {@link Action}. */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "dynamic.action_result",
    doc = "Result objects created by actions to tell Copybara what happened.")
public class ActionResult implements StarlarkValue {

  private final Result result;
  @Nullable private final String msg;

  private ActionResult(Result result, @Nullable String msg) {
    this.result = result;
    this.msg = msg;
  }

  public enum Result {
    SUCCESS,
    ERROR,
    NO_OP
  }

  public static ActionResult success() {
    return new ActionResult(SUCCESS, /*msg*/ null);
  }

  public static ActionResult error(String msg) {
    return new ActionResult(ERROR, msg);
  }

  public static ActionResult noop(@Nullable String msg) {
    return new ActionResult(NO_OP, msg);
  }

  public Result getResult() {
    return result;
  }

  @StarlarkMethod(
      name = "result",
      doc = "The result of this action",
      structField = true
  )
  public String getResultForSkylark() {
    return result.name();
  }

  @StarlarkMethod(
      name = "msg",
      doc = "The message associated with the result",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getMsg() {
    return msg;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("result", result)
        .add("msg", msg)
        .toString();
  }
}
