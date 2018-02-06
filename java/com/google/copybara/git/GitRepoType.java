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

import static com.google.copybara.git.GitModule.DEFAULT_INTEGRATE_LABEL;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.RepoException;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.git.GithubUtil.GithubPrUrl;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Git repository type. Knowing the repository type allow us to provide better experience, like
 * allowing to import Github PR/Gerrit changes using the web url as the reference.
 */
public enum GitRepoType {
  @DocField(description = "A standard git repository. This is the default")
  GIT {
    /**
     * Standard git resolution tries to apply some heuristics for resolving the ref. Currently it
     * supports:
     *
     * <ul>
     *   <li>SHA-1 references if they are reachable from heads. Example:
     *       7d45b192cf1bf3a45990afeb840a382760111dee
     *   <li>Valid git refs for {@code repoUrl}. Examples: master, refs/some/ref, etc.
     *   <li>Fetching HEAD from a git url. Example http://somerepo.com/foo, file:///home/john/repo,
     *       etc.
     *   <li>Fetching a reference from a git url. Example: "http://somerepo.com/foo branch"
     * </ul>
     */
    @Override
    GitRevision resolveRef(
        GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions)
        throws RepoException, CannotResolveRevisionException {
      logger.log(Level.INFO, "Resolving " + repoUrl + " reference: " + ref);

      Matcher sha1WithPatchSet = SHA_1_WITH_REVIEW_DATA.matcher(ref);
      if (sha1WithPatchSet.matches()) {
        GitRevision rev = repository.fetchSingleRef(repoUrl, sha1WithPatchSet.group(1));
        return new GitRevision(repository, rev.getSha1(), sha1WithPatchSet.group(2),
                               rev.contextReference(), rev.associatedLabels(), repoUrl);
      }


      if (!GIT_URL.matcher(ref).matches() && !FILE_URL.matcher(ref).matches()) {
        // If ref is not an url try a normal fetch of repoUrl and ref
        return fetchFromUrl(repository, repoUrl, ref);
      }
      String msg = "Git origin URL overwritten in the command line as " + ref;
      generalOptions.console().warn(msg);
      logger.warning(msg + ". Config value was: " + repoUrl);
      generalOptions.console().progress("Fetching HEAD for " + ref);
      GitRevision ghPullRequest = maybeFetchGithubPullRequest(repository, repoUrl, ref);
      if (ghPullRequest != null) {
        return ghPullRequest;
      }
      int spaceIdx = ref.lastIndexOf(" ");

      // Treat "http://someurl ref" as a url and a reference. This
      if (spaceIdx != -1) {
        return fetchFromUrl(repository, ref.substring(0, spaceIdx), ref.substring(spaceIdx + 1));
      }
      return fetchFromUrl(repository, ref, "HEAD");
    }

    private GitRevision fetchFromUrl(GitRepository repository, String repoUrl, String ref)
        throws RepoException, CannotResolveRevisionException {
      return repository.fetchSingleRef(repoUrl, ref);
    }
  },
  @DocField(description = "A git repository hosted in Github")
  GITHUB {
    /**
     * Github resolution supports all {@link GitRepoType#GIT} formats and additionally if the ref is
     * a github url, is equals to {@code repoUrl} and is a pull request it tries to transform it to
     * a valid git fetch of the equivalent ref.
     */
    @Override
    GitRevision resolveRef(
        GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions)
        throws RepoException, CannotResolveRevisionException {
      if ((ref.startsWith("https://github.com") && ref.startsWith(repoUrl))
          || GithubUtil.maybeParseGithubPrFromMergeOrHeadRef(ref).isPresent()) {
        GitRevision ghPullRequest = maybeFetchGithubPullRequest(repository, repoUrl, ref);
        if (ghPullRequest != null) {
          return ghPullRequest;
        }
      }
      return GIT.resolveRef(repository, repoUrl, ref, generalOptions);
    }
  },
  @DocField(description = "A Gerrit code review repository")
  GERRIT {

    private final Pattern url = Pattern.compile("https?://.*?/([0-9]+)(?:/([0-9]+))?/?");

    @Override
    GitRevision resolveRef(
        GitRepository repository, String repoUrl, String ref, GeneralOptions options)
        throws RepoException, CannotResolveRevisionException {
      if (ref == null || ref.isEmpty()) {
        return GIT.resolveRef(repository, repoUrl, ref, options);
      }
      Matcher refMatcher = WHOLE_GERRIT_REF.matcher(ref);
      if (refMatcher.matches()) {
        return fetchWithChangeNumberAsContext(
            repository,
            repoUrl,
            Integer.parseInt(refMatcher.group(1)),
            Integer.parseInt(refMatcher.group(2)),
            ref, options);
      }
      // A change number like '23423'
      if (CharMatcher.javaDigit().matchesAllOf(ref)) {
        return resolveLatestPatchSet(repository, repoUrl, Integer.parseInt(ref), options);
      }

      Matcher urlMatcher = url.matcher(ref);
      if (!urlMatcher.matches()) {
        return GIT.resolveRef(repository, repoUrl, ref, options);
      }
      if (!ref.startsWith(repoUrl)) {
        // Assume it is our url. We can make this more strict later
        options
            .console()
            .warn(
                String.format(
                    "Assuming repository '%s' for looking for review '%s'", repoUrl, ref));
      }
      int change = Integer.parseInt(urlMatcher.group(1));
      Integer patchSet = urlMatcher.group(2) == null ? null : Integer.parseInt(urlMatcher.group(2));
      if (patchSet == null) {
        return resolveLatestPatchSet(repository, repoUrl, change, options);
      }
      Map<Integer, GitRevision> patchSets = getGerritPatchSets(repository, repoUrl, change);
      if (!patchSets.containsKey(patchSet)) {
        throw new CannotResolveRevisionException(
            String.format(
                "Cannot find patch set %d for change %d in %s. Available Patch sets: %s",
                patchSet, change, repoUrl, patchSets.keySet()));
      }
      return fetchWithChangeNumberAsContext(
          repository, repoUrl, change, patchSet, patchSets.get(patchSet).contextReference(),
          options);
    }

    private GitRevision resolveLatestPatchSet(
        GitRepository repository, String repoUrl, int changeNumber, GeneralOptions options)
        throws RepoException, CannotResolveRevisionException {
      Entry<Integer, GitRevision> lastPatchset =
          // Last entry is the latest patchset, since it is ordered by patchsetId.
          getGerritPatchSets(repository, repoUrl, changeNumber).lastEntry();
      return fetchWithChangeNumberAsContext(
          repository,
          repoUrl,
          changeNumber,
          lastPatchset.getKey(),
          lastPatchset.getValue().contextReference(), options);
    }

    private GitRevision fetchWithChangeNumberAsContext(
        GitRepository repository, String repoUrl, int change, int patchSet, String ref,
        GeneralOptions generalOptions)
        throws RepoException, CannotResolveRevisionException {
      String metaRef = String.format("refs/changes/%02d/%d/meta", change % 100, change);
      repository.fetch(repoUrl, /*prune=*/true, /*force=*/true,
          ImmutableList.of(ref + ":refs/gerrit/" + ref, metaRef + ":refs/gerrit/" + metaRef));
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
          ImmutableMap.<String, String>builder()
              .put(GERRIT_CHANGE_NUMBER_LABEL, changeNumber)
              .put(GERRIT_CHANGE_ID_LABEL, changeId)
              .put(GERRIT_CHANGE_DESCRIPTION_LABEL, changeDescription)
              .put(
                  DEFAULT_INTEGRATE_LABEL,
                  new GerritIntegrateLabel(
                          repository, generalOptions, repoUrl, change, patchSet, changeId)
                      .toString())
              .build(),
          repoUrl);
    }

