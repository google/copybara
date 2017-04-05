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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.Change;
import com.google.copybara.LabelFinder;
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  private String runLog(Iterable<String> params) throws RepoException {
    List<String> fullParams =
        new ArrayList<>(Arrays.asList("log", "--pretty", "--no-color", "--date=iso-strict"));
    Iterables.addAll(fullParams, params);
    if (!roots.get(0).isEmpty()) {
      fullParams.add("--");
      fullParams.addAll(roots);
    }
    return repository.simpleCommand(fullParams.toArray(new String[0])).getStdout();
  }

  ImmutableList<GitChange> run(String refExpression) throws RepoException {
    List<String> params = new ArrayList<>();

    if (limit != -1) {
      params.add("-" + limit);
    }

    params.add("--parents");
    params.add("--first-parent");

    params.add(refExpression);

    return parseChanges(runLog(params));
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

    return new StringBuilder()
        .append("\n").append(BRANCH_COMMIT_LOG_HEADING).append("\n")
        .append(runLog(ImmutableList.of(parents.get(0) + ".." + ref)));
  }

  private ImmutableList<GitChange> parseChanges(String log) throws RepoException {
    // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
    if (log.isEmpty()) {
      return ImmutableList.of();
    }

    Iterator<String> rawLines = Splitter.on('\n').split(log).iterator();
    ImmutableList.Builder<GitChange> builder = ImmutableList.builder();

    while (rawLines.hasNext()) {
      String rawCommitLine = rawLines.next();
      Iterator<String> commitRevisions = Splitter.on(" ")
          .split(removePrefix(log, rawCommitLine, "commit")).iterator();

      GitRevision ref = repository.createReferenceFromCompleteSha1(commitRevisions.next());
      ArrayList<GitRevision> parents = new ArrayList<>();
      while (commitRevisions.hasNext()) {
        parents.add(repository.createReferenceFromCompleteSha1(commitRevisions.next()));
      }
      String line = rawLines.next();
      Author author = null;
      ZonedDateTime dateTime = null;
      while (!line.isEmpty()) {
        if (line.startsWith("Author: ")) {
          String authorStr = line.substring("Author: ".length()).trim();
          Author parsedUser;
          try {
            parsedUser = AuthorParser.parse(authorStr);
          } catch (InvalidAuthorException e) {
            throw new RepoException("Invalid author found in Git history.", e);
          }
          if (authoring == null || authoring.useAuthor(parsedUser.getEmail())) {
            author = parsedUser;
          } else {
            author = authoring.getDefaultAuthor();
          }
        } else if (line.startsWith("Date: ")) {
          dateTime = ZonedDateTime.parse(line.substring("Date: ".length()).trim());
        }
        line = rawLines.next();
      }
      Preconditions.checkState(author != null || dateTime != null,
          "Could not find author and/or date for commitRevisions %s in log\n:%s", rawCommitLine,
          log);
      StringBuilder message = new StringBuilder();
      // Maintain labels in order just in case we print them back in the destination.
      Map<String, String> labels = new LinkedHashMap<>();
      while (rawLines.hasNext()) {
        String s = rawLines.next();
        if (!s.startsWith(GitOrigin.GIT_LOG_COMMENT_PREFIX)) {
          break;
        }
        LabelFinder labelFinder = new LabelFinder(
            s.substring(GitOrigin.GIT_LOG_COMMENT_PREFIX.length()));
        if (labelFinder.isLabel()) {
          String previous = labels.put(labelFinder.getName(), labelFinder.getValue());
          if (previous != null && verbose) {
            console.warn(String.format("Possible duplicate label '%s' happening multiple times"
                    + " in commit. Keeping only the last value: '%s'\n  Discarded value: '%s'",
                labelFinder.getName(), labelFinder.getValue(), previous));
          }
        }
        message.append(s, GitOrigin.GIT_LOG_COMMENT_PREFIX.length(), s.length()).append("\n");
      }
      message.append(branchCommitLog(ref, parents));
      Change<GitRevision> change = new Change<>(
          ref, author, message.toString(), dateTime, ImmutableMap.copyOf(labels));
      builder.add(new GitChange(change, parents));
    }
    // Return older commit first.
    return builder.build().reverse();
  }

  private String removePrefix(String log, String line, String prefix) {
    Preconditions.checkState(line.startsWith(prefix), "Cannot find '%s' in:\n%s", prefix, log);
    return line.substring(prefix.length()).trim();
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
    private GitRepository repository;
    private Console console;
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
