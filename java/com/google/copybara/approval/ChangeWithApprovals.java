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
import com.google.common.collect.ImmutableList;
import com.google.copybara.revision.Change;
import java.util.Objects;

/**
 * Approvals for a change reference
 */
public class ChangeWithApprovals {

  private final Change<?> change;
  private final ImmutableList<Approval> approvals;

  public ChangeWithApprovals(Change<?> change, ImmutableList<Approval> approvals) {
    this.change = Preconditions.checkNotNull(change);
    this.approvals = Preconditions.checkNotNull(approvals);
  }

  public Change<?> getChange() {
    return change;
  }

  public ImmutableList<Approval> getApprovals() {
    return approvals;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("change", change)
        .add("approvals", approvals)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChangeWithApprovals that = (ChangeWithApprovals) o;
    return Objects.equals(change, that.change)
        && Objects.equals(approvals, that.approvals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(change, approvals);
  }
}
