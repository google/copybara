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

import static com.google.copybara.git.gerritapi.GerritApiUtil.parseTimestamp;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#git-person-info */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.GitPersonInfo",
    doc = "Git person information.")
public class GitPersonInfo implements StarlarkValue {

  @Key private String name;
  @Key private String email;
  @Key private String date;
  @Key private int tz;

  @StarlarkMethod(
      name = "name",
      doc = "The name of the author/committer.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getName() {
    return name;
  }

  @StarlarkMethod(
      name = "email",
      doc = "The email address of the author/committer.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getEmail() {
    return email;
  }

  public ZonedDateTime getDate() {
    return parseTimestamp(date).withZoneSameInstant(
        ZoneId.from(ZoneOffset.ofTotalSeconds(tz * 60)));
  }

  @StarlarkMethod(
      name = "date",
      doc = "The timestamp of when this identity was constructed.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getDateForSkylark() {
    return date;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("email", email)
        .add("date", date)
        .add("tz", tz)
        .toString();
  }
}
