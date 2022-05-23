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
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Collection;
import java.util.Objects;

/**
 * Approvals for a change reference TODO(malcon): Rename this to Statement
 * (https://github.com/in-toto/attestation/tree/v0.1.0/spec#predicate)
 */
public class ChangeWithApprovals {

  private final Change<?> change;
  private final ImmutableList<? extends StatementPredicate> predicates;

  public ChangeWithApprovals(Change<?> change) {
    this(change, ImmutableList.of());
  }

  public ChangeWithApprovals(
      Change<?> change, ImmutableList<? extends StatementPredicate> predicates) {
    this.change = Preconditions.checkNotNull(change);
    this.predicates = predicates;
  }

  public Change<?> getChange() {
    return change;
  }

  public ImmutableList<? extends StatementPredicate> getPredicates() {
    return predicates;
  }

  @CheckReturnValue
  public ChangeWithApprovals addApprovals(Collection<? extends StatementPredicate> approvals) {
    return new ChangeWithApprovals(
        change,
        ImmutableList.<StatementPredicate>builder().addAll(predicates).addAll(approvals).build());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("change", change)
        .add("predicates", predicates)
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
    return Objects.equals(change, that.change) && Objects.equals(predicates, that.predicates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(change, predicates);
  }
}
