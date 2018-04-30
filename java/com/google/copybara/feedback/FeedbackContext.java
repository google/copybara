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

package com.google.copybara.feedback;

import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.copybara.Endpoint;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import javax.annotation.Nullable;

/**
 * Gives access to the feedback migration information and utilities.
 */
@SuppressWarnings("unused")
@SkylarkModule(name = "feedback.context",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Gives access to the feedback migration information and utilities.",
    documented = false)
public class FeedbackContext implements SkylarkContext<FeedbackContext> {

  private final Feedback feedback;
  private final Action currentAction;
  @Nullable private final String ref;
  private final Console console;
  private final SkylarkDict params;

  FeedbackContext(Feedback feedback, Action currentAction, @Nullable String ref, Console console) {
    this(feedback, currentAction, ref, console, SkylarkDict.empty());
  }

  private FeedbackContext(
      Feedback feedback, Action currentAction, @Nullable String ref, Console console,
      SkylarkDict params) {
    this.feedback = Preconditions.checkNotNull(feedback);
    this.currentAction = Preconditions.checkNotNull(currentAction);
    this.ref = ref;
    this.console = Preconditions.checkNotNull(console);
    this.params = Preconditions.checkNotNull(params);
  }

  @SkylarkCallable(name = "origin", doc = "An object representing the origin. Can be used to"
      + " query about the ref or modifying the origin state", structField = true)
  public Endpoint getOrigin() {
    return feedback.getTrigger().getEndpoint();
  }

  @SkylarkCallable(name = "destination", doc = "An object representing the destination. Can be used"
      + " to query or modify the destination state", structField = true)
  public Endpoint getDestination() {
    return feedback.getDestination();
  }

  @SkylarkCallable(
      name = "feedback_name",
      doc = "The name of the Feedback migration calling this action.",
      structField = true)
  public String getFeedbackName() {
    return feedback.getName();
  }

  @SkylarkCallable(
      name = "action_name",
      doc = "The name of the current action.",
      structField = true)
  public String getActionName() {
    return currentAction.getName();
  }

  @SkylarkCallable(name = "ref", doc = "A string representation of the entity that triggered the"
      + " event", structField = true, allowReturnNones = true)
  public String getRef() {
    return ref;
  }

  @SkylarkCallable(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  @SkylarkCallable(name = "params", doc = "Parameters for the function if created with"
      + " core.dynamic_feedback", structField = true)
  public SkylarkDict getParams() {
    return params;
  }

  @Override
  public FeedbackContext withParams(SkylarkDict params) {
    return new FeedbackContext(feedback, currentAction, ref, console, params);
  }

  @Override
  public void validateResult(Object result) throws ValidationException {
    checkCondition(
        result != null,
        "Feedback actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: None", currentAction.getName());
    checkCondition(result instanceof ActionResult,
        "Feedback actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: %s", currentAction.getName(), result);
    ActionResult actionResult = (ActionResult) result;
    switch (actionResult.getResult()) {
      case ERROR:
        console.errorFmt(
            "Action '%s' returned error: %s", currentAction.getName(), actionResult.getMsg());
        break;
      case NO_OP:
        console.infoFmt(
            "Action '%s' returned noop: %s", currentAction.getName(), actionResult.getMsg());
        break;
      case SUCCESS:
        console.infoFmt("Action '%s' returned success", currentAction.getName());
        break;
    }
    // TODO(danielromero): Populate effects
  }

  @SkylarkCallable(name = "success", doc = "Returns a successful action result.")
  public ActionResult success() {
    return ActionResult.success();
  }

  @SkylarkCallable(name = "noop", doc = "Returns a no op action result with an optional message.",
      parameters = {
          @Param(
              name = "msg",
              type = String.class,
              doc = "The no op message",
              defaultValue = "None",
              noneable = true),
      })
  public ActionResult noop(Object noopMsg) {
    return ActionResult.noop(convertFromNoneable(noopMsg, /*defaultMsg*/ null));
  }

  @SkylarkCallable(name = "error", doc = "Returns an error action result.",
      parameters = {
          @Param(
              name = "msg",
              type = String.class,
              doc = "The error message"
          ),
      })
  public ActionResult noop(String errorMsg) {
    return ActionResult.error(errorMsg);
  }
}
