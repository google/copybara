/*
 * Copyright (C) 2018 Google Inc.
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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.WriterImpl.DefaultWriteHook;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.git.github.api.GitHubApiException;
import com.google.copybara.git.github.api.GitHubApiException.ResponseCode;
import com.google.copybara.git.github.util.GitHubUtil;
import com.google.copybara.transform.metadata.LabelTemplate;
import com.google.copybara.transform.metadata.LabelTemplate.LabelNotFoundException;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import java.util.List;
import javax.annotation.Nullable;

public class GitHubWriteHook extends DefaultWriteHook {
  private final String repoUrl;
  private final GeneralOptions generalOptions;
  private final GitHubOptions gitHubOptions;
  private final Console console;
  @Nullable
  private final String prBranchToUpdate;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public GitHubWriteHook(
      GeneralOptions generalOptions,
      String repoUrl,
      GitHubOptions gitHubOptions,
      String prBranchToUpdate,
      Console console) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.gitHubOptions = Preconditions.checkNotNull(gitHubOptions);
    this.prBranchToUpdate = prBranchToUpdate;
    this.console = console;
  }

  @Override
  public void beforePush(
      GitRepository scratchClone,
      MessageInfo messageInfo,
      boolean skipPush,
      List<? extends Change<?>> originChanges)
      throws ValidationException, RepoException {
    if (skipPush || prBranchToUpdate == null) {
      return;
    }
    String configProjectName = GitHubUtil.getProjectNameFromUrl(repoUrl);
    GitHubApi api = gitHubOptions.newGitHubApi(configProjectName);

    for (Change<?> change : originChanges) {
      SkylarkDict<String, String> labelDict = change.getLabelsForSkylark();
      String updatedPrBranchName = getUpdatedPrBranch(labelDict);
      try {
        //fails with NOT_FOUND if doesn't exist
        api.getReference(configProjectName, updatedPrBranchName);
        generalOptions.repoTask(
            "push current commit to the head of pr_branch_to_update",
            () ->
                scratchClone
                    .push()
                    .withRefspecs(
                        repoUrl,
                        ImmutableList.of(
                            scratchClone.createRefSpec("+HEAD:" + updatedPrBranchName)))
                    .run());
      } catch (GitHubApiException e) {
        if (e.getResponseCode() == ResponseCode.NOT_FOUND) {
          console.infoFmt("Branch %s does not exist", updatedPrBranchName);
          logger.atInfo().log("Branch %s does not exist", updatedPrBranchName);
          continue;
        }
        throw e;
      }
    }
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return prBranchToUpdate == null
        ? ImmutableSetMultimap.of()
        : ImmutableSetMultimap.of("pr_branch_to_update", prBranchToUpdate);
  }

  private String getUpdatedPrBranch(SkylarkDict<String, String> labelDict)
      throws ValidationException {
    try{
      return new LabelTemplate(prBranchToUpdate).resolve(e -> labelDict.get(e));
    } catch (LabelNotFoundException e) {
      throw new ValidationException(
          e,
          "Template '%s' has an error: %s", prBranchToUpdate, e.getMessage());
    }
  }
}
