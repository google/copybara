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

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.git.GitDestination.ProcessPushOutput;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination<GitReference> {

  private static final class CommitGenerator implements GitDestination.CommitGenerator {

    private final GerritOptions gerritOptions;

    CommitGenerator(GerritOptions gerritOptions) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
    }

    /**
     * Generates a message with a trailing Gerrit change id in the form:
     *
     * <pre>
     * Change-Id: I{SHA1 hash}
     * </pre>
     *
     * Where the hash is generated from the data in the current tree and other data, including the
     * values of the git variables {@code GIT_AUTHOR_IDENT} and {@code GIT_COMMITTER_IDENT}.
     */
    @Override
    public String message(TransformResult transformResult, GitRepository repo)
        throws RepoException {
      return String.format("%s\n%s: %s\nChange-Id: %s\n",
          transformResult.getSummary(),
          transformResult.getOriginRef().getLabelName(),
          transformResult.getOriginRef().asString(),
          changeId(repo)
      );
    }

    private String maybeParentHash(GitRepository repo) {
      try {
        return repo.simpleCommand("rev-parse", "HEAD^0").getStdout();
      } catch (RepoException e) {
        return "";
      }
    }

    private String changeId(GitRepository repo) throws RepoException {
      if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
        return gerritOptions.gerritChangeId;
      }

      return "I" + Hashing.sha1().newHasher()
          .putString(repo.simpleCommand("write-tree").getStdout(), Charsets.UTF_8)
          .putString(maybeParentHash(repo), Charsets.UTF_8)
          .putString(repo.simpleCommand("var", "GIT_AUTHOR_IDENT").getStdout(), Charsets.UTF_8)
          .putString(repo.simpleCommand("var", "GIT_COMMITTER_IDENT").getStdout(), Charsets.UTF_8)
          .hash();
    }

  }

  private final GitDestination gitDestination;

  private GerritDestination(GitDestination gitDestination) {
    this.gitDestination = Preconditions.checkNotNull(gitDestination);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("gitDestination", gitDestination)
        .toString();
  }

  @Override
  public Writer newWriter(Glob destinationFiles) {
    // TODO(matvore): Return a writer that doesn't support getPreviousRef()?
    // That method doesn't make sense for Gerrit since we do not plan to use previous ref for pull
    // requests.
    return gitDestination.newWriter(destinationFiles);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  static GerritDestination newGerritDestination(
      Options options, String url, String fetch, String pushToRefsFor,
      Map<String, String> environment) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    if (pushToRefsFor.isEmpty()) {
      pushToRefsFor = fetch;
    }
    return new GerritDestination(
        new GitDestination(
            url, fetch,
            "refs/for/" + pushToRefsFor,
            options.get(GitDestinationOptions.class),
            generalOptions.isVerbose(),
            new CommitGenerator(options.get(GerritOptions.class)),
            new GerritProcessPushOutput(generalOptions.console()),
            environment, options.get(GeneralOptions.class).console()));
  }

  static class GerritProcessPushOutput extends ProcessPushOutput {

    private static final Pattern GERRIT_URL_LINE = Pattern.compile(
        ".*: *(http(s)?://[^ ]+)( .*)?");
    private final Console console;

    GerritProcessPushOutput(Console console) {
      this.console = console;
    }

    @Override
    void process(String output) {
      List<String> lines = Splitter.on("\n").splitToList(output);
      for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        if ((line.contains("New Changes") || line.contains("Updated Changes"))
            && iterator.hasNext()) {
          String next = iterator.next();
          Matcher matcher = GERRIT_URL_LINE.matcher(next);
          if (matcher.matches()) {
            console.info("New Gerrit review created at " + matcher.group(1));
          }
        }
      }
    }
  }

  @Override
  public Reader<GitReference> newReader(Glob destinationFiles) {
    return gitDestination.newReader(destinationFiles);
  }
}
