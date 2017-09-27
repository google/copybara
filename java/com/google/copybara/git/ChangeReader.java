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
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
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

  @Nullable
  private final Authoring authoring;
  private final GitRepository repository;
  private final Console console;
  private final boolean verbose;
  private final int limit;
  private final ImmutableList<String> roots;
  private final boolean includeBranchCommitLogs;

  private ChangeReader(@Nullable Authoring authoring, GitRepository repository, Console console,
      boolean verbose, int limit, Iterable<String> roots, boolean includeBranchCommitLogs) {
    this.authoring = authoring;
    this.repository = checkNotNull(repository, "repository");
    this.console = checkNotNull(console, "console");
    this.verbose = verbose;
    this.limit = limit;
    this.roots = ImmutableList.copyOf(roots);
    this.includeBranchCommitLogs = includeBranchCommitLogs;
  }

  ImmutableList<GitChange> run(String refExpression) throws RepoException {
    LogCmd logCmd = repository
        .log(refExpression)
        .withPaths(Glob.isEmptyRoot(roots) ? ImmutableList.of() : roots);
    if (limit != -1) {
      logCmd = logCmd.withLimit(limit);
    }
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

    ImmutableList<GitLogEntry> entries = repository.log(parents.get(0) + ".." + ref)
        .withPaths(Glob.isEmptyRoot(roots) ? ImmutableList.of() : roots)
        .firstParent(false).run();

    // Remove the merge commit. Since we already have that in the body.
    entries = entries.subList(1, entries.size());

    return "\n" + BRANCH_COMMIT_LOG_HEADING + "\n" +
        Joiner.on("\n").join(entries.stream()
            .map(e -> ""
                + "commit " + e.getCommit() + "\n"
                + "Author:  " + filterAuthor(e.getAuthor()) + "\n"
                + "Date:    " + e.getAuthorDate() + "\n"
                + "\n"
                + "    " + e.getBody().replace("\n", "    \n"))
            .collect(Collectors.toList()));
  }

  private ImmutableList<GitChange> parseChanges(ImmutableList<GitLogEntry> logEntries)
      throws RepoException {

    ImmutableList.Builder<GitChange> result = ImmutableList.builder();
    for (GitLogEntry e : logEntries) {
      result.add(new GitChange(new Change<>(
          e.getCommit(),
          filterAuthor(e.getAuthor())
          , e.getBody() + branchCommitLog(e.getCommit(), e.getParents()),
          e.getAuthorDate(),
          ChangeMessage.parseAllAsLabels(e.getBody()).labelsAsMultimap(),
          e.getFiles()),
          e.getParents()));
    }
    return result.build().reverse();
  }

  private Author filterAuthor(Author author) {
    return authoring == null || authoring.useAuthor(author.getEmail())
        ? author
        : authoring.getDefaultAuthor();
  }

  /**
   * An enhanced version of Change that contains the git parents.
   */
  static class GitChange {

    private final Change<GitRevision> change;
    private final ImmutableList<GitRevision> parents;

    GitChange(Change<GitRevision> change, Iterable<GitRevision> parents) {
      this.change = change;
      this.parents = ImmutableList.copyOf(parents);
    }

    public Change<GitRevision> getChange() {
      return change;
    }

    public ImmutableList<GitRevision> getParents() {
      return parents;
    }
  }

  /**
   * Builder for ChangeReader.
   */
  static class Builder {
    private Authoring authoring = null;
    private final GitRepository repository;
    private final Console console;
    private boolean verbose = false;
    private int limit = -1;
    private ImmutableList<String> roots = ImmutableList.of("");
    private boolean includeBranchCommitLogs = false;

    // TODO(matvore): Consider adding destinationFiles.
    // For ALL_FILES and where roots is [""], This will skip merges that don't affect the tree
    // For other cases, this will skip merges and commits that don't affect a subtree
    static Builder forDestination(GitRepository repository, Console console) {
      return new Builder(repository, console);
    }

    static Builder forOrigin(
        Authoring authoring, GitRepository repository, Console console, Glob originFiles) {
      return new Builder(repository, console)
          .setAuthoring(authoring)
          .setRoots(originFiles.roots());
    }

    private Builder(GitRepository repository, Console console) {
      this.repository = checkNotNull(repository, "repository");
      this.console = checkNotNull(console, "console");
    }

    Builder setLimit(int limit) {
      Preconditions.checkArgument(limit > 0);
      this.limit = limit;
      return this;
    }

    private Builder setAuthoring(Authoring authoring) {
      this.authoring = checkNotNull(authoring, "authoring");
      return this;
    }

    Builder setVerbose(boolean verbose) {
      this.verbose = verbose;
      return this;
    }

    Builder setIncludeBranchCommitLogs(boolean includeBranchCommitLogs) {
      this.includeBranchCommitLogs = includeBranchCommitLogs;
      return this;
    }

    private Builder setRoots(Iterable<String> roots) {
      this.roots = ImmutableList.copyOf(roots);
      return this;
    }

    ChangeReader build() {
      return new ChangeReader(
          authoring, repository, console, verbose, limit, roots, includeBranchCommitLogs);
    }
  }

}
