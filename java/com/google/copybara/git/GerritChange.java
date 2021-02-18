/*
 * Copyright (C) 2019 Google Inc.
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

import static com.google.copybara.git.GitModule.DEFAULT_INTEGRATE_LABEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * A class that represents a Gerrit change. It contains all the necessary objects to do a
 * fetch when {@code {@link #fetch(ImmutableMultimap)}} is invoked.
 */
class GerritChange {

  public static final String GERRIT_CHANGE_NUMBER_LABEL = "GERRIT_CHANGE_NUMBER";
  public static final String GERRIT_CHANGE_ID_LABEL = "GERRIT_CHANGE_ID";
  public static final String GERRIT_COMPLETE_CHANGE_ID_LABEL =
      "GERRIT_COMPLETE_CHANGE_ID";
  // TODO(danielromero): Implement (and refer from gerrit_origin documentation in GitModule)
  public static final String GERRIT_CHANGE_URL_LABEL = "GERRIT_CHANGE_URL";
  public static final String GERRIT_CHANGE_BRANCH = "GERRIT_CHANGE_BRANCH";
  public static final String GERRIT_CHANGE_TOPIC = "GERRIT_CHANGE_TOPIC";
  public static final String GERRIT_CHANGE_DESCRIPTION_LABEL = "GERRIT_CHANGE_DESCRIPTION";
  public static final String GERRIT_OWNER_EMAIL_LABEL = "GERRIT_OWNER_EMAIL";
  public static final String GERRIT_OWNER_USERNAME_LABEL = "GERRIT_OWNER_USERNAME";
  private static final String GERRIT_PATCH_SET_REF_PREFIX = "PatchSet ";

  private static final Pattern WHOLE_GERRIT_REF =
      Pattern.compile("refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)");

  private static final Pattern URL_PATTERN =
      Pattern.compile("https?://.*?/([0-9]+)(?:/([0-9]+))?/?");

  private final GitRepository repository;
  private final GeneralOptions generalOptions;
  private final String repoUrl;
  private final int change;
  private final int patchSet;
  private final String ref;

  private GerritChange(GitRepository repository, GeneralOptions generalOptions,
      String repoUrl, int change, int patchSet, String ref) {
    this.repository = Preconditions.checkNotNull(repository);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.repoUrl = repoUrl;
    this.change = change;
    this.patchSet = patchSet;
    this.ref = ref;
  }

  /**
   * Get the change number
   */
  public int getChange() {
    return change;
  }

  /**
   * Gets the specific PatchSet of the Change
   */
  public int getPatchSet() {
    return patchSet;
  }

  /**
   * Context reference for creating GitRevision
   */
  public String getRef() {
    return ref;
  }

  /**
   * Given a local repository, a repo url and a reference, it tries to do its best to resolve the
   * reference to a Gerrit Change.
   *
   * <p>Note that if the PatchSet is not found in the ref, it will go to Gerrit to get the latest
   * PatchSet number.
   *
   * @return a Gerrit change if it can be resolved. Null otherwise.
   */
  @Nullable
  public static GerritChange resolve(
      GitRepository repository, String repoUrl, String ref, GeneralOptions options)
      throws RepoException, ValidationException {
    if (Strings.isNullOrEmpty(ref)) {
      return null;
    }
    Matcher refMatcher = WHOLE_GERRIT_REF.matcher(ref);
    if (refMatcher.matches()) {
      return new GerritChange(
          repository,
          options,
          repoUrl,
          Ints.tryParse(refMatcher.group(1)),
          Ints.tryParse(refMatcher.group(2)),
          ref);
    }
    // A change number like '23423'
    if (CharMatcher.javaDigit().matchesAllOf(ref)) {
      return resolveLatestPatchSet(repository, options, repoUrl, Ints.tryParse(ref));
    }

    Matcher urlMatcher = URL_PATTERN.matcher(ref);
    if (!urlMatcher.matches()) {
      return null;
    }

    if (!ref.startsWith(repoUrl)) {
      // Assume it is our url. We can make this more strict later
      options
          .console()
          .warn(
              String.format(
                  "Assuming repository '%s' for looking for review '%s'", repoUrl, ref));
    }
    int change = Ints.tryParse(urlMatcher.group(1));
    Integer patchSet = urlMatcher.group(2) == null ? null : Ints.tryParse(urlMatcher.group(2));
    if (patchSet == null) {
      return resolveLatestPatchSet(repository, options, repoUrl, change);
    }
    Map<Integer, GitRevision> patchSets = getGerritPatchSets(repository, repoUrl, change);
    if (!patchSets.containsKey(patchSet)) {
      throw new CannotResolveRevisionException(
          String.format(
              "Cannot find patch set %d for change %d in %s. Available Patch sets: %s",
              patchSet, change, repoUrl, patchSets.keySet()));
    }
    return new GerritChange(
        repository, options, repoUrl, change, patchSet, patchSets.get(patchSet).contextReference());

  }

