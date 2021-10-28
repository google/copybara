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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.git.github.api.GitHubEventType;

/**
 * A simple pair to express GitHub Events with arbitrary subtypes (Status, CheckRun)
 */
@AutoValue
public abstract class EventTrigger {
  public abstract GitHubEventType type();

  public abstract ImmutableSet<String> subtypes();


  public static EventTrigger create(GitHubEventType type, Iterable<String> subtypes) {
    return new AutoValue_EventTrigger(type, ImmutableSet.copyOf(subtypes));
  }
}
