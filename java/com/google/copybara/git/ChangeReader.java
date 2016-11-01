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
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.LabelFinder;
import com.google.copybara.RepoException;
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


  private ChangeReader(@Nullable Authoring authoring, GitRepository repository, Console console,
      boolean verbose, int limit) {
    this.authoring = authoring;
    this.repository = checkNotNull(repository, "repository");
    this.console = checkNotNull(console, "console");;
    this.verbose = verbose;
    this.limit = limit;
  }

  ImmutableList<GitChange> run(String refExpression)
      throws RepoException {
    List<String> params = new ArrayList<>(
        Arrays.asList("log", "--no-color", "--date=iso-strict"));

    if (limit != -1) {
      params.add("-" + limit);
    }

    params.add("--parents");
    params.add("--first-parent");

    params.add(refExpression);
    return parseChanges(
        repository.simpleCommand(params.toArray(new String[params.size()])).getStdout());
  }

  private ImmutableList<GitChange> parseChanges(String log) {
    // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
    if (log.isEmpty()) {
      return ImmutableList.of();
    }

    Iterator<String> rawLines = Splitter.on('\n').split(log).iterator();
    ImmutableList.Builder<GitChange> builder = ImmutableList.builder();

    while (rawLines.hasNext()) {
      String rawCommitLine = rawLines.next();
      Iterator<String> commitReferences = Splitter.on(" ")
          .split(removePrefix(log, rawCommitLine, "commit")).iterator();

      GitReference ref = repository.createReferenceFromCompleteSha1(commitReferences.next());
      ImmutableList.Builder<GitReference> parents = ImmutableList.builder();
      while (commitReferences.hasNext()) {
        parents.add(repository.createReferenceFromCompleteSha1(commitReferences.next()));
      }
      String line = rawLines.next();
      Author author = null;
      ZonedDateTime dateTime = null;
      while (!line.isEmpty()) {
        if (line.startsWith("Author: ")) {
          String authorStr = line.substring("Author: ".length()).trim();
          Author parsedUser = GitAuthorParser.parse(authorStr);
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
          "Could not find author and/or date for commitReferences %s in log\n:%s", rawCommitLine,
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
      Change<GitReference> change = new Change<>(
          ref, author, message.toString(), dateTime, ImmutableMap.copyOf(labels));
      builder.add(new GitChange(change, parents.build()));
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

    private final Change<GitReference> change;
    private final ImmutableList<GitReference> parents;

    GitChange(Change<GitReference> change, ImmutableList<GitReference> parents) {
      this.change = change;
      this.parents = parents;
    }

    public Change<GitReference> getChange() {
      return change;
    }

    public ImmutableList<GitReference> getParents() {
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

    static Builder forDestination(GitRepository repository, Console console) {
      return new Builder(repository, console);
    }

    static Builder forOrigin(Authoring authoring, GitRepository repository, Console console) {
      return new Builder(repository, console).setAuthoring(authoring);
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

    ChangeReader build() {
      return new ChangeReader(authoring, repository, console, verbose, limit);
    }
  }

}
