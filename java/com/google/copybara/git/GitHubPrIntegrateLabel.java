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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.util.GitHubIdentifier;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Integrate label for GitHub PR
 *
 * <p>Format like: "https://github.com/google/copybara/pull/12345 from mikelalcon:master SHA-1"
 *
 * <p>Where SHA is optional: If present it means to integrate the specific SHA. Otherwise the head
 * of the PR is used.
 */
class GitHubPrIntegrateLabel implements IntegrateLabel {

  private static final Pattern LABEL_PATTERN =
      Pattern.compile(
          "(https://[.a-zA-Z0-9_/-]+)/pull/([0-9]+)"
              + " from ([^\\s\\r\\n]*)(?: ([0-9a-f]{7,64}))?");

  private final GitRepository repository;
  private final GeneralOptions generalOptions;

  private final GitHubIdentifier gitHubIdentifier;
  private final long prNumber;
  private final String originBranch;
  @Nullable private final String sha;

  GitHubPrIntegrateLabel(
      GitRepository repository,
      GeneralOptions generalOptions,
      GitHubIdentifier gitHubIdentifier,
      long prNumber,
      String originBranch,
      @Nullable String sha) {
    this.repository = Preconditions.checkNotNull(repository);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.gitHubIdentifier = Preconditions.checkNotNull(gitHubIdentifier);
    this.prNumber = prNumber;
    this.originBranch = Preconditions.checkNotNull(originBranch);
    this.sha = sha;
  }

  @Nullable
  static GitHubPrIntegrateLabel parse(
      String str, GitRepository repository, GeneralOptions generalOptions) {
    Matcher matcher = LABEL_PATTERN.matcher(str.trim());
    if (!matcher.matches()) {
      return null;
    }
    try {
      GitHubIdentifier labelIdentifier = GitHubIdentifier.create(matcher.group(1));
      return new GitHubPrIntegrateLabel(
          repository,
          generalOptions,
          labelIdentifier,
          Long.parseLong(matcher.group(2)),
          matcher.group(3),
          matcher.group(4));
    } catch (IllegalArgumentException e) {
      generalOptions
          .console()
          .warnFmt("Invalid GitHub URL in integrate label '%s': %s", str, e.getMessage());
      return null;
    }
  }

  boolean matchesDestinationUrl(String destinationUrl) {
    try {
      GitHubIdentifier destinationIdentifier = GitHubIdentifier.create(destinationUrl);
      if (!gitHubIdentifier.getUrl().equals(destinationIdentifier.getUrl())) {
        generalOptions
            .console()
            .warnFmt(
                "GitHub PR integrate label URL '%s' does not match destination URL '%s'",
                gitHubIdentifier.getUrl(), destinationIdentifier.getUrl());
        return false;
      }
      return true;
    } catch (IllegalArgumentException e) {
      generalOptions
          .console()
          .warnFmt(
              "Destination URL '%s' is not a valid GitHub URL for integrate label '%s': %s",
              destinationUrl, this, e.getMessage());
      return false;
    }
  }

  @Override
  public String toString() {
    return String.format(
        "https://%s/%s/pull/%d from %s%s",
        gitHubIdentifier.getHostName(),
        getProjectId(),
        prNumber,
        originBranch,
        sha != null ? " " + sha : "");
  }

  @Override
  public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) throws ValidationException {
    return IntegrateLabel.withLabels(String.format("Merge pull request #%d from %s",
        prNumber, originBranch), labelsToAdd);
  }

  @Override
  public GitRevision getRevision() throws RepoException, ValidationException {
    String pr =
        "https://" + gitHubIdentifier.getHostName() + "/" + getProjectId() + "/pull/" + prNumber;
    String repoUrl = "https://" + gitHubIdentifier.getHostName() + "/" + getProjectId();
    GitRevision gitRevision = GitRepoType.GITHUB.resolveRef(repository, repoUrl, pr,
        generalOptions, /*describeVersion=*/ false, /*partialFetch*/ false, Optional.empty());
    if (sha == null) {
      return gitRevision;
    }
    if (sha.equals(gitRevision.getHash())) {
      return gitRevision;
    }
    generalOptions
        .console()
        .warnFmt(
            "Pull Request %s has more changes after %s (PR HEAD is %s)."
                + " Not all changes might be migrated",
            pr, sha, gitRevision.getHash());
    return repository.resolveReferenceWithContext(sha, gitRevision.contextReference(), repoUrl);
  }

  public String getProjectId() {
    return gitHubIdentifier.getPath();
  }

  public long getPrNumber() {
    return prNumber;
  }

  public String getOriginBranch() {
    return originBranch;
  }
}
