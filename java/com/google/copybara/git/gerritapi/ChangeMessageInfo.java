/*
 * Copyright (C) 2017 Google Inc.
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
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.time.ZonedDateTime;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-message-info
 */
@SuppressWarnings("unused")
@SkylarkModule(
    name = "gerritapi.ChangeMessageInfo",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    doc = "Gerrit change message information.")
public class ChangeMessageInfo implements StarlarkValue {

  @Key private String id;
  @Key private AccountInfo author;
  @Key("real_author") private AccountInfo realAuthor;
  @Key private String date;
  @Key private String message;
  @Key private String tag;
  @Key("_revision_number") private int revisionNumber;

  @SkylarkCallable(
      name = "id",
      doc = "The ID of the message.",
      structField = true,
      allowReturnNones = true)
  public String getId() {
    return id;
  }

  @SkylarkCallable(
      name = "author",
      doc =
          "Author of the message as an AccountInfo entity.\n"
              + "Unset if written by the Gerrit system.",
      structField = true,
      allowReturnNones = true)
  public AccountInfo getAuthor() {
    return author;
  }

  @SkylarkCallable(
      name = "real_author",
      doc =
          "Real author of the message as an AccountInfo entity.\n"
              + "Set if the message was posted on behalf of another user.",
      structField = true,
      allowReturnNones = true)
  public AccountInfo getRealAuthor() {
    return realAuthor;
  }

  public ZonedDateTime getDate() {
    return parseTimestamp(date);
  }

  @SkylarkCallable(
      name = "date",
      doc = "The timestamp of when this identity was constructed.",
      structField = true,
      allowReturnNones = true)
  public String getDateForSkylark() {
    return date;
  }

  @SkylarkCallable(
      name = "message",
      doc = "The text left by the user.",
      structField = true,
      allowReturnNones = true)
  public String getMessage() {
    return message;
  }

  @SkylarkCallable(
      name = "tag",
      doc = "Value of the tag field from ReviewInput set while posting the review. "
          + "NOTE: To apply different tags on on different votes/comments multiple "
          + "invocations of the REST call are required.",
      structField = true,
      allowReturnNones = true)
  public String getTag() {
    return tag;
  }

  @SkylarkCallable(
      name = "revision_number",
      doc = "Which patchset (if any) generated this message.",
      structField = true,
      allowReturnNones = true)
  public int getRevisionNumber() {
    return revisionNumber;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("author", author)
        .add("realAuthor", realAuthor)
        .add("date", date)
        .add("message", message)
        .add("tag", tag)
        .add("revisionNumber", revisionNumber)
        .toString();
  }
}
