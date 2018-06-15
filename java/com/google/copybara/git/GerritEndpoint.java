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
import com.google.copybara.git.gerritapi.ChangeInfo;
import com.google.copybara.git.gerritapi.ChangesQuery;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.GetChangeInput;
import com.google.copybara.git.gerritapi.IncludeResult;
import com.google.copybara.git.gerritapi.ReviewResult;
import com.google.copybara.git.gerritapi.SetReviewInput;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import javax.annotation.Nullable;

/** Gerrit endpoint implementation for feedback migrations. */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@SkylarkModule(
    name = "gerrit_api_obj",
    category = SkylarkModuleCategory.BUILTIN,
    documented = false,
    doc = "Gerrit API endpoint implementation for feedback migrations.")
public class GerritEndpoint implements Endpoint {

  private final LazyResourceLoader<GerritApi> apiSupplier;
  private final String url;
  @Nullable
  private final Console console;

  GerritEndpoint(LazyResourceLoader<GerritApi> apiSupplier, String url) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.console = null;
  }

  private GerritEndpoint(LazyResourceLoader<GerritApi> apiSupplier, String url, Console console) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.console = Preconditions.checkNotNull(console);
  }

  @SkylarkCallable(
    name = "get_change",
    doc = "Retrieve a Gerrit change.",
    parameters = {
      @Param(
          name = "id", type = String.class, named = true,
          doc = "The change id or change number."
      ),
      @Param(
        name = "include_results",
        named = true,
        type = SkylarkList.class,
        generic1 = String.class,
        doc =
            ""
                + "What to include in the response. See "
                + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html"
                + "#query-options",
        positional = false,
        defaultValue = "['LABELS']"
      ),
    },
    useLocation = true
  )
  public ChangeInfo getChange(String id, SkylarkList<?> includeResults, Location location)
      throws EvalException {
    try {
      ImmutableSet.Builder<IncludeResult> enumResults = ImmutableSet.builder();
      for (Object result : includeResults) {
        enumResults.add(
            SkylarkUtil.stringToEnum(
                null, "include_results", (String) result, IncludeResult.class));
      }
      ChangeInfo changeInfo = doGetChange(id, enumResults.build());
      ValidationException.checkCondition(
          !changeInfo.isMoreChanges(), "Pagination is not supported yet.");
      return changeInfo;
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, e);
    }
  }

  private ChangeInfo doGetChange(String changeId, ImmutableSet<IncludeResult> includeResults)
      throws RepoException, ValidationException {
    GerritApi gerritApi = apiSupplier.load(console);
    return gerritApi.getChange(changeId, new GetChangeInput(includeResults));
  }

  @SkylarkCallable(
      name = "post_review",
      doc =
          "Post a label to a Gerrit change for a particular revision. The label will be authored by"
              + " the user running the tool, or the role account if running in the service.\n",
      parameters = {
        @Param(
            name = "change_id",
            type = String.class,
            named = true,
            doc = "The Gerrit change id."),
        @Param(
            name = "revision_id",
            type = String.class,
            named = true,
            doc = "The revision for which the comment will be posted."),
        @Param(
            name = "review_input",
            type = SetReviewInput.class,
            doc = "The review to post to Gerrit.",
            named = true),
      },
      useLocation = true)
  public ReviewResult postLabel(
      String changeId, String revisionId, SetReviewInput reviewInput, Location location)
      throws EvalException {
    try {
      return doSetReview(changeId, revisionId, reviewInput);
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, e);
    }
  }

  private ReviewResult doSetReview(
      String changeId, String revisionId, SetReviewInput setReviewInput)
      throws RepoException, ValidationException {
    GerritApi gerritApi = apiSupplier.load(console);
    return gerritApi.setReview(changeId, revisionId, setReviewInput);
  }

  @SkylarkCallable(
      name = "list_changes_by_commit",
      doc =
          "Get changes from Gerrit based on a query."
              + " See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes.\n",
      parameters = {
        @Param(
            name = "commit",
            type = String.class,
            named = true,
            doc =
                "The commit sha to list changes by."
                    + " See https://gerrit-review.googlesource.com/Documentation/user-search.html#_basic_change_search."),
      },
      useLocation = true)
  public SkylarkList<ChangeInfo> listChanges(String commit, Location location)
      throws EvalException {
    try {
      GerritApi gerritApi = apiSupplier.load(console);
      return SkylarkList.createImmutable(
          gerritApi.getChanges(new ChangesQuery(String.format("commit:%s", commit))));
    } catch (RepoException | ValidationException | RuntimeException e) {
      throw new EvalException(location, e);
    }
  }

  @SkylarkCallable(
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
