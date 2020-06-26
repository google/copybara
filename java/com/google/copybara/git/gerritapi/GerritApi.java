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

package com.google.copybara.git.gerritapi;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.util.List;
import java.util.Map;

/**
 * A mini API for getting and updating Gerrit projects through the Gerrit REST API.
 */
public class GerritApi {

  protected final GerritApiTransport transport;
  protected final Profiler profiler;

  public GerritApi(GerritApiTransport transport, Profiler profiler) {
    this.transport = Preconditions.checkNotNull(transport);
    this.profiler = Preconditions.checkNotNull(profiler);
  }

  public List<ChangeInfo> getChanges(ChangesQuery query)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_get_changes")) {
      List<ChangeInfo> result = transport.get("/changes/?" + query.asUrlParams(),
                                              new TypeToken<List<ChangeInfo>>() {}.getType());
      return ImmutableList.copyOf(result);
    }
  }

  public ChangeInfo getChange(String changeId, GetChangeInput input)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_get_change")) {
      return transport.get("/changes/" + changeId + "?" + input.asUrlParams(), ChangeInfo.class);
    }
  }

  public ChangeInfo getChangeDetail(String changeId, GetChangeInput input)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_get_change_detail")) {
      return transport.get("/changes/" + changeId + "/detail?" + input.asUrlParams(),
          ChangeInfo.class);
    }
  }

  public ChangeInfo abandonChange(String changeId, AbandonInput abandonInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_abandon_change")) {
      return transport.post("/changes/" + changeId + "/abandon", abandonInput, ChangeInfo.class);
    }
  }

  public ChangeInfo restoreChange(String changeId, RestoreInput restoreInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_restore_change")) {
      return transport.post("/changes/" + changeId + "/restore", restoreInput, ChangeInfo.class);
    }
  }

  public Map<String, ProjectInfo> listProjects(ListProjectsInput listProjectsInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_list_projects")) {
      return transport.get("/projects/?" + listProjectsInput.asUrlParams(),
          new TypeToken<Map<String, ProjectInfo>>() {}.getType());
    }
  }

  public ProjectInfo createProject(String project) throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_create_project")) {
      return transport.put("/projects/" + escape(project), new ProjectInput(),
          new TypeToken<ProjectInfo>() {}.getType());
    }
  }

  private String escape(String project) throws ValidationException {
    // Gerrit does a good validation in the server side, but we do some basic checks
    checkCondition(!project.contains(" "), "Invalid project name, has spaces: '%s'", project);
    return project.replace("/", "%2F");
  }

  public ProjectAccessInfo getAccessInfo(String project)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_access")) {
      return transport.get("/projects/" + project + "/access", ProjectAccessInfo.class);
    }
  }

  public ReviewResult setReview(String changeId, String revisionId, SetReviewInput setReviewInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_set_review")) {
      return transport.post(
          "/changes/" + changeId + "/revisions/" + revisionId + "/review",
          setReviewInput,
          ReviewResult.class);
    }
  }

  public void deleteReviewer(String changeId, long accountId,
      DeleteReviewerInput deleteReviewerInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_delete_reviewer")) {
      transport.post(
          "/changes/" + changeId + "/reviewers/" + accountId + "/delete",
          deleteReviewerInput, Empty.class);
    }
  }

  public void addReviewer(String changeId, ReviewerInput reviewerInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_add_reviewer")) {
      transport.post(
          "/changes/" + changeId + "/reviewers",
          reviewerInput, Empty.class);
    }
  }

  public AccountInfo getSelfAccount()
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_get_self")) {
      return transport.get("/accounts/self", AccountInfo.class);
    }
  }

  public Map<String, ActionInfo> getActions(String changeId, String revision)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_get_actions")) {
      return transport.get("/changes/" +  changeId + "/revisions/" +  revision + "/actions",
          new TypeToken<Map<String, ActionInfo>>() {}.getType());
    }
  }

  public void deleteVote(String changeId, String accountId, String  labelId,
      DeleteVoteInput deleteVoteInput) throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_delete_reviewer_vote")) {
      transport.post(
          "/changes/" + changeId + "/reviewers/" + accountId + "/votes/"  + labelId + "/delete",
          deleteVoteInput, Empty.class);
    }
  }

  public ChangeInfo submitChange(String changeId, SubmitInput submitInput)
      throws RepoException, ValidationException {
    try (ProfilerTask ignore = profiler.start("gerrit_submit_change")) {
      return transport.post("/changes/" + changeId + "/submit", submitInput, ChangeInfo.class);
    }
  }
}
