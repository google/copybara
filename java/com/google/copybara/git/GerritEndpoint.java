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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gerritapi.ActionInfo;
import com.google.copybara.git.gerritapi.ChangeInfo;
import com.google.copybara.git.gerritapi.ChangesQuery;
import com.google.copybara.git.gerritapi.DeleteVoteInput;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.GerritApiException;
import com.google.copybara.git.gerritapi.GetChangeInput;
import com.google.copybara.git.gerritapi.IncludeResult;
import com.google.copybara.git.gerritapi.NotifyType;
import com.google.copybara.git.gerritapi.ReviewResult;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.util.console.Console;
import java.util.Map;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** Gerrit endpoint implementation for feedback migrations. */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@StarlarkBuiltin(
    name = "gerrit_api_obj",
    doc = "Gerrit API endpoint implementation for feedback migrations and after migration hooks.")
public class GerritEndpoint implements Endpoint, StarlarkValue {

  private final LazyResourceLoader<GerritApi> apiSupplier;
  private final String url;
  private final Console console;

  GerritEndpoint(LazyResourceLoader<GerritApi> apiSupplier, String url, Console console) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.console = Preconditions.checkNotNull(console);
  }

  @StarlarkMethod(
      name = "get_change",
      doc = "Retrieve a Gerrit change.",
      parameters = {
        @Param(name = "id", named = true, doc = "The change id or change number."),
        @Param(
            name = "include_results",
            named = true,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            doc =
                ""
                    + "What to include in the response. See "
                    + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html"
                    + "#query-options",
            positional = false,
            defaultValue = "['LABELS']"),
      })
  public ChangeInfo getChange(String id, Sequence<?> includeResults) throws EvalException {
    try {
      ChangeInfo changeInfo = doGetChange(id, getIncludeResults(includeResults));
      ValidationException.checkCondition(
          !changeInfo.isMoreChanges(), "Pagination is not supported yet.");
      return changeInfo;
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException("Error getting change: " + e.getMessage(), e);
    }
  }

  @StarlarkMethod(
      name = "get_actions",
      doc = "Retrieve the actions of a Gerrit change.",
      parameters = {
        @Param(name = "id", named = true, doc = "The change id or change number."),
        @Param(name = "revision", named = true, doc = "The revision of the change."),
      })
  public Map<String, ActionInfo> getActions(String id, String revision) throws EvalException {
    try {
      GerritApi gerritApi = apiSupplier.load(console);
      return gerritApi.getActions(id, revision);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException("Error getting actions: " + e.getMessage(), e);
    }
  }

  private ImmutableSet<IncludeResult> getIncludeResults(Sequence<?> includeResults)
      throws EvalException {
    ImmutableSet.Builder<IncludeResult> enumResults = ImmutableSet.builder();
    for (Object result : includeResults) {
      enumResults.add(
          SkylarkUtil.stringToEnum("include_results", (String) result, IncludeResult.class));
    }
    return enumResults.build();
  }

  private ChangeInfo doGetChange(String changeId, ImmutableSet<IncludeResult> includeResults)
      throws RepoException, ValidationException {
    GerritApi gerritApi = apiSupplier.load(console);
    return gerritApi.getChange(changeId, new GetChangeInput(includeResults));
  }

  @StarlarkMethod(
      name = "post_review",
      doc =
          "Post a review to a Gerrit change for a particular revision. The review will be authored "
              + "by the user running the tool, or the role account if running in the service.\n",
      parameters = {
        @Param(name = "change_id", named = true, doc = "The Gerrit change id."),
        @Param(
            name = "revision_id",
            named = true,
            doc = "The revision for which the comment will be posted."),
        @Param(name = "review_input", doc = "The review to post to Gerrit.", named = true),
      })
  public ReviewResult postReview(String changeId, String revisionId, SetReviewInput reviewInput)
      throws EvalException {
    try {
      GerritApi gerritApi = apiSupplier.load(console);
      return gerritApi.setReview(changeId, revisionId, reviewInput);
    } catch (GerritApiException re) {
      if (re.getGerritResponseMsg().matches("(?s).*Applying label \"\\w+\":.*is restricted.*")) {
        throw new EvalException(
            "Permission error calling post_review",
            new ValidationException(
                "Gerrit returned a permission error while attempting to post a review:\n"
                    + re.getMessage(),
                re));
      }
      throw new EvalException("Error calling post_review", re);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException("Error calling post_review: " + e.getMessage(), e);
    }
  }

  @StarlarkMethod(
      name = "delete_vote",
      doc = "Delete a label vote from an account owner on a Gerrit change.\n",
      parameters = {
        @Param(name = "change_id", named = true, doc = "The Gerrit change id."),
        @Param(
            name = "account_id",
            named = true,
            doc =
                "The account owner who votes on label_id. Use 'me' or 'self' "
                    + "if the account owner makes this api call"),
        @Param(name = "label_id", named = true, doc = "The name of the label."),
      })
  public void deleteVote(String changeId, String accountId, String labelId) throws EvalException {
    try {
      GerritApi gerritApi = apiSupplier.load(console);
      gerritApi.deleteVote(changeId, accountId, labelId, new DeleteVoteInput(NotifyType.NONE));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException("Error calling delete_vote: " + e.getMessage(), e);
    }
  }

  @StarlarkMethod(
      name = "list_changes",
      doc =
          "Get changes from Gerrit based on a query. See"
              + " https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes.\n",
      parameters = {
        @Param(
            name = "query",
            named = true,
            doc =
                "The query string to list changes by. See"
                    + " https://gerrit-review.googlesource.com/Documentation/user-search.html#_basic_change_search."),
        @Param(
            name = "include_results",
            named = true,
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            doc =
                ""
                    + "What to include in the response. See "
                    + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html"
                    + "#query-options",
            positional = false,
            defaultValue = "[]"),
      })
  public Sequence<ChangeInfo> listChanges(String queryString, Sequence<?> includeResults)
      throws EvalException, RepoException, ValidationException {
    GerritApi gerritApi = apiSupplier.load(console);
    return StarlarkList.immutableCopyOf(
        gerritApi.getChanges(
            new ChangesQuery(queryString)
                .withInclude(getIncludeResults(includeResults))));
  }

  @StarlarkMethod(
      name = "url",
      doc = "Return the URL of this endpoint.",
      structField = true)
  public String getUrl() {
    return url;
  }

  @Override
  public GerritEndpoint withConsole(Console console) {
    return new GerritEndpoint(this.apiSupplier, this.url, console);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "gerrit_api", "url", url);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .toString();
  }
}
