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

package com.google.copybara.git;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.collect.ImmutableList;
import com.google.copybara.DestinationEffect;
import com.google.copybara.SkylarkContext;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionResult;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.console.Console;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

public class GitMirrorContext implements SkylarkContext<GitMirrorContext>, StarlarkValue {

  final Action currentAction;
  private final SkylarkConsole console;
  private boolean force;
  private final Dict<?, ?> params;
  private List<String> sourceRefs;
  private List<Refspec> refspecs;
  private String originUrl;
  private String destinationUrl;
  @Nullable
  private ActionResult actionResult;

  public GitMirrorContext(Action currentAction, SkylarkConsole console, List<String> sourceRefs,
      List<Refspec> refspecs, GitRepository repo, String originUrl, String destinationUrl,
      boolean force) {
    this(currentAction, console, sourceRefs, refspecs, originUrl, destinationUrl, force,
        Dict.empty());
  }

  public GitMirrorContext(Action currentAction, SkylarkConsole console, List<String> sourceRefs,
      List<Refspec> refspecs, String originUrl, String destinationUrl, boolean force,
      Dict<?, ?> params) {
    this.currentAction = checkNotNull(currentAction);
    this.console = checkNotNull(console);
    this.sourceRefs = sourceRefs;
    this.refspecs = checkNotNull(refspecs);
    this.originUrl = originUrl;
    this.destinationUrl = destinationUrl;
    this.force = force;
    this.params = checkNotNull(params);
  }

  @Override
  public GitMirrorContext withParams(Dict<?, ?> params) {
    return new GitMirrorContext(
        currentAction, console, sourceRefs, refspecs, originUrl, destinationUrl, force, params);
  }

  @Override
  public void onFinish(Object result, SkylarkContext<?> actionContext) throws ValidationException {
    checkCondition(
        result != null,
        "Mirror actions must return a result via built-in functions: success(), "
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
  }

  @StarlarkMethod(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }


  @StarlarkMethod(
      name = "params",
      doc = "Parameters for the function if created with git.mirror_function",
      structField = true)
  public Dict<?, ?> getParams() {
    return params;
  }

  @StarlarkMethod(name = "success", doc = "Returns a successful action result.")
  public ActionResult success() {
    return ActionResult.success();
  }

  // TODO(b/185208243): move to a shared super class.
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

  @StarlarkMethod(
      name = "refs",
      doc =
          "A list containing string representations of the entities " + "that triggered the event",
      structField = true)
  public Sequence<String> getRefs() {
    return StarlarkList.immutableCopyOf(sourceRefs);
  }

  // TODO(b/185208243): Implement effects
  public Collection<? extends DestinationEffect> getNewDestinationEffects() {
    return ImmutableList.of();
  }

  public ActionResult getActionResult() {
    return checkNotNull(actionResult, "method called before onFinish()");
  }
}
