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

package com.google.copybara.hg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.hg.HgRepository.LogCmd;
import com.google.copybara.util.console.Console;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Utility class to introspect the log of a Mercurial (Hg) repository.
 */
class ChangeReader {
  private static final String NULL_GLOBAL_ID = Strings.repeat("0", 40);

  private final HgRepository repository;
  private final int limit;
  private final int skip;
  private final Console console;
  private final Optional<Authoring> authoring;

  private ChangeReader(HgRepository repository, int limit, int skip, Console console,
      Optional<Authoring> authoring) {
    this.repository = repository;
    this.limit = limit;
    this.skip = skip;
    this.console = console;
    this.authoring = authoring;
  }

  ImmutableList<HgChange> run(String refExpression) throws RepoException, ValidationException {
    LogCmd logCmd = repository.log();
    if (limit > 0) {
      if (skip > 0) {
        logCmd = logCmd.withReferenceExpression(
            String.format("limit(%s::, %d, %d)", refExpression, limit, skip));
        return parseChanges(logCmd.run());
      }

      logCmd = logCmd.withLimit(limit);
    }

    logCmd = logCmd.withReferenceExpression(refExpression);
    return parseChanges(logCmd.run());
  }

  static class Builder {

    private final HgRepository repository;
    private int limit;
    private int skip;
    private final Console console;
    private Authoring authoring = null;

    private Builder(HgRepository repository, Console console) {
      this.repository = Preconditions.checkNotNull(repository);
      this.console = Preconditions.checkNotNull(console);
      this.limit = 0;
      this.skip = 0;
    }

    static Builder forOrigin(HgRepository repository, Authoring authoring, Console console) {
      return new Builder(repository, console)
          .setAuthoring(authoring);
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

    Builder setAuthoring(Authoring authoring) {
      this.authoring = checkNotNull(authoring, "authoring");
      return this;
    }

    ChangeReader build() {
      return new ChangeReader(repository, limit, skip, console, Optional.of(authoring));
    }
  }

  /**
   * A version of {@class Change} that contains the parents of a hg change.
   */
  static class HgChange {
    private final Change<HgRevision> change;
    private final ImmutableList<HgRevision> parents;

    HgChange(Change<HgRevision> change, Iterable<HgRevision> parents) {
      this.change = Preconditions.checkNotNull(change);
      Preconditions.checkNotNull(parents);
      this.parents = ImmutableList.copyOf(parents);
    }

    Change<HgRevision> getChange() {
      return change;
    }

    ImmutableList<HgRevision> getParents() {
      return parents;
    }
  }

  private ImmutableList<HgChange> parseChanges(ImmutableList<HgLogEntry> logEntries)
      throws RepoException {
    ImmutableList.Builder<HgChange> result = ImmutableList.builder();

    for (HgLogEntry entry : logEntries) {
      HgRevision rev = new HgRevision(entry.getGlobalId());
      if (NULL_GLOBAL_ID.equals(rev.getGlobalId())) {
        continue;
      }

      Author user;
      try {
        user = AuthorParser.parse(entry.getUser());
      }
      catch (InvalidAuthorException e) {
        console.warn(String.format("Cannot parse commit user and email: %s", e.getMessage()));
        user = authoring.orElseThrow(
            () -> new RepoException(
                String.format("No default author provided.")))
            .getDefaultAuthor();
      }

      ZonedDateTime date = entry.getZonedDate();
      ImmutableListMultimap<String, String> labels =
          ChangeMessage.parseAllAsLabels(entry.getDescription()).labelsAsMultimap();

      ImmutableSet<String> files = ImmutableSet.copyOf(entry.getFiles());

      Change<HgRevision> change =
          new Change<>(rev, user, entry.getDescription(), date, labels, files);

      ImmutableList.Builder<HgRevision> builder = ImmutableList.builder();
      for (String parentId : entry.getParents()) {
        builder.add(new HgRevision(parentId));
      }

      result.add(new HgChange(change, builder.build()));
    }
    return result.build();
  }
}
