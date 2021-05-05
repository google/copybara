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

import com.google.common.collect.ImmutableMap;
import com.google.copybara.Endpoint;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionContext;
import com.google.copybara.transform.SkylarkConsole;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;

/**
 * A FeedbackContext is an ActionContext that has access to the origin & destination and
 * is used for feedback mechanisms (core.feedback & core.workflow 'after_migration' hooks)
 */
@SuppressWarnings("unused")
public abstract class FeedbackContext extends ActionContext<FeedbackContext> {

  FeedbackContext(
      Action currentAction,
      SkylarkConsole console,
      ImmutableMap<String, String> labels,
      Dict<?, ?> params) {
    super(currentAction, console, labels, params);
  }

  @StarlarkMethod(name = "origin", doc = "An object representing the origin. Can be used to"
      + " query about the ref or modifying the origin state", structField = true)
  public abstract Endpoint getOrigin() throws EvalException;

  @StarlarkMethod(name = "destination", doc = "An object representing the destination. Can be used"
      + " to query or modify the destination state", structField = true)
  public abstract Endpoint getDestination() throws EvalException;
}
