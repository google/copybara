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
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract context for feedback migrations.
 */
@SuppressWarnings("unused")
public abstract class FeedbackContext implements SkylarkContext<FeedbackContext> {

  final Action currentAction;
  final SkylarkConsole console;
  final List<DestinationEffect> newDestinationEffects = new ArrayList<>();

  private final SkylarkDict<?, ?> params;

  FeedbackContext(Action currentAction, SkylarkConsole console, SkylarkDict<?, ?> params) {
    this.currentAction = Preconditions.checkNotNull(currentAction);
    this.console = Preconditions.checkNotNull(console);
    this.params = Preconditions.checkNotNull(params);
  }

  @SkylarkCallable(name = "origin", doc = "An object representing the origin. Can be used to"
      + " query about the ref or modifying the origin state", structField = true)
  public abstract Endpoint getOrigin() throws EvalException;

  @SkylarkCallable(name = "destination", doc = "An object representing the destination. Can be used"
      + " to query or modify the destination state", structField = true)
  public abstract Endpoint getDestination() throws EvalException;

  @SkylarkCallable(
      name = "action_name",
      doc = "The name of the current action.",
      structField = true)
  public String getActionName() {
    return currentAction.getName();
  }

  @SkylarkCallable(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  @SkylarkCallable(name = "params", doc = "Parameters for the function if created with"
      + " core.dynamic_feedback", structField = true)
  public SkylarkDict<?, ?> getParams() {
    return params;
  }

  @SkylarkCallable(
      name = "record_effect",
      doc = "Records an effect of the current action.",
      parameters = {
          @Param(name = "summary", type = String.class, doc = "The summary of this effect"),
          @Param(
              name = "origin_refs",
              type = SkylarkList.class,
              generic1 = OriginRef.class,
              doc = "The origin refs"),
          @Param(name = "destination_ref", type = DestinationRef.class, doc = "The destination ref"),
          @Param(
              name = "errors",
              type = SkylarkList.class,
              generic1 = String.class,
              defaultValue = "[]",
              doc = "An optional list of errors"),
          @Param(
              name = "type",
              type = String.class,
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
                      + "</ul>",
              defaultValue = "\"UPDATED\""
          )
      })
  public void recordEffect(
      String summary, List<OriginRef> originRefs, DestinationRef destinationRef,
      List<String> errors, String typeStr) throws EvalException {
    DestinationEffect.Type type =
        SkylarkUtil.stringToEnum(null, "type", typeStr, DestinationEffect.Type.class);
    newDestinationEffects.add(
        new DestinationEffect(type, summary, originRefs, destinationRef, errors));
  }

  /**
   * Return the new {@link DestinationEffect}s created by this context.
   */
  public ImmutableList<DestinationEffect> getNewDestinationEffects() {
    return ImmutableList.copyOf(newDestinationEffects);
  }


}
