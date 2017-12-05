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

import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info
 */
public class AccountInfo {
  @Key("_account_id") long accountId;
  @Key String name;
  @Key String email;
  @Key("secondary_emails") List<String> secondaryEmails;
  @Key String username;

  public AccountInfo() {
  }

  @VisibleForTesting
  public AccountInfo(long accountId, String email) {
    this.accountId = accountId;
    this.email = email;
  }

  public long getAccountId() {
    return accountId;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public ImmutableList<String> getSecondaryEmails() {
    return ImmutableList.copyOf(secondaryEmails);
  }

  public String getUsername() {
    return username;
  }

  @Override
  public String toString() {
    return getToStringHelper().toString();
  }

  public ToStringHelper getToStringHelper() {
    return MoreObjects.toStringHelper(this)
        .add("accountId", accountId)
        .add("name", name)
        .add("email", email)
        .add("secondaryEmails", secondaryEmails)
        .add("username", username);
  }
}
