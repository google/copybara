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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
 */
/** A Gerrit change that can be read from a feedback migration. */
@SkylarkModule(
    name = "change",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    doc = "Gerrit change that can be read from a feedback migration.",
    documented = false
)
public class ChangeInfo implements SkylarkValue {

  @Key private String id;
  @Key private String project;
  @Key private String branch;
  @Key private String topic;
  @Key("change_id") private String changeId;
  @Key private String subject;
  @Key private String status;
  @Key private String created;
  @Key private String updated;
  @Key private String submitted;
  @Key("_number") private long number;
  @Key private AccountInfo owner;
  @Key private Map<String, LabelInfo> labels;
  @Key private List<ChangeMessageInfo> messages;
  @Key("current_revision") private String currentRevision;
  @Key("revisions") private Map<String, RevisionInfo> allRevisions;
  @Key("_more_changes") private boolean moreChanges;

  @SkylarkCallable(
      name = "id", doc = "The id of this change.", structField = true, allowReturnNones = true)
  public String getId() {
    return id;
  }

  public String getProject() {
    return project;
  }

  public String getBranch() {
    return branch;
  }

  public String getTopic() {
    return topic;
  }

  public String getChangeId() {
    return changeId;
  }

  public String getSubject() {
    return subject;
  }

  public ChangeStatus getStatus() {
    return ChangeStatus.valueOf(status);
  }

  public ZonedDateTime getCreated() {
    return parseTimestamp(created);
  }

  public ZonedDateTime getUpdated() {
    return parseTimestamp(updated);
  }

  public ZonedDateTime getSubmitted() {
    return parseTimestamp(submitted);
  }

  public long getNumber() {
    return number;
  }

  public AccountInfo getOwner() {
    return owner;
  }

  public ImmutableMap<String, LabelInfo> getLabels() {
    return ImmutableMap.copyOf(labels);
  }

  public List<ChangeMessageInfo> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  public String getCurrentRevision() {
    return currentRevision;
  }

  public ImmutableMap<String, RevisionInfo> getAllRevisions() {
    return ImmutableMap.copyOf(allRevisions);
  }

  public boolean isMoreChanges() {
    return moreChanges;
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("project", project)
        .add("branch", branch)
        .add("topic", topic)
        .add("changeId", changeId)
        .add("subject", subject)
        .add("status", status)
        .add("created", created)
        .add("updated", updated)
        .add("submitted", submitted)
        .add("number", number)
        .add("owner", owner)
        .add("labels", labels)
        .add("messages", messages)
        .add("currentRevision", currentRevision)
        .add("allRevisions", allRevisions)
        .add("moreChanges", moreChanges)
        .toString();
  }
}
