/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.approval;

import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;

/** An approvals validator that is provided by the origin */
public interface ApprovalsProvider {

  /**
   * Given a list of changes, return a list of changes that have approvals
   *
   * @param changes changes to be verified with the existing approvals.
   * @param console console, in case some message need to be printed
   * @throws RepoException if access to the origin system fails because of being unavailable, server
   *     error, etc.
   * @throws ValidationException if failure is attributable to the user setup (e.g. permission
   *     errors, etc.)
   */
  ApprovalsResult computeApprovals(ImmutableList<ChangeWithApprovals> changes, Console console)
      throws RepoException, ValidationException;

  /**
   * An object containing the approvals found for set of changes.
   *
   * <p>The purpose of this class is to make it easier to migrate to attestations in the future. For
   * example, storing general information about the source.
   */
  class ApprovalsResult {
    private final ImmutableList<ChangeWithApprovals> changes;

    public ApprovalsResult(ImmutableList<ChangeWithApprovals> changes) {
      this.changes = changes;
    }

    /**
     * List of changes with its corresponding approvals. Must be the complete list of changes, with
     * or without any approval.
     */
    public ImmutableList<ChangeWithApprovals> getChanges() {
      return changes;
    }
  }
}
