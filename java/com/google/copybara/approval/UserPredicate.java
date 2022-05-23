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
import com.google.api.client.util.Value;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Objects;

/** Defines a predicate over a user action */
public class UserPredicate extends StatementPredicate {

  @Key("username")
  private final String username;

  private final UserPredicateType type;

  public UserPredicate(
      String username, UserPredicateType userType, String originUrl, String description) {
    super(userType.toString(), description, originUrl);
    this.username = username;
    this.type = userType;
  }

  /**
   * String representing the username for the user predicate. (E.g. the username of the approver,
   * owners, etc.).
   */
  public String username() {
    return username;
  }

  public UserPredicateType userType() {
    return type;
  }

  /** Type of user predicate */
  public enum UserPredicateType {
    // username is the owner of the change
    @Value("owner")
    OWNER,
    // username has approved the change
    // Called LGTM for historical reasons (used internally)
    @Value("approval")
    LGTM
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserPredicate)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    UserPredicate that = (UserPredicate) o;
    return Objects.equal(username, that.username) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), username, type);
  }

  @Override
  protected ToStringHelper toStringHelper() {
    return super.toStringHelper().add("username", username).add("type", type);
  }
}
