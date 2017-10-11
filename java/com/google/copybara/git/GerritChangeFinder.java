/*
 * Copyright (C) 2016 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.copybara.RepoException;
import java.util.Optional;

/** Generic abstract class for querying Gerrit. */
public interface GerritChangeFinder {

  /**
   * Information about a single gerrit change.
   */
  class GerritChange {
    private final String changeId;
    private final String status;

    public GerritChange(String changeId, String status) {
      this.changeId = Preconditions.checkNotNull(changeId);
      this.status = Preconditions.checkNotNull(status);
    }

    /**
     * @return ChangeId of the retrieved change
     */
    public String getChangeId() {
      return changeId;
    }

    /**
     * @return Status of the retrieved change, e.g. MERGED or NEW.
     */
    public String getStatus() {
      return status;
    }

    @Override
    public String toString() {
      return String.format("changeId='%s'\nstatus='%s'", changeId, status);
    }
  }

  /**
   * Queries for a change with the given changeId
   * @param repoUrl Gerrit repository URL
   * @param changeId The changeId of the change to find
   */
  Optional<GerritChange> query(String repoUrl, String changeId)
      throws RepoException;

  /**
   * Return true if this change finder can query {@code repoUrl}
   */
  boolean canQuery(String repoUrl);
}