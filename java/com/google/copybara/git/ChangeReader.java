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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.LogCmd;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Utility class to introspect the log of a Git repository.
 */
class ChangeReader {

  @Nullable private final Authoring authoring;
  private final GitRepository repository;
  private final int limit;
  private final ImmutableList<String> roots;
  private final boolean includeBranchCommitLogs;
  private final String url;
  private final boolean firstParent;
  private final boolean partialFetch;
  private final int skip;
  @Nullable private final String grepString;

  private ChangeReader(@Nullable Authoring authoring, GitRepository repository, int limit,
      Iterable<String> roots, boolean includeBranchCommitLogs, @Nullable String url,
      boolean firstParent, boolean partialFetch, int skip, @Nullable String grepString) {
    this.authoring = authoring;
    this.repository = checkNotNull(repository, "repository");
    this.limit = limit;
    this.roots = ImmutableList.copyOf(roots);
    this.includeBranchCommitLogs = includeBranchCommitLogs;
    this.url = url;
    this.firstParent = firstParent;
    this.partialFetch = partialFetch;
    this.skip = skip;
    this.grepString = grepString;
  }

  ImmutableList<Change<GitRevision>> run(String refExpression)
      throws RepoException, ValidationException {
    LogCmd logCmd = repository
        .log(refExpression)
        .firstParent(firstParent);
    if (limit != -1) {
      logCmd = logCmd.withLimit(limit);
    }
    if (skip > 0) {
      logCmd = logCmd.withSkip(skip);
    }
    if (grepString != null) {
      logCmd = logCmd.grep(grepString);
    }
    if (partialFetch && roots.contains("")) {
      throw new ValidationException("Config error: partial_fetch feature is not compatible "
          + "with fetching the whole repo.");
    }
    if (partialFetch) {
      logCmd = logCmd.withPaths(roots);
    }
    // Log command does not filter by roots here because of how git log works. Some commits (e.g.
    // fake merges) might not include the files in the log, and filtering here would return
    // incorrect results. We do filter later on the changes to match the actual glob.
    return parseChanges(logCmd.includeFiles(true).includeMergeDiff(true).run());
  }

  static final String BRANCH_COMMIT_LOG_HEADING = "-- Branch commit log --";

  private CharSequence branchCommitLog(GitRevision ref, List<GitRevision> parents)
      throws RepoException {
    if (parents.size() <= 1) {
      // Not a merge commit, so don't bother showing full log of branch commits. This would only
      // contain the raw commit of 'ref', which will be redundant.
      return "";
    }
    if (!includeBranchCommitLogs) {
      return "";
    }

    ImmutableList<GitLogEntry> entries =
        repository
            .log(parents.get(0).getSha1() + ".." + ref.getSha1())
            // This might give incorrect results but several migrations rely on this behavior.
            // and first_parent = False doesn't work for ITERATIVE
            .withPaths(Glob.isEmptyRoot(roots) ? ImmutableList.of() : roots)
            .firstParent(false)
            .run();

    if (entries.isEmpty()) {
      return "";
    }
    // Remove the merge commit. Since we already have that in the body.
    entries = entries.subList(1, entries.size());

    return "\n" + BRANCH_COMMIT_LOG_HEADING + "\n" +
        Joiner.on("\n").join(entries.stream()
            .map(e -> ""
                + "commit " + e.getCommit().getSha1() + "\n"
                + "Author:  " + filterAuthor(e.getAuthor()) + "\n"
                + "Date:    " + e.getAuthorDate() + "\n"
                + "\n"
                + "    " + e.getBody().replace("\n", "    \n"))
            .collect(Collectors.toList()));
  }

  private ImmutableList<Change<GitRevision>> parseChanges(ImmutableList<GitLogEntry> logEntries)
      throws RepoException {

    ImmutableList.Builder<Change<GitRevision>> result = ImmutableList.builder();
    GitRevision last = null;
    for (GitLogEntry e : logEntries) {
      // Keep the first commit if repeated (merge commits).
      if (last != null && last.equals(e.getCommit())) {
        continue;
      }
      last = e.getCommit();
      result.add(new Change<>(
          e.getCommit().withUrl(url),
          filterAuthor(e.getAuthor())
          , e.getBody() + branchCommitLog(e.getCommit(), e.getParents()),
          e.getAuthorDate(),
          ChangeMessage.parseAllAsLabels(e.getBody()).labelsAsMultimap(),
          e.getFiles(), e.getParents().size() > 1, e.getParents()));
    }
    return result.build().reverse();
  }

  private Author filterAuthor(Author author) {
    return authoring == null || authoring.useAuthor(author.getEmail())
        ? author
        : authoring.getDefaultAuthor();
  }

  /**
   * Builder for ChangeReader.
   */
  static class Builder {

    private Authoring authoring = null;
    private final GitRepository repository;
    private int limit = -1;
    private ImmutableList<String> roots = ImmutableList.of("");
    private boolean includeBranchCommitLogs = false;
    private String url;
    private boolean firstParent;
    private boolean partialFetch;
    private int skip;
    private String grepString;

    // TODO(matvore): Consider adding destinationFiles.
    // For ALL_FILES and where roots is [""], This will skip merges that don't affect the tree
    // For other cases, this will skip merges and commits that don't affect a subtree
    static Builder forDestination(GitRepository repository, Console console) {
      return new Builder(repository, console);
    }

    static Builder forOrigin(Authoring authoring, GitRepository repository, Console console) {
      return new Builder(repository, console)
          .setAuthoring(authoring);
    }

    private Builder(GitRepository repository, Console console) {
      this.repository = checkNotNull(repository, "repository");
      checkNotNull(console, "console");
    }

    Builder setLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      this.limit = limit;
      return this;
    }

    Builder setSkip(int skip) {
      Preconditions.checkArgument(skip >= 0);
      this.skip = skip;
      return this;
    }

    private Builder setAuthoring(Authoring authoring) {
      this.authoring = checkNotNull(authoring, "authoring");
      return this;
    }

    Builder setPartialFetch(boolean partialFetch) {
      this.partialFetch = partialFetch;
      return this;
    }

    Builder setFirstParent(boolean firstParent) {
      this.firstParent = firstParent;
      return this;
    }

    Builder setIncludeBranchCommitLogs(boolean includeBranchCommitLogs) {
      this.includeBranchCommitLogs = includeBranchCommitLogs;
      return this;
    }

    Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    /**
     * Only return commits that match the given paths in the Git log command.
     */
    Builder setRoots(Iterable<String> roots) {
      this.roots = ImmutableList.copyOf(roots);
      return this;
    }

    /**
     * Grep for the given pattern in the Git log command.
     */
    Builder grep(String grepString) {
      this.grepString = grepString;
      return this;
    }

    ChangeReader build() {
      return new ChangeReader(
          authoring, repository, limit, roots, includeBranchCommitLogs, url,
          firstParent, partialFetch, skip, grepString);
    }
  }

}
