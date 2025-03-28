/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab.api.entities;

import com.google.api.client.util.Key;
import com.google.api.client.util.Value;
import com.google.common.annotations.VisibleForTesting;

/**
 * Represents a GitLab Merge Request.
 *
 * @see <a
 *     href="https://docs.gitlab.com/api/merge_requests/#response">https://docs.gitlab.com/api/merge_requests/#response</a>
 */
public class MergeRequest implements GitLabApiEntity {
  @Key private int id;
  @Key private int iid;
  @Key private String sha;

  @Key("detailed_merge_status")
  private DetailedMergeStatus detailedMergeStatus;

  @Key("source_branch")
  private String sourceBranch;

  @Key private State state;

  @Key("web_url")
  private String webUrl;

  /** Creates a new instance of {@link MergeRequest}. */
  public MergeRequest() {}

  /**
   * Constructs a new instance of {@link MergeRequest} with the given parameters.
   *
   * @param id the ID of the GitLab project
   * @param iid the internal ID of the merge request
   * @param sha the SHA1 of the MR's head commit
   * @param detailedMergeStatus the merge status of the commit
   * @param sourceBranch the source branch of the MR
   * @param webUrl the web URL of the MR
   * @param state the state of the MR
   */
  @VisibleForTesting
  public MergeRequest(
      int id,
      int iid,
      String sha,
      DetailedMergeStatus detailedMergeStatus,
      String sourceBranch,
      String webUrl,
      State state) {
    this.id = id;
    this.iid = iid;
    this.sha = sha;
    this.detailedMergeStatus = detailedMergeStatus;
    this.sourceBranch = sourceBranch;
    this.webUrl = webUrl;
    this.state = state;
  }

  /**
   * Returns the ID of the merge request. When querying for an MR, use {@link #getIid()} instead.
   *
   * @return the ID
   */
  public int getId() {
    return id;
  }

  /**
   * Returns the internal ID (iid) of the merge request. When querying for an MR, this is the ID
   * that GitLab expects.
   *
   * @return the internal ID
   */
  public int getIid() {
    return iid;
  }

  /**
   * Returns the detailed merge status of the merge request.
   *
   * @return the status
   * @see <a href="https://docs.gitlab.com/api/merge_requests/#merge-status">GitLab Merge status
   *     docs</a>
   */
  public DetailedMergeStatus getDetailedMergeStatus() {
    return detailedMergeStatus;
  }

  /**
   * Returns the name of the source branch of the merge request.
   *
   * @return the source branch
   */
  public String getSourceBranch() {
    return sourceBranch;
  }

  /**
   * Returns the Web URL of the merge request.
   *
   * @return the web URL
   */
  public String getWebUrl() {
    return webUrl;
  }

  /**
   * Returns the diff head SHA of the merge request.
   *
   * @return the diff head SHA
   */
  public String getSha() {
    return sha;
  }

  /**
   * Returns the state of the merge request.
   *
   * @return the state
   */
  public State getState() {
    return state;
  }

  /**
   * Represents all possible merge statuses for a merge request.
   *
   * @see <a href="https://docs.gitlab.com/api/merge_requests/#merge-status">GitLab Merge status
   *     docs</a> for a list of merge statuses.
   */
  public enum DetailedMergeStatus {
    @Value("approvals_syncing")
    APPROVALS_SYNCING,
    @Value("checking")
    CHECKING,
    @Value("ci_must_pass")
    CI_MUST_PASS,
    @Value("ci_still_running")
    CI_STILL_RUNNING,
    @Value("commits_status")
    COMMITS_STATUS,
    @Value("conflict")
    CONFLICT,
    @Value("discussions_not_resolved")
    DISCUSSIONS_NOT_RESOLVED,
    @Value("draft_status")
    DRAFT_STATUS,
    @Value("jira_association_missing")
    JIRA_ASSOCIATION_MISSING,
    @Value("mergeable")
    MERGEABLE,
    @Value("merge_request_blocked")
    MERGE_REQUEST_BLOCKED,
    @Value("merge_time")
    MERGE_TIME,
    @Value("need_rebase")
    NEED_REBASE,
    @Value("not_approved")
    NOT_APPROVED,
    @Value("not_open")
    NOT_OPEN,
    @Value("preparing")
    PREPARING,
    @Value("requested_changes")
    REQUESTED_CHANGES,
    @Value("security_policy_violations")
    SECURITY_POLICY_VIOLATIONS,
    @Value("status_checks_must_pass")
    STATUS_CHECKS_MUST_PASS,
    @Value("unchecked")
    UNCHECKED,
    @Value("locked_paths")
    LOCKED_PATHS,
    @Value("locked_lfs_files")
    LOCKED_LFS_FILES
  }

  /**
   * Represents the possible states of a merge request.
   *
   * @see <a href="https://docs.gitlab.com/api/merge_requests/#response">Get Single MR response
   *     documentation</a> for more information
   */
  public enum State {
    @Value("opened")
    OPENED,
    @Value("closed")
    CLOSED,
    @Value("merged")
    MERGED,
    @Value("locked")
    LOCKED
  }
}
