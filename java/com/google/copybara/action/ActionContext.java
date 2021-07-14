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

package com.google.copybara.action;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.SkylarkContext;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.console.Console;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;

/**
 * A StarlarkContext for running Actions.
 */
public abstract class ActionContext<T extends SkylarkContext<T>> implements SkylarkContext<T>,
    StarlarkValue {

  protected final List<DestinationEffect> newDestinationEffects = new ArrayList<>();
  protected final Action action;
  protected final SkylarkConsole console;
  protected final ImmutableMap<String, String> labels;
  private final Dict<?, ?> params;
  private ActionResult actionResult;

  public ActionContext(Action action, SkylarkConsole console, ImmutableMap<String, String> labels,
      Dict<?, ?> params) {
    this.action = checkNotNull(action);
    this.console = checkNotNull(console);
    this.labels = checkNotNull(labels);
    this.params = checkNotNull(params);
  }

  @StarlarkMethod(
      name = "action_name",
      doc = "The name of the current action.",
      structField = true)
  public String getActionName() {
    return action.getName();
  }

  @StarlarkMethod(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  @StarlarkMethod(
      name = "params",
      doc = "Parameters for the function if created with" + " core.action",
      structField = true)
  public Dict<?, ?> getParams() {
    return params;
  }

  @StarlarkMethod(name = "success", doc = "Returns a successful action result.")
  public ActionResult success() {
    return ActionResult.success();
  }

  @StarlarkMethod(
      name = "noop",
      doc = "Returns a no op action result with an optional message.",
      parameters = {
          @Param(
              name = "msg",
              allowedTypes = {
                  @ParamType(type = String.class),
                  @ParamType(type = NoneType.class),
              },
              doc = "The no op message",
              defaultValue = "None"),
      })
  public ActionResult noop(Object noopMsg) {
    return ActionResult.noop(convertFromNoneable(noopMsg, /*defaultMsg*/ null));
  }

  @StarlarkMethod(
      name = "error",
      doc = "Returns an error action result.",
      parameters = {
          @Param(name = "msg", doc = "The error message"),
      })
  public ActionResult error(String errorMsg) {
    return ActionResult.error(errorMsg);
  }

  /**
   * Return the new {@link DestinationEffect}s created by this context.
   */
  public ImmutableList<DestinationEffect> getNewDestinationEffects() {
    return ImmutableList.copyOf(newDestinationEffects);
  }

  @StarlarkMethod(
      name = "cli_labels",
      doc = "Access labels that a user passes through flag '--labels'. "
          + "For example: --labels=foo:value1,bar:value2. Then it can access in this way:"
          + "cli_labels['foo'].",
      structField = true)
  public Dict<String, String> getCliLabels() {
    return Dict.copyOf(null, labels);
  }

  @StarlarkMethod(
      name = "record_effect",
      doc = "Records an effect of the current action.",
      parameters = {
          @Param(name = "summary", doc = "The summary of this effect", named = true),
          @Param(
              name = "origin_refs",
              allowedTypes = {
                  @ParamType(type = Sequence.class, generic1 = OriginRef.class),
              },
              doc = "The origin refs",
              named = true),
          @Param(name = "destination_ref", doc = "The destination ref", named = true),
          @Param(
              name = "errors",
              allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
              defaultValue = "[]",
              doc = "An optional list of errors",
              named = true),
          @Param(
              name = "type",
              doc =
                  "The type of migration effect:<br>"
                      + "<ul>"
                      + "<li><b>'CREATED'</b>: A new review or change was created.</li>"
                      + "<li><b>'UPDATED'</b>: An existing review or change was updated.</li>"
                      + "<li><b>'NOOP'</b>: The change was a noop.</li>"
                      + "<li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relative"
                      + "to an existing pending change.</li>"
                      + "<li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because "
                      + "the change doesn't have enough approvals.</li>"
                      + "<li><b>'ERROR'</b>: A user attributable error happened that prevented "
                      + "the destination from creating/updating the change. "
                      + "<li><b>'STARTED'</b>: The initial effect of a migration that depends on a "
                      + "previous one. This allows to have 'dependant' migrations defined by users.\n"
                      + "An example of this: a workflow migrates code from a Gerrit review to a "
                      + "GitHub PR, and a feedback migration migrates the test results from a CI in "
                      + "GitHub back to the Gerrit change.\n"
                      + "This effect would be created on the former one.</li>"
                      + "</ul>",
              defaultValue = "\"UPDATED\"",
              named = true)
      })
  public void recordEffect(
      String summary,
      Sequence<?> originRefs, // <OriginRef>
      DestinationRef destinationRef,
      Sequence<?> errors, // <String>
      String typeStr)
      throws EvalException {
    DestinationEffect.Type type =
        SkylarkUtil.stringToEnum("type", typeStr, DestinationEffect.Type.class);
    newDestinationEffects.add(
        new DestinationEffect(
            type,
            summary,
            Sequence.cast(originRefs, OriginRef.class, "origin_refs"),
            destinationRef,
            Sequence.cast(errors, String.class, "errors")));
  }

  @Override
  public void onFinish(Object result, SkylarkContext<T> context) throws ValidationException {
    checkCondition(
        result != null,
        "Actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: None", action.getName());
    checkCondition(result instanceof ActionResult,
        "Actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: %s", action.getName(), result);
    this.actionResult = (ActionResult) result;
    switch (actionResult.getResult()) {
      case ERROR:
        console.errorFmt(
            "Action '%s' returned error: %s", action.getName(), actionResult.getMsg());
        break;
      case NO_OP:
        console.infoFmt(
            "Action '%s' returned noop: %s", action.getName(), actionResult.getMsg());
        break;
      case SUCCESS:
        console.infoFmt("Action '%s' returned success", action.getName());
        break;
    }
    // Populate effects registered in the action context. This is required because SkylarkAction
    // makes a copy of the context to inject the parameters, but that instance is not visible from
    // the caller
    this.newDestinationEffects.addAll(((ActionContext<T>) context).newDestinationEffects);
  }

  public ActionResult getActionResult() {
    return actionResult;
  }
}
