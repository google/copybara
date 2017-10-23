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

import static com.google.copybara.git.GitModule.NO_GIT_DESTINATION_INTEGRATES;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.hash.Hashing;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.GerritChangeFinder.GerritChange;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.ProcessPushStructuredOutput;
import com.google.copybara.util.Glob;
import com.google.copybara.util.StructuredOutput;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination<GitRevision> {

  private static final int MAX_FIND_ATTEMPTS = 100;
  
  static final String CHANGE_ID_LABEL = "Change-Id";

  private static final class CommitGenerator implements GitDestination.CommitGenerator {

    private final GerritOptions gerritOptions;
    private final String repoUrl;
    private final Author committer;
    private final Console console;

    private CommitGenerator(
        GerritOptions gerritOptions, String repoUrl, Author committer, Console console) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
      this.repoUrl = Preconditions.checkNotNull(repoUrl);
      this.committer = Preconditions.checkNotNull(committer);
      this.console = Preconditions.checkNotNull(console);
    }

    /**
     * Returns message with a trailing Gerrit change id and if the change already exists or not. The
     * Gerrit change id has the form:
     *
     * <pre>
     * Change-Id: I{SHA1 hash}
     * </pre>
     *
     * Where the hash is generated from the data in the current tree and other data, including the
     * values of the git variables {@code GIT_AUTHOR_IDENT} and {@code GIT_COMMITTER_IDENT}. Checks
     * if the change exists or not by querying Gerrit. It needs to return if the change exists or
     * not because copybara prints a different output based on if the change already exists in
     * Gerrit or not.
     */
    @Override
    public MessageInfo message(TransformResult result) throws RepoException, ValidationException {
      if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
        return createMessageInfo(result, /*newPush=*/false, gerritOptions.gerritChangeId);
      }

      String workflowId = result.getChangeIdentity();
      GerritChangeFinder changeFinder = gerritOptions.getChangeFinder().get();
      if (changeFinder == null || gerritOptions.newChange) {
        return defaultMessageInfo(result, workflowId);
      } else if (!changeFinder.canQuery(repoUrl)) {
        console.warnFmt("Url '%s' is not eligible for Gerrit review reuse"
                            + " - new reviews will be created for each patch set.", repoUrl);
        return defaultMessageInfo(result, workflowId);
      }
      int attempt = 0;
      while (attempt <= MAX_FIND_ATTEMPTS) {
        String changeId = computeChangeId(workflowId, committer.getEmail(), attempt);
        console.progressFmt("Querying Gerrit ('%s') for change '%s'", repoUrl, changeId);
        Optional<GerritChange> change = changeFinder.query(repoUrl, changeId);
        if (!change.isPresent()) {
          return createMessageInfo(result, /*newPush=*/true, changeId);
        }
        if (change.get().getStatus().equals("NEW")) {
          return createMessageInfo(result, /*newPush=*/false, change.get().getChangeId());
        }
        attempt++;
      }
      throw new RepoException(
          String.format("Unable to find unmerged change for '%s', committer '%s'.",
                        workflowId, committer));
    }

    private MessageInfo defaultMessageInfo(TransformResult result, String workflowId) {
      // Default implementation: hash with the time to avoid collisions - will never find a change
      return createMessageInfo(
          result,
          /*newPush=*/true,
          computeChangeId(workflowId, committer.getEmail(),
                          (int) (System.currentTimeMillis() / 1000)));
    }

    private static MessageInfo createMessageInfo(TransformResult result, boolean newPush,
        String gerritChangeId) {
      Revision rev = result.getCurrentRevision();

      return new MessageInfo(ImmutableList.of(
          new LabelFinder(rev.getLabelName() + ": " + rev.asString()),
          new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId)), newPush);
    }

    static String computeChangeId(String workflowId, String committerEmail, int attempt) {
      return "I"
          + Hashing.sha1()
          .newHasher()
          .putString(workflowId, StandardCharsets.UTF_8)
          .putString(committerEmail, StandardCharsets.UTF_8)
          .putInt(attempt)
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
  public Writer<GitRevision> newWriter(Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<GitRevision> oldWriter) {
    return gitDestination.newWriter(destinationFiles, dryRun, groupId, oldWriter);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  static GerritDestination newGerritDestination(Options options, String url, String fetch,
      String pushToRefsFor) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    if (pushToRefsFor.isEmpty()) {
      pushToRefsFor = fetch;
    }
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    String push =
        Strings.isNullOrEmpty(gerritOptions.gerritTopic)
            ? String.format("refs/for/%s", pushToRefsFor)
            : String.format("refs/for/%s%%topic=%s", pushToRefsFor, gerritOptions.gerritTopic);
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    return new GerritDestination(
        new GitDestination(
            url,
            fetch,
            push,
            destinationOptions,
            generalOptions,
            /*skipPush=*/ false,
            new CommitGenerator(gerritOptions,
                url,
                destinationOptions.getCommitter(),
                generalOptions.console()),
            new GerritProcessPushOutput(
                generalOptions.console(),
                generalOptions.getStructuredOutput()),
            NO_GIT_DESTINATION_INTEGRATES));
  }

  static class GerritProcessPushOutput extends ProcessPushStructuredOutput {

    private static final Pattern GERRIT_URL_LINE = Pattern.compile(
        ".*: *(http(s)?://[^ ]+)( .*)?");
    private final Console console;

    GerritProcessPushOutput(Console console, StructuredOutput structuredOutput) {
      super(structuredOutput);
      this.console = Preconditions.checkNotNull(console);
    }

    @Override
    public void process(String output, boolean newReview, GitRepository localRepo) {
      super.process(output, newReview, localRepo);
      List<String> lines = Splitter.on("\n").splitToList(output);
      for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        if ((line.contains("New Changes") || line.contains("Updated Changes"))
            && iterator.hasNext()) {
          String next = iterator.next();
          Matcher matcher = GERRIT_URL_LINE.matcher(next);
          if (matcher.matches()) {
            String message = newReview
                ? "New Gerrit review created at "
                : "Updated existing Gerrit review at ";
            message = message + matcher.group(1);
            console.info(message);
            structuredOutput.getCurrentSummaryLineBuilder().setSummary(message);
            return;
          }
        }
      }
    }
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<>();
    for (Entry<String, String> entry : gitDestination.describe(originFiles).entries()) {
      if (entry.getKey().equals("type")) {
        continue;
      }
      builder.put(entry);
    }
    return builder
        .put("type", "gerrit.destination")
        .build();
  }
}
