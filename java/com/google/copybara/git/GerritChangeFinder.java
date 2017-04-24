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
import com.google.common.hash.Hashing;
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.util.console.Console;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/** Generic abstract class for querying Gerrit. */
public abstract class GerritChangeFinder {

  private static final int MAX_ATTEMPTS = 100;

  /**
   * Information about a single gerrit change.
   */
  public static class GerritChange {
    private final boolean found;
    private final String changeId;
    @Nullable private final String status;

    public GerritChange(String changeId, String status, boolean found) {
      Preconditions.checkArgument(!found || status != null,
          "Status can only be null if the change was not found.");
      this.changeId = Preconditions.checkNotNull(changeId);
      this.status = status;
      this.found = found;
    }

    /**
     * @return ChangeId of the retrieved change
     */
    public String getChangeId() {
      return changeId;
    }

    /**
     * @return Status of the retrieved change, e.g. MERGED or NEW.
     *     Can be null if the change was not found.
     */
    @Nullable public String getStatus() {
      return status;
    }

    /**
     * @return Whether an unmerged change was found.
     */
    public boolean wasFound() {
      return found;
    }

    @Override
    public String toString() {
      return String.format("changeId='%s'\nstatus='%s'\nfound='%s'", changeId, status, found);
    }
  }

  /** Default no op implementation. */
  // TODO(copybara-team) - provide a proper implementation.
  public static class Default extends GerritChangeFinder {

    @Override
    protected GerritChange query(String url, String changeId, Console console) {
      // Default implementation: rehash with the time to avoid collisions - will never find a change
      return new GerritChange(
          computeChangeId(
              changeId, "DummyCommitter", (int) (System.currentTimeMillis() / 1000)), null, false);
    }
  }

  protected String computeChangeId(String workflowId, String committerEmail, int attempt) {
    return "I"
        + Hashing.sha1()
        .newHasher()
        .putString(workflowId, StandardCharsets.UTF_8)
        .putString(committerEmail, StandardCharsets.UTF_8)
        .putInt(attempt)
        .hash();
  }

  /**
   * Queries for a change with the given changeId
   * @param repoUrl Gerrit repository URL
   * @param changeId The changeId of the change to find
   * @param console
   */
  protected abstract GerritChange query(String repoUrl, String changeId, Console console)
      throws RepoException;


  /**
   * Finds the first unmerged change for the given workflowId or the changeId to use for a new one,
   * if none are found.
   * @param repoUrl Gerrit repository URL
   * @param workflowId The WorkflowId of the change to find
   * @param committer Committer for the change
   * @param console
   */
  public GerritChange find(String repoUrl, String workflowId, Author committer, Console console)
      throws RepoException {
    int attempt = 0;
    while (attempt <= MAX_ATTEMPTS) {
      GerritChange change =
          query(repoUrl, computeChangeId(workflowId, committer.getEmail(), attempt), console);
      if (!change.wasFound() || change.getStatus().equals("NEW")) {
        return change;
      }
      attempt++;
    }
    throw new RepoException(
        String.format("Unable to find unmerged change for '%s', committer '%s'.",
            workflowId, committer));
  }
}