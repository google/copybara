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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.BaselinesWithoutLabelVisitor;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import javax.annotation.Nullable;

/**
 * A {@link Origin} that can read Gerrit reviews TODO(malcon): Implement Reader/getChanges to detect
 * already migrated patchets
 */
public class GerritOrigin extends GitOrigin {

  private final GeneralOptions generalOptions;
  private final GitOptions gitOptions;
  private final GitOriginOptions gitOriginOptions;
  private final SubmoduleStrategy submoduleStrategy;
  private final boolean includeBranchCommitLogs;

  private GerritOrigin(
      GeneralOptions generalOptions,
      String repoUrl,
      @Nullable String configRef,
      GitOptions gitOptions,
      GitOriginOptions gitOriginOptions,
      SubmoduleStrategy submoduleStrategy,
      boolean includeBranchCommitLogs,
      boolean firstParent) {
    super(
        generalOptions,
        repoUrl,
        configRef,
        GitRepoType.GERRIT,
        gitOptions,
        gitOriginOptions,
        submoduleStrategy,
        includeBranchCommitLogs,
        firstParent);
    this.generalOptions = generalOptions;
    this.gitOptions = gitOptions;
    this.gitOriginOptions = gitOriginOptions;
    this.submoduleStrategy = submoduleStrategy;
    this.includeBranchCommitLogs = includeBranchCommitLogs;
  }

  @Override
  public GitRevision resolve(@Nullable String reference) throws RepoException, ValidationException {
    generalOptions.console().progress("Git Origin: Initializing local repo");

    checkCondition(!Strings.isNullOrEmpty(reference), "Expecting a change number as reference");
    return GitRepoType.GERRIT.resolveRef(getRepository(), repoUrl, reference, this.generalOptions);
  }

  /** Builds a new {@link GerritOrigin}. */
  static GerritOrigin newGerritOrigin(
      Options options, String url, SubmoduleStrategy submoduleStrategy, boolean firstParent) {

    return new GerritOrigin(
        options.get(GeneralOptions.class),
        url,
        /* configRef= */ null,
        options.get(GitOptions.class),
        options.get(GitOriginOptions.class),
        submoduleStrategy,
        /*includeBranchCommitLogs=*/ false,
        firstParent);
  }

  @Override
  public Reader<GitRevision> newReader(Glob originFiles, Authoring authoring) {
    return new GitOrigin.ReaderImpl(repoUrl, originFiles, authoring, gitOptions, gitOriginOptions,
        generalOptions, includeBranchCommitLogs, submoduleStrategy, firstParent) {
      /**
       * Group identity is the individual change identity for now. If we want to group a list of
       * commits we would add Gerrit topic support and an option to git.gerrit_origin to enable it.
       */
      @Nullable
      @Override
      public String getGroupIdentity(GitRevision rev) throws RepoException {
        return rev.contextReference();
      }

      @Override
      public ImmutableList<GitRevision> findBaselinesWithoutLabel(
          GitRevision startRevision, int limit)
          throws RepoException, CannotResolveRevisionException {

        // Skip the first change as it is the Gerrit review change
        BaselinesWithoutLabelVisitor<GitRevision> visitor =
            new BaselinesWithoutLabelVisitor<>(originFiles, limit, /*skipFirst=*/ true);
        visitChanges(startRevision, visitor);
        return visitor.getResult();
      }
    };
  }
}