    /**
     * Use NoteDB for extracting the Change-id. It should be the first commit in the log
     * of the meta reference.
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
  };

  /**
   * Check if the reference is a github pull request url. And if so, fetch it and return the
   * reference. Otherwise return null.
   */
  @Nullable
  protected static GitRevision maybeFetchGithubPullRequest(GitRepository repository,
      String repoUrl, String ref)
      throws RepoException, CannotResolveRevisionException {
    Optional<GithubPrUrl> githubPrUrl = GithubUtil.maybeParseGithubPrUrl(ref);
    if (githubPrUrl.isPresent()) {
      // TODO(malcon): Support merge ref too once we have github pr origin.
      String stableRef = GithubUtil.asHeadRef(githubPrUrl.get().getPrNumber());
      GitRevision gitRevision = repository.fetchSingleRef(
          "https://github.com/" + githubPrUrl.get().getProject(), stableRef);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          /*reviewReference=*/null,
          stableRef,
          ImmutableMap.of(), repoUrl);
    }
    if (GithubUtil.maybeParseGithubPrFromMergeOrHeadRef(ref).isPresent()) {
      GitRevision gitRevision = repository.fetchSingleRef(repoUrl, ref);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          /*reviewReference=*/null,
          ref,
          ImmutableMap.of(), repoUrl);
    }
    return null;
  }

  public static final String GERRIT_CHANGE_NUMBER_LABEL = "GERRIT_CHANGE_NUMBER";
  public static final String GERRIT_CHANGE_ID_LABEL = "GERRIT_CHANGE_ID";
  // TODO(danielromero): Implement (and refer from gerrit_origin documentation in GitModule)
  public static final String GERRIT_CHANGE_URL_LABEL = "GERRIT_CHANGE_URL";
  public static final String GERRIT_CHANGE_DESCRIPTION_LABEL = "GERRIT_CHANGE_DESCRIPTION";

  private static final Logger logger = Logger.getLogger(GitRepoType.class.getCanonicalName());

  private static final Pattern GIT_URL =
      Pattern.compile("(\\w+://)(.+@)*([\\w.]+)(:[\\d]+)?/*(.*)");

  private static final Pattern FILE_URL = Pattern.compile("file://(.*)");

  private static final String GERRIT_PATCH_SET_REF_PREFIX = "PatchSet ";

  private static final Pattern WHOLE_GERRIT_REF =
      Pattern.compile("refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)");

  /** Example: "54d2a09b272f22a6d27e76b891f36213b98e0ddc random text" */
  private static final Pattern SHA_1_WITH_REVIEW_DATA =
      Pattern.compile("(" + GitRevision.COMPLETE_SHA1_PATTERN.pattern() + ") (.+)");

  abstract GitRevision resolveRef(
      GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions)
      throws RepoException, CannotResolveRevisionException;

  static String gerritPatchSetAsReviewReference(int patchSet) {
    return GERRIT_PATCH_SET_REF_PREFIX + patchSet;
  }

  /**
   * Get all the patchsets for a change ordered by the patchset number. Last is the most recent
   * one.
   */
  static TreeMap<Integer, GitRevision> getGerritPatchSets(GitRepository repository, String url, int changeNumber)
      throws RepoException, CannotResolveRevisionException {
    TreeMap<Integer, GitRevision> patchSets = new TreeMap<>();
    String basePath = String.format("refs/changes/%02d/%d", changeNumber % 100, changeNumber);
    Map<String, String> refsToSha1 = repository.lsRemote(url, ImmutableList.of(basePath + "/*"));
    if (refsToSha1.isEmpty()) {
      throw new CannotResolveRevisionException(
          String.format("Cannot find change number %d in '%s'", changeNumber, url));
    }
    for (Entry<String, String> e : refsToSha1.entrySet()) {
      if (e.getKey().endsWith("/meta")) {
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
      int patchSet = Integer.parseInt(matcher.group(2));
      patchSets.put(
          patchSet,
          new GitRevision(
              repository,
              e.getValue(),
              gerritPatchSetAsReviewReference(patchSet),
              e.getKey(),
              ImmutableMap.of(), url));
    }
    return patchSets;
  }

  @SuppressWarnings("unused")
  @Nullable
  Integer getGerritPatchSetFromReviewReference(@Nullable String reviewReference) {
    if (reviewReference == null || !reviewReference.startsWith(GERRIT_PATCH_SET_REF_PREFIX)) {
      return null;
    }
    return Integer.parseInt(reviewReference.substring(GERRIT_PATCH_SET_REF_PREFIX.length()));
  }

}
