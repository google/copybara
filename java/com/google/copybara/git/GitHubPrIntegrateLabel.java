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
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import javax.annotation.Nullable;

/**
 * Integrate label for GitHub PR
 *
 * <p>Format like: "https://github.com/google/copybara/pull/12345 from mikelalcon:master SHA-1"
 *
 * <p>Where SHA-1 is optional: If present it means to integrate the specific SHA-1. Otherwise the
 * head of the PR is used.
 */
class GitHubPrIntegrateLabel implements IntegrateLabel {

  private static final Pattern LABEL_PATTERN =
      Pattern.compile(
          "https://github.com/([a-zA-Z0-9_/-]+)/pull/([0-9]+) from ([^\\s\\r\\n]*)(?: ([0-9a-f]{7,40}))?");

  private final GitRepository repository;
  private final GeneralOptions generalOptions;

  private final String projectId;
  private final long prNumber;
  private final String originBranch;
  @Nullable
  private final String sha1;

  GitHubPrIntegrateLabel(GitRepository repository, GeneralOptions generalOptions, String projectId,
      long prNumber, String originBranch, @Nullable String sha1) {
    this.repository = Preconditions.checkNotNull(repository);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.projectId = Preconditions.checkNotNull(projectId);
    this.prNumber = prNumber;
    this.originBranch = Preconditions.checkNotNull(originBranch);
    this.sha1 = sha1;
  }

  @Nullable
  static GitHubPrIntegrateLabel parse(String str, GitRepository repository,
      GeneralOptions generalOptions) {
    Matcher matcher = LABEL_PATTERN.matcher(str);
    return matcher.matches()
           ? new GitHubPrIntegrateLabel(repository, generalOptions,
                                        matcher.group(1),
                                        Long.parseLong(matcher.group(2)),
                                        matcher.group(3),
                                        matcher.group(4))
           : null;
  }

  @Override
  public String toString() {
    return String.format("https://github.com/%s/pull/%d from %s%s", projectId, prNumber,
        originBranch, sha1 != null ? " " + sha1 : "");
  }

  @Override
  public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) {
    return IntegrateLabel.withLabels(String.format("Merge pull request #%d from %s",
        prNumber, originBranch), labelsToAdd);
  }

  @Override
  public GitRevision getRevision() throws RepoException, ValidationException {
    String pr = "https://github.com/" + projectId + "/pull/" + prNumber;
    String repoUrl = "https://github.com/" + projectId;
    GitRevision gitRevision = GitRepoType.GITHUB.resolveRef(repository, repoUrl, pr,
        generalOptions, /*describeVersion=*/ false, /*partialFetch*/ false);
    if (sha1 == null) {
      return gitRevision;
    }
    if (sha1.equals(gitRevision.getSha1())) {
      return gitRevision;
    }
    generalOptions.console().warnFmt(
        "Pull Request %s has more changes after %s (PR HEAD is %s)."
            + " Not all changes might be migrated", pr, sha1, gitRevision.getSha1());
    return repository.resolveReferenceWithContext(sha1, gitRevision.contextReference(), repoUrl);
  }

  public String getProjectId() {
    return projectId;
  }

  public long getPrNumber() {
    return prNumber;
  }

  public String getOriginBranch() {
    return originBranch;
  }
}
