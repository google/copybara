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

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Objects;

/**
 * A predicate represents an statement over a change. This is an approximation to
 * https://github.com/in-toto/attestation/tree/v0.1.0/spec#predicate predicates.
 *
 * <p>The predicate should be able to be serialized to a JSON object. This can be used to persist it
 * or to apply a rule engine that validates the statements. The JSON object should include type,
 * description and url fields. It should also be conformant with
 * https://github.com/in-toto/attestation/tree/v0.1.0/spec#predicate TODO(malcon): Make it really
 * conformant.
 *
 */
public abstract class StatementPredicate {
  @Key private final String type;
  @Key private final String description;
  @Key private final String url;

  public StatementPredicate(String type, String description, String url) {
    this.type = type;
    this.description = description;
    this.url = url;
  }

  /** Predicate type: Approval, ownership, etc. */
  public String type() {
    return type;
  }

  /** Text representation of the predicate for human consumption. */
  public String description() {
    return description;
  }

  /**
   * Returns where the predicate happened (E.g. an approval in GitHub would have
   * https://github.com/example/project/pull/123 as the url)
   */
  public String url() {
    return url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StatementPredicate)) {
      return false;
    }
    StatementPredicate that = (StatementPredicate) o;
    return Objects.equal(type, that.type)
        && Objects.equal(description, that.description)
        && Objects.equal(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, description, url);
  }

  @Override
  public final String toString() {
    return toStringHelper().toString();
  }

  protected ToStringHelper toStringHelper() {
    return MoreObjects.toStringHelper(this)
        .add("type", type)
        .add("description", description)
        .add("url", url);
  }
}
