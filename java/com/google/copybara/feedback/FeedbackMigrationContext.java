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
import com.google.common.collect.ImmutableList;
import com.google.copybara.Endpoint;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import javax.annotation.Nullable;

/** Skylark context for feedback migrations. */
@SuppressWarnings("unused")
@SkylarkModule(
    name = "feedback.context",
    category = SkylarkModuleCategory.BUILTIN,
    doc =
        "Gives access to the feedback migration information and utilities. This context is a "
            + "concrete implementation for feedback migrations.")
public class FeedbackMigrationContext extends FeedbackContext implements SkylarkValue {

  private final Feedback feedback;
  private final ImmutableList<String> refs;
  @Nullable private ActionResult actionResult;


  FeedbackMigrationContext(
      Feedback feedback, Action currentAction, ImmutableList<String> refs, SkylarkConsole console) {
    this(feedback, currentAction, refs, console, SkylarkDict.empty());
  }

  private FeedbackMigrationContext(
      Feedback feedback,
      Action currentAction,
      ImmutableList<String> refs,
      SkylarkConsole console,
      SkylarkDict<?, ?> params) {
    super(currentAction, console, params);
    this.feedback = Preconditions.checkNotNull(feedback);
    this.refs = ImmutableList.copyOf(refs);
  }

  @Override
  public Endpoint getOrigin() {
    return feedback.getTrigger().getEndpoint().withConsole(console);
  }

  @Override
  public Endpoint getDestination() {
    return feedback.getDestination().withConsole(console);
  }

  @SkylarkCallable(
      name = "feedback_name",
      doc = "The name of the Feedback migration calling this action.",
      structField = true)
  public String getFeedbackName() {
    return feedback.getName();
  }

  @SkylarkCallable(name = "refs", doc = "A list containing string representations of the entities "
      + "that triggered the event", structField = true)
  public SkylarkList<String> getRefs() {
    return SkylarkList.createImmutable(refs);
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
  public ActionResult error(String errorMsg) {
    return ActionResult.error(errorMsg);
  }

  @Override
  public FeedbackContext withParams(SkylarkDict<?, ?> params) {
    return new FeedbackMigrationContext(feedback, currentAction, refs, console, params);
  }

  @Override
  public void onFinish(Object result, SkylarkContext<?> actionContext) throws ValidationException {
    checkCondition(
        result != null,
        "Feedback actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: None", currentAction.getName());
    checkCondition(result instanceof ActionResult,
        "Feedback actions must return a result via built-in functions: success(), "
            + "error(), noop() return, but '%s' returned: %s", currentAction.getName(), result);
    this.actionResult = (ActionResult) result;
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
    // Populate effects registered in the action context. This is required because SkylarkAction
    // makes a copy of the context to inject the parameters, but that instance is not visible from
    // the caller
    this.newDestinationEffects.addAll(((FeedbackContext) actionContext).newDestinationEffects);
  }

  ActionResult getActionResult() {
    Preconditions.checkNotNull(actionResult, "Action result should be set. This is a bug.");
    return actionResult;
  }
}
