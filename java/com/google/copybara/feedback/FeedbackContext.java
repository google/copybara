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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.Endpoint;
import com.google.copybara.SkylarkContext;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.console.Console;
import java.util.ArrayList;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

/** Abstract context for feedback migrations. */
@SuppressWarnings("unused")
public abstract class FeedbackContext implements SkylarkContext<FeedbackContext>, StarlarkValue {

  final Action currentAction;
  final SkylarkConsole console;
  final List<DestinationEffect> newDestinationEffects = new ArrayList<>();

  private final Dict<?, ?> params;

  FeedbackContext(Action currentAction, SkylarkConsole console, Dict<?, ?> params) {
    this.currentAction = Preconditions.checkNotNull(currentAction);
    this.console = Preconditions.checkNotNull(console);
    this.params = Preconditions.checkNotNull(params);
  }

  @StarlarkMethod(name = "origin", doc = "An object representing the origin. Can be used to"
      + " query about the ref or modifying the origin state", structField = true)
  public abstract Endpoint getOrigin() throws EvalException;

  @StarlarkMethod(name = "destination", doc = "An object representing the destination. Can be used"
      + " to query or modify the destination state", structField = true)
  public abstract Endpoint getDestination() throws EvalException;

  @StarlarkMethod(
      name = "action_name",
      doc = "The name of the current action.",
      structField = true)
  public String getActionName() {
    return currentAction.getName();
  }

  @StarlarkMethod(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  @StarlarkMethod(
      name = "params",
      doc = "Parameters for the function if created with" + " core.dynamic_feedback",
      structField = true)
  public Dict<?, ?> getParams() {
    return params;
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

  /**
   * Return the new {@link DestinationEffect}s created by this context.
   */
  public ImmutableList<DestinationEffect> getNewDestinationEffects() {
    return ImmutableList.copyOf(newDestinationEffects);
  }
}
