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
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Endpoint;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionResult;
import com.google.copybara.transform.SkylarkConsole;

import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;

import javax.annotation.Nullable;

/** Skylark context for feedback migrations. */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "feedback.context",
    doc =
        "Gives access to the feedback migration information and utilities. This context is a "
            + "concrete implementation for feedback migrations.")
public class FeedbackMigrationContext extends FeedbackContext {

  private final Feedback feedback;
  private final ImmutableList<String> refs;

  FeedbackMigrationContext(
      Feedback feedback, Action currentAction,  ImmutableMap<String, String> labels,
      ImmutableList<String> refs, SkylarkConsole console) {
    this(feedback, currentAction, labels, refs, console, Dict.empty());
  }

  private FeedbackMigrationContext(
      Feedback feedback,
      Action currentAction,
      ImmutableMap<String, String> labels,
      ImmutableList<String> refs,
      SkylarkConsole console,
      Dict<?, ?> params) {
    super(currentAction, console, labels, params);
    this.feedback = Preconditions.checkNotNull(feedback);
    this.refs = ImmutableList.copyOf(refs);
  }

  @Override
  public Endpoint getOrigin() {
    return feedback.getTrigger().getEndpoint().withConsole(getConsole());
  }

  @Override
  public Endpoint getDestination() {
    return feedback.getDestination().withConsole(getConsole());
  }

  @StarlarkMethod(
      name = "feedback_name",
      doc = "The name of the Feedback migration calling this action.",
      structField = true)
  public String getFeedbackName() {
    return feedback.getName();
  }

  @StarlarkMethod(
      name = "refs",
      doc =
          "A list containing string representations of the entities " + "that triggered the event",
      structField = true)
  public Sequence<String> getRefs() {
    return StarlarkList.immutableCopyOf(refs);
  }

  @Override
  public FeedbackContext withParams(Dict<?, ?> params) {
    return new FeedbackMigrationContext(feedback, action, labels, refs, console, params);
  }
}
