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

package com.google.copybara.approval;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.Objects;

/** An approval from a trusted user. */
public class Approval {

  private final String userName;
  private final ApprovalType type;
  private final String approvalUrl;
  private final String approvalInfo;

  public Approval(String userName, ApprovalType type, String approvalUrl,
      String approvalInfo) {
    this.userName = Preconditions.checkNotNull(userName);
    this.type = Preconditions.checkNotNull(type);
    this.approvalUrl = Preconditions.checkNotNull(approvalUrl);
    this.approvalInfo = Preconditions.checkNotNull(approvalInfo);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("ldap", userName)
        .add("type", type)
        .add("approvalUrl", approvalUrl)
        .add("approvalInfo", approvalInfo)
        .toString();
  }

  /**
   * The user that approved or LGTM the change
   */
  public String getUserName() {
    return userName;
  }

  /**
   * Type of approval
   */
  public ApprovalType getType() {
    return type;
  }

  /**
   * Raw informational data of the approval. For example "Code-Review+2"
   */
  public String getApprovalInfo() {
    return approvalInfo;
  }

  /**
   * Url of the host or review
   */
  public String approvalUrl() {
    return approvalUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Approval approval = (Approval) o;
    return Objects.equals(userName, approval.userName)
        && type == approval.type
        && Objects.equals(approvalUrl, approval.approvalUrl)
        && Objects.equals(approvalInfo, approval.approvalInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userName, type, approvalUrl, approvalInfo);
  }

  /**
   * The type of approval the change has
   */
  public enum ApprovalType {
    OWNER,
    // TODO(malcon): Rename this. We might need more fine-grain types (iow a string), and apply
    // a policy in the destination.
    LGTM
  }
}
