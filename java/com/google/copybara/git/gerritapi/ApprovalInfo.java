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
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#approval-info */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.ApprovalInfo",
    doc = "Gerrit approval information.")
public class ApprovalInfo extends AccountInfo implements StarlarkValue {
  @Key int value;
  @Key String date;

  @StarlarkMethod(
      name = "value",
      doc =
          "The vote that the user has given for the label. If present and zero, the user "
              + "is permitted to vote on the label. If absent, the user is not permitted to vote "
              + "on that label.",
      structField = true)
  public int getValue() {
    return value;
  }

  public ZonedDateTime getDate() {
    return GerritApiUtil.parseTimestamp(date);
  }

  @StarlarkMethod(
      name = "date",
      doc = "The time and date describing when the approval was made.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getDateForSkylark() {
    return date;
  }

  public ApprovalInfo() {}

  @VisibleForTesting
  public ApprovalInfo(long accountId, String email, int value) {
    super(accountId, email);
    this.value = value;
  }

  @Override
  public String toString() {
    return getToStringHelper()
        .add("value", value)
        .add("date", date)
        .toString();
  }
}
