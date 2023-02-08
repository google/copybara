/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.git.gitlab.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.reflect.TypeToken;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;

public class GitLabApi {
  private final GitLabApiTransport transport;
  private final Profiler profiler;

  public static final int MAX_PER_PAGE = 100;
  private static final int MAX_PAGES = 60000;

  public GitLabApi(GitLabApiTransport transport, Profiler profiler) {
    this.transport = transport;
    this.profiler = profiler;
  }

  public ImmutableList<MergeRequest> getMergeRequests(String projectId, String sourceBranch) throws ValidationException, RepoException {
    ImmutableListMultimap.Builder<String, String> params = ImmutableListMultimap.builder();
    if (sourceBranch != null) {
      params.put("source_branch", sourceBranch);
    }

    return paginatedGet(String.format("projects/%s/merge_requests", escape(projectId)),
        "gitlab_api_list_merge_requests",
        new TypeToken<List<MergeRequest>>() {
        }.getType(), "MergeRequest",
        ImmutableListMultimap.of(),
        params.build());
  }

  private static String escape(String query) {
    try {
      return URLEncoder.encode(query, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Shouldn't fail", e);
    }
  }

  public MergeRequest updateMergeRequest(String projectId, long iid, String title, String description, String state_event) throws ValidationException, RepoException {
    ImmutableListMultimap.Builder<String, String> params = ImmutableListMultimap.builder();
    if (title != null) {
      params.put("title", title);
    }
    if (description != null) {
      params.put("description", description);
    }
    if (state_event != null) {
      params.put("state_event", state_event);
    }

    try (Profiler.ProfilerTask ignore = profiler.start("gitlab_api_update_merge_request")) {
      return transport.put(
          String.format("projects/%s/merge_requests/%d", escape(projectId), iid), MergeRequest.class, params.build());
    }
  }

  public MergeRequest createMergeRequest(String projectId, String title, String description, String sourceBranch, String targetBranch) throws ValidationException, RepoException {
    ImmutableListMultimap.Builder<String, String> params = ImmutableListMultimap.builder();
    if (title != null) {
      params.put("title", title);
    }
    if (description != null) {
      params.put("description", description);
    }
    if (sourceBranch != null) {
      params.put("source_branch", sourceBranch);
    }
    if (targetBranch != null) {
      params.put("target_branch", targetBranch);
    }

    try (Profiler.ProfilerTask ignore = profiler.start("gitlab_api_create_mr")) {
      return transport.post(
          String.format("projects/%s/merge_requests", escape(projectId)), MergeRequest.class, params.build());
    }
  }

  private <T> ImmutableList<T> paginatedGet(String path, String profilerName, Type type,
                                            String entity, ImmutableListMultimap<String, String> headers,
                                            ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    int pages = 1;
    while (path != null && pages <= MAX_PAGES) {
      try (Profiler.ProfilerTask ignore = profiler.start(String.format("%s_page_%d", profilerName, pages))) {
        ImmutableListMultimap<String, String> requestParams = ImmutableListMultimap.<String, String>builder()
            .putAll(params)
            .put("page", String.valueOf(pages))
            .put("per_page", String.valueOf(MAX_PER_PAGE))
            .build();

        List<T> page = transport.get(path, type, headers, requestParams);
        if (page.isEmpty()) {
          break;
        }
        builder.addAll(page);
        pages++;
      }
    }
    return builder.build();
  }
}
