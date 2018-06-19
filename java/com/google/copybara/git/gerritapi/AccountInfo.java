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
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.util.List;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#account-info */
@SuppressWarnings("unused")
@SkylarkModule(
    name = "gerritapi.AccountInfo",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    doc = "Gerrit account information.")
public class AccountInfo {
  @Key("_account_id") long accountId;
  @Key String name;
  @Key String email;
  @Key("secondary_emails") List<String> secondaryEmails;
  @Key String username;

  public AccountInfo() {}

  @VisibleForTesting
  public AccountInfo(long accountId, String email) {
    this.accountId = accountId;
    this.email = email;
  }

  public long getAccountId() {
    return accountId;
  }

  @SkylarkCallable(
      name = "account_id",
      doc = "The numeric ID of the account.",
      structField = true,
      allowReturnNones = true)
  public String getAccountIdAsString() {
    return Long.toString(accountId);
  }

  @SkylarkCallable(
      name = "name",
      doc =
          "The full name of the user.\n"
              + "Only set if detailed account information is requested.\n"
              + "See option DETAILED_ACCOUNTS for change queries\n"
              + "and option DETAILS for account queries.",
      structField = true,
      allowReturnNones = true)
  public String getName() {
    return name;
  }

  @SkylarkCallable(
      name = "email",
      doc =
          "The email address the user prefers to be contacted through.\n"
              + "Only set if detailed account information is requested.\n"
              + "See option DETAILED_ACCOUNTS for change queries\n"
              + "and options DETAILS and ALL_EMAILS for account queries.",
      structField = true,
      allowReturnNones = true)
  public String getEmail() {
    return email;
  }

  @SkylarkCallable(
      name = "secondary_emails",
      doc =
          "A list of the secondary email addresses of the user.\n"
              + "Only set for account queries when the ALL_EMAILS option or the suggest "
              + "parameter is set.\n"
              + "Secondary emails are only included if the calling user has the Modify Account, "
              + "and hence is allowed to see secondary emails of other users.",
      structField = true,
      allowReturnNones = true)
  public ImmutableList<String> getSecondaryEmails() {
    return ImmutableList.copyOf(secondaryEmails);
  }

  @SkylarkCallable(
      name = "username",
      doc =
          "The username of the user.\n"
              + "Only set if detailed account information is requested.\n"
              + "See option DETAILED_ACCOUNTS for change queries\n"
              + "and option DETAILS for account queries.",
      structField = true,
      allowReturnNones = true)
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
