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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/**
 * Actions are Starlark functions that receive a context object (that is different depending
 *  on where it is used) that expose an API to implement custom logic in Starlark. */
@StarlarkBuiltin(
    name = "dynamic.action",
    doc = "An action is an Starlark piece of code that does part of a migration. It is used"
        + "to define the logic of migration for feedback workflow, on_finish hooks, git.mirror,"
        + " etc.",
    documented = false)
public interface Action extends StarlarkValue {

  <T extends SkylarkContext<T>> void run(ActionContext<T> context)
      throws ValidationException, RepoException;

  String getName();

  /** Returns a key-value list of the options the action was instantiated with. */
  ImmutableSetMultimap<String, String> describe();
}
