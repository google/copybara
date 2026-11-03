/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.onboard.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Decides whether a generated config that failed validation can be fixed without the user. */
public interface RepairPolicy {

  /**
   * A generated config that failed validation.
   *
   * @param archetype the archetype the config was generated from
   * @param answers the user's survey answers, keyed by the field name used in the prompt
   * @param config the config that failed validation
   * @param errors the validation errors
   */
  record Request(
      String archetype,
      ImmutableMap<String, String> answers,
      String config,
      ImmutableList<String> errors) {}

  /**
   * Whether the errors can be fixed without the user.
   *
   * @param reason why, shown to the user if they need to fix it
   * @param userAction what the user should do, if they need to fix it
   */
  record Classification(boolean fixable, String reason, String userAction) {}

  /**
   * A fixed config, or an empty one if the user needs to fix it after all.
   *
   * @param reason what was fixed, or why the user needs to fix it
   */
  record Repair(Optional<String> config, String reason) {}

  /** Returns whether the errors in {@code request} can be fixed without the user. */
  Classification classify(Request request) throws LlmException;

  /** Returns a fixed config for {@code request}. */
  Repair repair(Request request) throws LlmException;
}
