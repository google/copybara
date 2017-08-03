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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.doc.annotations.DocField;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Map;
import java.util.Map.Entry;
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
                               rev.contextReference(), rev.associatedLabels());
      }


      if (!GIT_URL.matcher(ref).matches() && !FILE_URL.matcher(ref).matches()) {
        // If ref is not an url try a normal fetch of repoUrl and ref
        return repository.fetchSingleRef(repoUrl, ref);
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
        return repository.fetchSingleRef(ref.substring(0, spaceIdx), ref.substring(spaceIdx + 1));
      }
      return repository.fetchSingleRef(ref, "HEAD");
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
          || GITHUB_PULL_REQUEST_REF.matches(ref)) {
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

    private final Pattern WHOLE_REF = Pattern.compile("refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)");
    private final Pattern URL = Pattern.compile("https?://.*?/([0-9]+)(?:/([0-9]+))?/?");

    @Override
    GitRevision resolveRef(
        GitRepository repository, String repoUrl, String ref, GeneralOptions options)
        throws RepoException, CannotResolveRevisionException {
      if (ref == null || ref.isEmpty()) {
        return GIT.resolveRef(repository, repoUrl, ref, options);
      }
      Matcher refMatcher = WHOLE_REF.matcher(ref);
      if (refMatcher.matches()) {
        return fetchWithChangeNumberAsContext(
            repository,
            repoUrl,
            Integer.parseInt(refMatcher.group(1)),
            Integer.parseInt(refMatcher.group(2)),
            ref);
      }
      // A change number like '23423'
      if (CharMatcher.javaDigit().matchesAllOf(ref)) {
        return resolveLatestPatchSet(repository, repoUrl, options, Integer.parseInt(ref));
      }

      Matcher urlMatcher = URL.matcher(ref);
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
        return resolveLatestPatchSet(repository, repoUrl, options, change);
      }
      Map<Integer, GitRevision> patchSets = getPatchSets(repository, repoUrl, change, options);
      if (!patchSets.containsKey(patchSet)) {
        throw new CannotResolveRevisionException(
            String.format(
                "Cannot find patch set %d for change %d in %s. Available Patch sets: %s",
                patchSet, change, repoUrl, patchSets.keySet()));
      }
      return fetchWithChangeNumberAsContext(
          repository, repoUrl, change, patchSet, patchSets.get(patchSet).contextReference());
    }

    private GitRevision resolveLatestPatchSet(
        GitRepository repository, String repoUrl, GeneralOptions generalOptions, int changeNumber)
        throws RepoException, CannotResolveRevisionException {
      Entry<Integer, GitRevision> lastPatchset =
          // Last entry is the latest patchset, since it is ordered by patchsetId.
          getPatchSets(repository, repoUrl, changeNumber, generalOptions).lastEntry();
      return fetchWithChangeNumberAsContext(
          repository,
          repoUrl,
          changeNumber,
          lastPatchset.getKey(),
          lastPatchset.getValue().contextReference());
    }

    private GitRevision fetchWithChangeNumberAsContext(
        GitRepository repository, String repoUrl, int change, int patchSet, String ref)
        throws RepoException, CannotResolveRevisionException {
      GitRevision gitRevision = repository.fetchSingleRef(repoUrl, ref);
      String changeNumber = Integer.toString(change);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          gerritPatchSetAsReviewReference(patchSet),
          changeNumber,
          ImmutableMap.of(GERRIT_CHANGE_NUMBER_LABEL, changeNumber));
    }

    /**
     * Get all the patchsets for a change ordered by the patchset number. Last is the most recent
     * one.
     */
    private TreeMap<Integer, GitRevision> getPatchSets(
        GitRepository repository, String url, int changeNumber, GeneralOptions generalOptions)
        throws RepoException, CannotResolveRevisionException {
      TreeMap<Integer, GitRevision> patchSets = new TreeMap<>();
      String basePath = String.format("refs/changes/%02d/%d", changeNumber % 100, changeNumber);
      Map<String, String> refsToSha1 =
          GitRepository.lsRemote(
              url, ImmutableList.of(basePath + "/*"), generalOptions.getEnvironment());
      if (refsToSha1.isEmpty()) {
        throw new CannotResolveRevisionException(
            String.format("Cannot find change number %d in '%s'", changeNumber, url));
      }
      for (Entry<String, String> e : refsToSha1.entrySet()) {
        Preconditions.checkState(
            e.getKey().startsWith(basePath + "/"),
            String.format("Unexpected response reference %s for %s", e.getKey(), basePath));
        if (e.getKey().endsWith("/meta")) {
          continue;
        }
        Matcher matcher = WHOLE_REF.matcher(e.getKey());
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
                ImmutableMap.of()));
      }
      return patchSets;
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
    Matcher matcher = GITHUB_PULL_REQUEST.matcher(ref);
    if (matcher.matches()) {
      // TODO(malcon): Support merge ref too once we have github pr origin.
      String stableRef = "refs/pull/" + matcher.group(2) + "/head";
      GitRevision gitRevision = repository.fetchSingleRef(matcher.group(1), stableRef);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          // TODO(malcon): Decide the format to use here:
          /*reviewReference=*/null,
          stableRef,
          ImmutableMap.of(GITHUB_PR_NUMBER_LABEL, matcher.group(2)));

    }
    matcher = GITHUB_PULL_REQUEST_REF.matcher(ref);
    if (matcher.matches()) {
      GitRevision gitRevision = repository.fetchSingleRef(repoUrl, ref);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          // TODO(malcon): Decide the format to use here:
          /*reviewReference=*/null,
          ref,
          ImmutableMap.of(GITHUB_PR_NUMBER_LABEL, matcher.group(1)));
    }
    return null;
  }

  public static final String GERRIT_CHANGE_NUMBER_LABEL = "GERRIT_CHANGE_NUMBER";
  public static final String GITHUB_PR_NUMBER_LABEL = "GITHUB_PR_NUMBER";

  private static final Logger logger = Logger.getLogger(GitRepoType.class.getCanonicalName());

  private static final Pattern GITHUB_PULL_REQUEST =
      Pattern.compile("(?P<url>https://github[.]com/.+)/pull/([0-9]+)");

  private static final Pattern GITHUB_PULL_REQUEST_REF =
      Pattern.compile("refs/pull/([0-9]+)/(head|merge)");

  private static final Pattern GIT_URL =
      Pattern.compile("(\\w+://)(.+@)*([\\w.]+)(:[\\d]+){0,1}/*(.*)");

  private static final Pattern FILE_URL = Pattern.compile("file://(.*)");

  private static final String GERRIT_PATCH_SET_REF_PREFIX = "PatchSet ";

  /** Example: "54d2a09b272f22a6d27e76b891f36213b98e0ddc random text" */
  private static final Pattern SHA_1_WITH_REVIEW_DATA =
      Pattern.compile("(" + GitRevision.COMPLETE_SHA1_PATTERN.pattern() + ") (.+)");

  abstract GitRevision resolveRef(
      GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions)
      throws RepoException, CannotResolveRevisionException;

  String gerritPatchSetAsReviewReference(int patchSet) {
    return GERRIT_PATCH_SET_REF_PREFIX + patchSet;
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
