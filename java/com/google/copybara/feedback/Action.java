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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** An action that will be executed in a feedback workflow or on finish hook */
@SkylarkModule(
    name = "feedback.action",
    doc = "An action that will be executed in a feedback workflow or on_finish hook",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    documented = false)
public interface Action extends StarlarkValue {

  void run(SkylarkContext<?> context) throws ValidationException, RepoException;

  String getName();

  /** Returns a key-value list of the options the action was instantiated with. */
  ImmutableSetMultimap<String, String> describe();
}
