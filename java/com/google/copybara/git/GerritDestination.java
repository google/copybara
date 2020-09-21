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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Endpoint;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.Options;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.WriteHook;
import com.google.copybara.git.gerritapi.ChangeInfo;
import com.google.copybara.git.gerritapi.ChangeStatus;
import com.google.copybara.git.gerritapi.ChangesQuery;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.IncludeResult;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.git.gerritapi.SubmitInput;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination<GitRevision> {

  static final int MAX_FIND_ATTEMPTS = 150;

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
    private final List<String> reviewersTemplate;
    @Nullable
    private final Checker endpointChecker;
    @Nullable
    private final NotifyOption notifyOption;
    private final Console console;
    private final ChangeIdPolicy changeIdPolicy;
    private final boolean allowEmptyDiffPatchSet;
    private final GeneralOptions generalOptions;
    private final List<String> ccTemplate;
    private final List<String> labelsTemplate;
    @Nullable
    private final String topicTemplate;
    private final boolean partialFetch;
    private final boolean gerritSubmit;

    GerritWriteHook(
        GeneralOptions generalOptions,
        GerritOptions gerritOptions,
        String repoUrl,
        Author committer,
        List<String> reviewersTemplate,
        List<String> ccTemplate,
        ChangeIdPolicy changeIdPolicy,
        boolean allowEmptyDiffPatchSet,
        List<String> labelsTemplate,
        @Nullable Checker endpointChecker,
        @Nullable NotifyOption notifyOption,
        @Nullable String topicTemplate,
        boolean partialFetch,
        boolean gerritSubmit) {
      this.generalOptions = Preconditions.checkNotNull(generalOptions);
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
      this.repoUrl = Preconditions.checkNotNull(repoUrl);
      this.committer = Preconditions.checkNotNull(committer);
      this.console = Preconditions.checkNotNull(generalOptions.console());
      this.changeIdPolicy = Preconditions.checkNotNull(changeIdPolicy);
      this.allowEmptyDiffPatchSet = allowEmptyDiffPatchSet;
      this.reviewersTemplate = Preconditions.checkNotNull(reviewersTemplate);
      this.ccTemplate = Preconditions.checkNotNull(ccTemplate);
      this.endpointChecker = endpointChecker;
      this.notifyOption = notifyOption;
      this.labelsTemplate = labelsTemplate;
      this.topicTemplate = topicTemplate;
      this.partialFetch = partialFetch;
      this.gerritSubmit = gerritSubmit;
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
      String hashTag = computeInternalHashTag(result);
      Optional<ChangeInfo> activeChange = findActiveChange(hashTag);
      if (activeChange.isPresent()) {
        return createMessageInfo(result, /*newReview=*/false,
            activeChange.get().getChangeId(), changeIdPolicy);
      }

      if (isGerritRandomChangeId()) {
        // If no change is found, create a random change-id. Change-Ids can be reused later
        // by looking by hashtag in the code above.
        return createMessageInfo(result, /*newReview=*/true,
            "I"
                + Hashing.sha1()
                .newHasher()
                .putString(UUID.randomUUID().toString(), StandardCharsets.UTF_8)
                .hash(), changeIdPolicy);
      }

      // TODO(malcon): Remove this logic after 2020-09-01, once all new changes are created with
      // hashtag.
      int attempt = 0;
      while (attempt <= MAX_FIND_ATTEMPTS) {
        String changeId = computeChangeId(workflowId, committer.getEmail(), attempt);
        List<ChangeInfo> changes = findChanges(changeId, ImmutableList.of());
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

    // TODO(malcon): Remove after 2020-09-01
    private boolean isGerritRandomChangeId() {
      return generalOptions.isTemporaryFeature("GERRIT_RANDOM_CHANGE_ID", true);
    }

    private String computeInternalHashTag(TransformResult result) {
      return "copybara_id_" + result.getChangeIdentity()
          + "_" + committer.getEmail().replaceAll("[ ,]", "_");
    }

    private Optional<ChangeInfo> findActiveChange(String hashTag)
        throws RepoException, ValidationException {

      console.progressFmt("Querying Gerrit ('%s') for active changes with hashtag '%s'",
          repoUrl, hashTag);
      List<ChangeInfo> changes = gerritOptions.newGerritApi(repoUrl).getChanges(new ChangesQuery(
          String.format("hashtag:\"%s\" AND project:%s AND status:NEW",
              hashTag, gerritOptions.getProject(repoUrl))));
      Optional<ChangeInfo> maxChangeNumber = changes.stream()
          .max(Comparator.comparingLong(ChangeInfo::getNumber));
      if (changes.size() > 1) {
        console.warnFmt("Multiple changes found for the same internal copybara tag: %s. Reusing"
                + " %s",
            changes.stream().map(ChangeInfo::getNumber).collect(Collectors.toList()),
            maxChangeNumber.get().getNumber());
      }
      return maxChangeNumber;
    }

    private List<ChangeInfo> findChanges(String changeId, Iterable<IncludeResult> includes)
        throws RepoException, ValidationException {
      console.progressFmt("Querying Gerrit ('%s') for change '%s'", repoUrl, changeId);
      return gerritOptions.newGerritApi(repoUrl).getChanges(new ChangesQuery(
          "change: " + changeId + " AND project:" + gerritOptions.getProject(repoUrl))
          .withInclude(includes));
    }

    @Override
    public void beforePush(GitRepository repo, MessageInfo messageInfo, boolean skipPush,
        List<? extends Change<?>> originChanges) throws RepoException, ValidationException {
      GerritMessageInfo gerritMessageInfo = (GerritMessageInfo) messageInfo;
      if (allowEmptyDiffPatchSet || gerritMessageInfo.newReview) {
        return;
      }
      try (ProfilerTask ignore = generalOptions.profiler().start("previous_patchset_check")) {
        ChangeInfo changeInfo = findChange(gerritMessageInfo.changeId);
        if (changeInfo == null) {
          return;
        }
        SameGitTree sameGitTree = new SameGitTree(repo, repoUrl, generalOptions, partialFetch);
        if (sameGitTree.hasSameTree(changeInfo.getCurrentRevision())) {
          throw new EmptyChangeException(String.format(
                "Skipping creating a new Gerrit PatchSet for change %s since the diff is the"
                    + " same from the previous PatchSet (%s)",
                changeInfo.getNumber(),
                changeInfo.getCurrentRevision()));
        }
      }
    }

    @Nullable
    private  ChangeInfo findChange(String changeId) throws ValidationException, RepoException {
      List<ChangeInfo> changes = findChanges(changeId,
          ImmutableList.of(IncludeResult.CURRENT_REVISION));
      if (changes.isEmpty()) {
        return null;
      }
      ChangeInfo changeInfo = changes.get(0);
      return changeInfo;
    }

    private void submitChange(String changeId)
        throws RepoException, ValidationException {
      try (ProfilerTask ignore = generalOptions.profiler().start("submit_gerrit_change")) {
        ChangeInfo changeInfo = findChange(changeId);
        if (changeInfo == null) {
          return;
        }
        GerritApi gerritApi = gerritOptions.newGerritApi(repoUrl);
        // Default selfReview before submitchange. We might consider to let users decide whether
        // do the selfReview before submitChange in future on demand. The main reason is that there
        // are two ways make a gerrit change submittable. One is selfReview, the other is prolog
        // config.
        gerritApi.setReview(changeInfo.getChangeId(), changeInfo.getCurrentRevision(),
            new SetReviewInput("", ImmutableMap
                .of("Code-Review", 2)));
        ChangeInfo resultInfo =
            gerritApi.submitChange(changeInfo.getChangeId(), new SubmitInput(null));
        console.infoFmt("Submitted change : %s/changes/%s", repoUrl, resultInfo.getChangeId());
      }
    }

    @Override
    public String getPushReference(String pushToRefsFor, TransformResult transformResult)
        throws ValidationException {
      List<String> components = Splitter.on('%').limit(2).splitToList(pushToRefsFor);

      Multimap<String, Optional<String>> options = LinkedHashMultimap.create();
      if (components.size() > 1) {
        for (String entry : Splitter.on(',')
            .split(components.get(1))) {
          List<String> strings = Splitter.on("=").limit(1).splitToList(entry);
          if (strings.size() > 1) {
            options.put(strings.get(0), Optional.of(strings.get(1)));
          } else {
            options.put(strings.get(0), Optional.empty());
          }
        }
      }

      if (notifyOption != null) {
        options.put("notify", Optional.of(notifyOption.toString()));
      }

      String topic = null;
      if (topicTemplate != null) {
        topic =
            SkylarkUtil.mapLabels(transformResult.getLabelFinder(), topicTemplate, "topic");
      }
      if (!Strings.isNullOrEmpty(gerritOptions.gerritTopic)) {
        if (topic != null) {
          console.warnFmt("Overriding topic %s with %s", topic, gerritOptions.gerritTopic);
        }
        topic = gerritOptions.gerritTopic;
      }

      if (topic != null) {
        options.put("topic", Optional.of(topic));
      }

      if (pushToRefsFor.startsWith("refs/for/")) {
        // Set an internal hashtag so that we can reuse changes in future snapshots.
        options.put("hashtag", Optional.of(computeInternalHashTag(transformResult)));
      }

      options.putAll("r",
          SkylarkUtil.mapLabels(transformResult.getLabelFinder(), reviewersTemplate)
              .stream().map(Optional::of).collect(Collectors.toList()));

      options.putAll("cc",
          SkylarkUtil.mapLabels(transformResult.getLabelFinder(), ccTemplate)
              .stream().map(Optional::of).collect(Collectors.toList()));

      options.putAll("label",
          SkylarkUtil.mapLabels(transformResult.getLabelFinder(), labelsTemplate)
              .stream().map(Optional::of).collect(Collectors.toList()));

      String result = components.get(0);
      if (!options.isEmpty()) {
        result += "%" + Joiner.on(',').join(options.entries()
            .stream()
            .map(e -> e.getKey()+(e.getValue().isPresent()?"="+e.getValue().get():""))
            .collect(Collectors.toList()));
      }
      return result;
    }

    @Override
    public ImmutableList<DestinationEffect> afterPush(String serverResponse,
        MessageInfo messageInfo, GitRevision pushedRevision,
        List<? extends Change<?>> originChanges) throws ValidationException, RepoException {
      // Should be the message info returned by generateMessageInfo
      GerritMessageInfo gerritMessageInfo = (GerritMessageInfo) messageInfo;
      if (gerritSubmit) {
        submitChange(gerritMessageInfo.changeId);
      }
      ImmutableList.Builder<DestinationEffect> result = ImmutableList.<DestinationEffect>builder()
              .add(new DestinationEffect(
                  DestinationEffect.Type.CREATED,
                  String.format("Created revision %s", pushedRevision.getSha1()),
                  originChanges,
                  new DestinationEffect.DestinationRef(
                      pushedRevision.getSha1(), "commit", /*url=*/ null)));

      List<String> lines = Splitter.on("\n").splitToList(serverResponse);
      Matcher gerritUrlMatcher = tryFindGerritUrl(lines);
      if (gerritUrlMatcher == null || !gerritUrlMatcher.matches()) {
        gerritUrlMatcher = tryFindGerritUrlOldFormat(lines);
      }
      if (gerritUrlMatcher != null && gerritUrlMatcher.matches()) {
        String message = gerritMessageInfo.newReview
            ? "New Gerrit review created at "
            : "Updated existing Gerrit review at ";
        String url = gerritUrlMatcher.group(1);
        String changeNum = url.substring(url.lastIndexOf("/") + 1);
        message = message + url;
        console.info(message);
        if (gerritSubmit) {
          message = message + ".\n Submited the change through API.";
        }
        result.add(
            new DestinationEffect(
                gerritMessageInfo.newReview
                    ? DestinationEffect.Type.CREATED
                    : DestinationEffect.Type.UPDATED,
                message,
                originChanges,
                new DestinationEffect.DestinationRef(changeNum, "gerrit_review", url)));
      }

      return result.build();
    }

    @Nullable
    private Matcher tryFindGerritUrl(List<String> lines) {
      boolean successFound = false;
      for (String line : lines) {
        if ((line.contains("SUCCESS"))) {
          successFound = true;
        }
        if (successFound) {
          // Usually next line is empty, but let's try best effort to find the URL after "SUCCESS"
          Matcher urlMatcher = GERRIT_URL_LINE.matcher(line);
          if (urlMatcher.matches()) {
            return urlMatcher;
          }
        }
      }
      return null;
    }

    @Nullable
    private Matcher tryFindGerritUrlOldFormat(List<String> lines) {
      for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        if ((line.contains("New Changes") || line.contains("Updated Changes"))
            && iterator.hasNext()) {
          String next = iterator.next();
          Matcher urlMatcher = GERRIT_URL_LINE.matcher(next);
          if (urlMatcher.matches()) {
            return urlMatcher;
          }
        }
      }
      return null;
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
        labels.add(new LabelFinder(result.getRevIdLabel() + ": " + rev.asString()));
      }
      String existingChangeId = getExistingChangeId(result.getSummary());
      String effectiveChangeId = existingChangeId;
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
          effectiveChangeId = gerritChangeId;
          break;
        case REUSE:
          if (existingChangeId == null) {
            labels.add(new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId));
            effectiveChangeId = gerritChangeId;
          }
          break;
        case REPLACE:
          labels.add(new LabelFinder(CHANGE_ID_LABEL + ": " + gerritChangeId));
          effectiveChangeId = gerritChangeId;
          break;
        default:
          throw new UnsupportedOperationException("Unsupported policy: " + changeIdPolicy);
      }

      return new GerritMessageInfo(labels.build(), newReview, effectiveChangeId);
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

    @Override
    public Endpoint getFeedbackEndPoint(Console console) throws ValidationException {
      gerritOptions.validateEndpointChecker(endpointChecker, repoUrl);
      return new GerritEndpoint(
          gerritOptions.newGerritApiSupplier(repoUrl, endpointChecker), repoUrl, console);
    }
  }

  @Override
  public Writer<GitRevision> newWriter(WriterContext writerContext) {
    return gitDestination.newWriter(writerContext);
  }

  /** A message info that contains also information if the change is a new review */
  @VisibleForTesting
  static class GerritMessageInfo extends MessageInfo {

    private final boolean newReview;
    private final String changeId;

    GerritMessageInfo(ImmutableList<LabelFinder> labelsToAdd, boolean newReview, String changeId) {
      super(labelsToAdd);
      this.newReview = newReview;
      this.changeId = Preconditions.checkNotNull(changeId);
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
      boolean partialFetch,
      @Nullable NotifyOption notifyOption,
      ChangeIdPolicy changeIdPolicy,
      boolean allowEmptyPatchSet,
      List<String> reviewers,
      List<String> cc,
      List<String> labels,
      @Nullable Checker endpointChecker,
      Iterable<GitIntegrateChanges> integrates,
      @Nullable String topicTemplate,
      boolean gerritSubmit) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    String push = submit && !gerritSubmit
        ? pushToRefsFor : String.format("refs/for/%s", pushToRefsFor);
    GitDestinationOptions destinationOptions = options.get(GitDestinationOptions.class);
    return new GerritDestination(
        new GitDestination(
            url,
            fetch,
            push,
            partialFetch,
            /*tagName=*/null,
            /*tagMsg=*/null,
            destinationOptions,
            options.get(GitOptions.class),
            generalOptions,
            new GerritWriteHook(
                generalOptions,
                gerritOptions,
                url,
                destinationOptions.getCommitter(),
                reviewers,
                cc,
                changeIdPolicy,
                allowEmptyPatchSet,
                labels,
                endpointChecker,
                notifyOption,
                topicTemplate,
                partialFetch,
                gerritSubmit),
            integrates),
        submit);
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

  /**
   * Notify Gerrit push option:
   * https://gerrit-review.googlesource.com/Documentation/user-upload.html#notify
   */
  enum NotifyOption {
    NONE,
    OWNER,
    OWNER_REVIEWERS,
    ALL
  }
}
