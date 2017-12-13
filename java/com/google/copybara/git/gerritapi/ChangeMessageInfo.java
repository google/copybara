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
import java.time.ZonedDateTime;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-message-info
 */
public class ChangeMessageInfo {

  @Key private String id;
  @Key private AccountInfo author;
  @Key("real_author") private AccountInfo realAuthor;
  @Key private String date;
  @Key private String message;
  @Key private String tag;
  @Key("_revision_number") private int revisionNumber;

  public String getId() {
    return id;
  }

  public AccountInfo getAuthor() {
    return author;
  }

  public AccountInfo getRealAuthor() {
    return realAuthor;
  }

  public ZonedDateTime getDate() {
    return parseTimestamp(date);
  }

  public String getMessage() {
    return message;
  }

  public String getTag() {
    return tag;
  }

  public int getRevisionNumber() {
    return revisionNumber;
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
