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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.GeneralOptions;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.git.github.util.GitHubHost.GitHubPrUrl;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Optional;
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
        GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions,
        boolean describeVersion, boolean partialFetch)
        throws RepoException, ValidationException {
      logger.atInfo().log("Resolving %s reference: %s", repoUrl, ref);

      Matcher sha1WithPatchSet = SHA_1_WITH_REVIEW_DATA.matcher(ref);
      if (sha1WithPatchSet.matches()) {
        GitRevision rev = repository.fetchSingleRefWithTags(repoUrl, sha1WithPatchSet.group(1),
            /*fetchTags=*/describeVersion, partialFetch);
        return new GitRevision(repository, rev.getSha1(), sha1WithPatchSet.group(2),
                               rev.contextReference(), rev.associatedLabels(), repoUrl);
      }


      if (!GIT_URL.matcher(ref).matches() && !FILE_URL.matcher(ref).matches()) {
        // If ref is not an url try a normal fetch of repoUrl and ref
        return fetchFromUrl(repository, repoUrl, ref, describeVersion, partialFetch);
      }
      String msg = "Git origin URL overwritten in the command line as " + ref;
      generalOptions.console().warn(msg);
      logger.atWarning().log("%s. Config value was: %s", msg, repoUrl);
      generalOptions.console().progress("Fetching HEAD for " + ref);
      GitRevision ghPullRequest = maybeFetchGithubPullRequest(repository, repoUrl, ref,
          describeVersion, partialFetch);
      if (ghPullRequest != null) {
        return ghPullRequest;
      }
      int spaceIdx = ref.lastIndexOf(" ");

      // Treat "http://someurl ref" as a url and a reference. This
      if (spaceIdx != -1) {
        return fetchFromUrl(repository, ref.substring(0, spaceIdx), ref.substring(spaceIdx + 1),
            describeVersion, partialFetch);
      }
      return fetchFromUrl(repository, ref, "HEAD", describeVersion, partialFetch);
    }

    private GitRevision fetchFromUrl(GitRepository repository, String repoUrl, String ref,
        boolean describeVersion, boolean partialFetch)
        throws RepoException, ValidationException {
      return repository.fetchSingleRefWithTags(repoUrl, ref, /*fetchTags=*/describeVersion, partialFetch);
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
        GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions,
        boolean describeVersion, boolean partialFetch)
        throws RepoException, ValidationException {
      if ((ref.startsWith("https://github.com") && ref.startsWith(repoUrl))
          || GitHubUtil.maybeParseGithubPrFromMergeOrHeadRef(ref).isPresent()) {
        GitRevision ghPullRequest = maybeFetchGithubPullRequest(repository, repoUrl, ref,
            describeVersion, partialFetch);
        if (ghPullRequest != null) {
          return ghPullRequest;
        }
      }
      return GIT.resolveRef(repository, repoUrl, ref, generalOptions, describeVersion, partialFetch);
    }
  },
  @DocField(description = "A Gerrit code review repository")
  GERRIT {
    @Override
    GitRevision resolveRef(
        GitRepository repository, String repoUrl, String ref, GeneralOptions options,
        boolean describeVersion, boolean partialFetch)
        throws RepoException, ValidationException {
      if (ref == null || ref.isEmpty()) {
        // TODO(malcon): Deprecate this. Gerrit origin should only be used with changes or
        // (maybe) overwrite url as a ref.
        return GIT.resolveRef(repository, repoUrl, ref, options, describeVersion, partialFetch);
      }

      GerritChange change = GerritChange
          .resolve(repository, repoUrl, ref, options);

      if (change != null) {
        return change.fetch(ImmutableMultimap.of());
      }

      // Try git base resolve to resolve almost anything that can be git (sha, random urls, etc.)
      return GIT.resolveRef(repository, repoUrl, ref, options, describeVersion, partialFetch);

    }
  };

  /**
   * Check if the reference is a github pull request url. And if so, fetch it and return the
   * reference. Otherwise return null.
   */
  @Nullable
  protected static GitRevision maybeFetchGithubPullRequest(GitRepository repository,
      String repoUrl, String ref, boolean describeVersion, boolean partialFetch)
      throws RepoException, ValidationException {
    // TODO(malcon): This only supports github.com PRs, not enterprise.
    Optional<GitHubPrUrl> githubPrUrl = GitHubHost.GITHUB_COM.maybeParseGithubPrUrl(ref);
    if (githubPrUrl.isPresent()) {
      // TODO(malcon): Support merge ref too once we have github pr origin.
      String stableRef = GitHubUtil.asHeadRef(githubPrUrl.get().getPrNumber());
      GitRevision gitRevision = repository.fetchSingleRefWithTags(
          "https://github.com/" + githubPrUrl.get().getProject(), stableRef,
          /*fetchTags=*/describeVersion, partialFetch);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          /*reviewReference=*/null,
          stableRef,
          ImmutableListMultimap.of(), repoUrl);
    }
    if (GitHubUtil.maybeParseGithubPrFromMergeOrHeadRef(ref).isPresent()) {
      GitRevision gitRevision = repository.fetchSingleRefWithTags(repoUrl, ref,
          /*fetchTags=*/describeVersion, partialFetch);
      return new GitRevision(
          repository,
          gitRevision.getSha1(),
          /*reviewReference=*/null,
          ref,
          ImmutableListMultimap.of(), repoUrl);
    }
    return null;
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern GIT_URL =
      Pattern.compile("(\\w+://)(.+@)*([\\w.]+)(:[\\d]+)?/*(.*)");

  private static final Pattern FILE_URL = Pattern.compile("file://(.*)");

  /** Example: "54d2a09b272f22a6d27e76b891f36213b98e0ddc random text" */
  private static final Pattern SHA_1_WITH_REVIEW_DATA =
      Pattern.compile("(" + GitRevision.COMPLETE_SHA1_PATTERN.pattern() + ") (.+)");

  // TODO(malcon): Remove all these once internal code reveres to GerritChange fields
  public static final String GERRIT_CHANGE_NUMBER_LABEL = GerritChange.GERRIT_CHANGE_NUMBER_LABEL;
  public static final String GERRIT_CHANGE_ID_LABEL = GerritChange.GERRIT_CHANGE_ID_LABEL;
  public static final String GERRIT_CHANGE_URL_LABEL = GerritChange.GERRIT_CHANGE_URL_LABEL;
  public static final String GERRIT_CHANGE_DESCRIPTION_LABEL =
      GerritChange.GERRIT_CHANGE_DESCRIPTION_LABEL;

  abstract GitRevision resolveRef(
      GitRepository repository, String repoUrl, String ref, GeneralOptions generalOptions,
      boolean describeVersion, boolean partialFetch)
      throws RepoException, ValidationException;

}