  /**
   * Fetch the change from Gerrit
   *
   * @param additionalLabels additional labels to add to the GitRevision labels
   * @return The resolved and fetched SHA-1 of the change.
   */
  GitRevision fetch(ImmutableMultimap<String, String> additionalLabels)
      throws RepoException, ValidationException {
    String metaRef = String.format("refs/changes/%02d/%d/meta", change % 100, change);
    repository.fetch(repoUrl, /*prune=*/true, /*force=*/true,
        ImmutableList.of(ref + ":refs/gerrit/" + ref, metaRef + ":refs/gerrit/" + metaRef), false);
    GitRevision gitRevision = repository.resolveReference("refs/gerrit/" + ref);
    GitRevision metaRevision = repository.resolveReference("refs/gerrit/" + metaRef);
    String changeId = getChangeIdFromMeta(repository, metaRevision , metaRef);
    String changeNumber = Integer.toString(change);
    String changeDescription = getDescriptionFromMeta(repository, metaRevision , metaRef);
    return new GitRevision(
        repository,
        gitRevision.getSha1(),
        gerritPatchSetAsReviewReference(patchSet),
        changeNumber,
        ImmutableListMultimap.<String, String>builder()
            .put(GERRIT_CHANGE_NUMBER_LABEL, changeNumber)
            .put(GERRIT_CHANGE_ID_LABEL, changeId)
            .put(GERRIT_CHANGE_DESCRIPTION_LABEL, changeDescription)
            .put(
                DEFAULT_INTEGRATE_LABEL,
                new GerritIntegrateLabel(
                    repository, generalOptions, repoUrl, change, patchSet, changeId)
                    .toString())
            .putAll(additionalLabels)
            .putAll(generalOptions.cliLabels().entrySet())
            .build(),
        repoUrl);
  }

  private static GerritChange resolveLatestPatchSet(
      GitRepository repository, GeneralOptions options, String repoUrl,
      int changeNumber)
      throws RepoException, ValidationException {
    Entry<Integer, GitRevision> lastPatchset =
        // Last entry is the latest patchset, since it is ordered by patchsetId.
        getGerritPatchSets(repository, repoUrl, changeNumber).lastEntry();
    return new GerritChange(repository, options, repoUrl, changeNumber, lastPatchset.getKey(),
        lastPatchset.getValue().contextReference());
  }

  /**
   * Use NoteDB for extracting the Change-id. It should be the first commit in the log
   * of the meta reference.
   *
   * TODO(malcon): Remove usage and use Gerrit API in GerritOrigin
   */
  private String getChangeIdFromMeta(GitRepository repo, GitRevision metaRevision,
      String metaRef) throws RepoException {
    List<ChangeMessage> changes = getChanges(repo, metaRevision, metaRef);
    String changeId = null;
    for (LabelFinder change : Iterables.getLast(changes).getLabels()) {
      if (change.isLabel() && change.getName().equals("Change-id")
          && change.getSeparator().equals(": ")) {
        changeId = change.getValue();
      }
    }
    if (changeId == null) {
      throw new RepoException(String.format(
          "Cannot find Change-id in %s. Not present in: \n%s", metaRef,
          Iterables.getLast(changes).getText()));
    }

    return changeId;
  }

  private String getDescriptionFromMeta(GitRepository repo, GitRevision metaRevision,
      String metaRef) throws RepoException {
    List<ChangeMessage> changes = getChanges(repo, metaRevision, metaRef);
    return changes.get(0).getText();
  }

  /**
   * Returns the list of {@link ChangeMessage}s. Guarantees that there is at least one change.
   */
  private List<ChangeMessage> getChanges(GitRepository repo, GitRevision metaRevision,
      String metaRef) throws RepoException {
    List<ChangeMessage> changes = Lists.transform(repo.log(metaRevision.getSha1()).run(),
        e -> ChangeMessage.parseMessage(e.getBody()));

    if (changes.isEmpty()) {
      throw new RepoException("Cannot find any PatchSet in " + metaRef);
    }
    return changes;
  }

  /**
   * Get all the patchsets for a change ordered by the patchset number. Last is the most recent
   * one.
   */
  static TreeMap<Integer, GitRevision> getGerritPatchSets(
      GitRepository repository, String url, int changeNumber)
      throws RepoException, ValidationException {
    TreeMap<Integer, GitRevision> patchSets = new TreeMap<>();
    String basePath = String.format("refs/changes/%02d/%d", changeNumber % 100, changeNumber);
    Map<String, String> refsToSha1 = repository.lsRemote(url, ImmutableList.of(basePath + "/*"));
    if (refsToSha1.isEmpty()) {
      throw new CannotResolveRevisionException(
          String.format("Cannot find change number %d in '%s'", changeNumber, url));
    }
    for (Entry<String, String> e : refsToSha1.entrySet()) {
      if (e.getKey().endsWith("/meta") || e.getKey().endsWith("/robot-comments")) {
        continue;
      }
      Preconditions.checkState(
          e.getKey().startsWith(basePath + "/"),
          String.format("Unexpected response reference %s for %s", e.getKey(), basePath));
      Matcher matcher = WHOLE_GERRIT_REF.matcher(e.getKey());
      Preconditions.checkArgument(
          matcher.matches(),
          "Unexpected format for response reference %s for %s",
          e.getKey(),
          basePath);
      int patchSet = Ints.tryParse(matcher.group(2));
      patchSets.put(
          patchSet,
          new GitRevision(
              repository,
              e.getValue(),
              gerritPatchSetAsReviewReference(patchSet),
              e.getKey(),
              ImmutableListMultimap.of(), url));
    }
    return patchSets;
  }

  @VisibleForTesting
  static String gerritPatchSetAsReviewReference(int patchSet) {
    return GERRIT_PATCH_SET_REF_PREFIX + patchSet;
  }
}
