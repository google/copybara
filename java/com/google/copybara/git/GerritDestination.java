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

import static com.google.copybara.git.GitModule.DEFAULT_GIT_INTEGRATES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.Options;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.WriteHook;
import com.google.copybara.git.gerritapi.ChangeInfo;
import com.google.copybara.git.gerritapi.ChangeStatus;
import com.google.copybara.git.gerritapi.ChangesQuery;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination<GitRevision> {

  private static final int MAX_FIND_ATTEMPTS = 100;

  static final String CHANGE_ID_LABEL = "Change-Id";

  private final GitDestination gitDestination;
  private final boolean submit;

  private GerritDestination(GitDestination gitDestination, boolean submit) {
    this.gitDestination = Preconditions.checkNotNull(gitDestination);
    this.submit = submit;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("gitDestination", gitDestination)
        .toString();
  }

  static class GerritWriteHook implements WriteHook {

    private static final Pattern GERRIT_URL_LINE = Pattern.compile(".*: *(http(s)?://[^ ]+)( .*)?");

    private final GerritOptions gerritOptions;
    private final String repoUrl;
    private final Author committer;
    private final Console console;
    private final ChangeIdPolicy changeIdPolicy;
    private final boolean allowEmptyPatchSet;

    GerritWriteHook(GerritOptions gerritOptions, String repoUrl, Author committer,
        Console console, ChangeIdPolicy changeIdPolicy, boolean allowEmptyPatchSet) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
      this.repoUrl = Preconditions.checkNotNull(repoUrl);
      this.committer = Preconditions.checkNotNull(committer);
      this.console = Preconditions.checkNotNull(console);
      this.changeIdPolicy = Preconditions.checkNotNull(changeIdPolicy);
      this.allowEmptyPatchSet = allowEmptyPatchSet;
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
    public MessageInfo generateMessageInfo(TransformResult result)
        throws ValidationException, RepoException {
      if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
        return createMessageInfo(result, /*newReview=*/false, gerritOptions.gerritChangeId,
            // CLI flag always wins.
            ChangeIdPolicy.REPLACE);
      }

      String workflowId = result.getChangeIdentity();

      int attempt = 0;
      while (attempt <= MAX_FIND_ATTEMPTS) {
        String changeId = computeChangeId(workflowId, committer.getEmail(), attempt);
        console.progressFmt("Querying Gerrit ('%s') for change '%s'", repoUrl, changeId);
        List<ChangeInfo> changes = gerritOptions.newGerritApi(repoUrl).getChanges(new ChangesQuery(
            "change: " + changeId + " AND project:" + gerritOptions.getProject(repoUrl)));
        if (changes.isEmpty()) {
          return createMessageInfo(result, /*newReview=*/true, changeId, changeIdPolicy);
        }
        if (changes.get(0).getStatus().equals(ChangeStatus.NEW)) {
          return createMessageInfo(result, /*newReview=*/false, changes.get(0).getChangeId(),
              changeIdPolicy);
        }
        attempt++;
      }
      throw new RepoException(
          String.format("Unable to find unmerged change for '%s', committer '%s'.",
              workflowId, committer));
    }

    @Override
    public void beforePush(boolean skipPush) throws RepoException, ValidationException {
       if (!allowEmptyPatchSet) {
         throw new ValidationException("allow_empty_patchset check is still not implemented");
       }
    }

    @Override
    public ImmutableList<DestinationEffect> afterPush(String serverResponse,
        MessageInfo messageInfo, GitRevision pushedRevision,
        List<? extends Change<?>> originChanges) {
      // Should be the message info returned by generateMessageInfo
      GerritMessageInfo gerritMessageInfo = (GerritMessageInfo) messageInfo;
      ImmutableList.Builder<DestinationEffect> result = ImmutableList.<DestinationEffect>builder()
              .add(new DestinationEffect(
                  DestinationEffect.Type.CREATED,
                  String.format("Created revision %s", pushedRevision.getSha1()),
                  originChanges,
                  new DestinationEffect.DestinationRef(
                      pushedRevision.getSha1(), "commit", /*url=*/ null),
                  ImmutableList.of()));

      List<String> lines = Splitter.on("\n").splitToList(serverResponse);
      for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        if ((line.contains("New Changes") || line.contains("Updated Changes"))
            && iterator.hasNext()) {
          String next = iterator.next();
          Matcher matcher = GERRIT_URL_LINE.matcher(next);
          if (matcher.matches()) {
            String message = gerritMessageInfo.newReview
                ? "New Gerrit review created at "
                : "Updated existing Gerrit review at ";
            String url = matcher.group(1);
            String changeNum = url.substring(url.lastIndexOf("/") + 1);
            message = message + url;
            console.info(message);
            result.add(
                new DestinationEffect(
                    gerritMessageInfo.newReview
                        ? DestinationEffect.Type.CREATED
                        : DestinationEffect.Type.UPDATED,
                    message,
                    originChanges,
                    new DestinationEffect.DestinationRef(changeNum, "gerrit_review", url),
                    ImmutableList.of()));
            break;
          }
        }
      }
      return result.build();
    }

    @Nullable
    private String getExistingChangeId(String msg) {
      ChangeMessage changeMessage = ChangeMessage.parseMessage(msg);
      ImmutableListMultimap<String, String> labels = changeMessage.labelsAsMultimap();
      if (labels.containsKey(CHANGE_ID_LABEL)) {
        return Iterables.getLast(labels.get(CHANGE_ID_LABEL));
      }
      return null;
    }

    private GerritMessageInfo createMessageInfo(TransformResult result, boolean newReview,
        String gerritChangeId, ChangeIdPolicy changeIdPolicy) throws ValidationException {
      Revision rev = result.getCurrentRevision();
      ImmutableList.Builder<LabelFinder> labels = ImmutableList.builder();
      if (result.isSetRevId()) {
        labels.add(new LabelFinder(rev.getLabelName() + ": " + rev.asString()));
      }
      String existingChangeId = getExistingChangeId(result.getSummary());
      switch (changeIdPolicy) {
        case REQUIRE:
          ValidationException.checkCondition(existingChangeId != null,
              "%s label not found in message:\n%s",
              CHANGE_ID_LABEL, result.getSummary());
          break;
        case FAIL_IF_PRESENT:
          ValidationException.checkCondition(existingChangeId == null,
              "%s label found in message:\n%s. You can use"
                  + " git.gerrit_destination(change_id_policy = ...) to change this behavior",
              CHANGE_ID_LABEL, result.getSummary());
          labels.add(new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId));
          break;
        case REUSE:
          if (existingChangeId == null) {
            labels.add(new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId));
          }
          break;
        case REPLACE:
          labels.add(new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId));
          break;
        default:
          throw new UnsupportedOperationException("Unsupported policy: " + changeIdPolicy);
      }

      return new GerritMessageInfo(labels.build(), newReview);
    }

    @SuppressWarnings("deprecation")
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

  @Override
  public Writer<GitRevision> newWriter(Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<GitRevision> oldWriter) {
    return gitDestination.newWriter(destinationFiles, dryRun, groupId, oldWriter);
  }

  /** A message info that contains also information if the change is a new review */
  @VisibleForTesting
  static class GerritMessageInfo extends MessageInfo {

    private final boolean newReview;

    GerritMessageInfo(ImmutableList<LabelFinder> labelsToAdd, boolean newReview) {
      super(labelsToAdd);
      this.newReview = newReview;
    }
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  static GerritDestination newGerritDestination(
      Options options,
      String url,
      String fetch,
      String pushToRefsFor,
      boolean submit,
      ChangeIdPolicy changeIdPolicy,
      boolean allowEmptyPatchSet) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    String push;
    if (submit) {
      push = pushToRefsFor;
    } else {
      push =
          Strings.isNullOrEmpty(gerritOptions.gerritTopic)
              ? String.format("refs/for/%s", pushToRefsFor)
              : String.format("refs/for/%s%%topic=%s", pushToRefsFor, gerritOptions.gerritTopic);
    }
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    return new GerritDestination(
        new GitDestination(
            url,
            fetch,
            push,
            destinationOptions,
            options.get(GitOptions.class),
            generalOptions,
            /*skipPush=*/ false,
            new GerritWriteHook(
                gerritOptions,
                url,
                destinationOptions.getCommitter(),
                generalOptions.console(),
                changeIdPolicy,
                allowEmptyPatchSet),
            DEFAULT_GIT_INTEGRATES),
        submit
    );
  }

  @Override
  public String getType() {
    return submit ? gitDestination.getType() : "gerrit.destination";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<>();
    if (submit) {
      return gitDestination.describe(originFiles);
    }
    for (Entry<String, String> entry : gitDestination.describe(originFiles).entries()) {
      if (entry.getKey().equals("type")) {
        continue;
      }
      builder.put(entry);
    }
    return builder
        .put("type", getType())
        .build();
  }

  /** What to do in the presence or absent of Change-Id in message. */
  public enum ChangeIdPolicy {
    /** Require that the change_id is present in the message as a valid label */
    REQUIRE,
    /** Fail if found in message */
    FAIL_IF_PRESENT,
    /** Reuse if present. Otherwise generate a new one */
    REUSE,
    /** Replace with a new one if found */
    REPLACE,
  }
}
