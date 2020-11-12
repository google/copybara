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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.ChangeInfo",
    doc = "Gerrit change information.")
public class ChangeInfo implements StarlarkValue {

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
  @Key private boolean submittable;
  @Key("_number") private long number;
  @Key private AccountInfo owner;
  @Key private Map<String, LabelInfo> labels;
  @Key private List<ChangeMessageInfo> messages;
  @Key("current_revision") private String currentRevision;
  @Key("revisions") private Map<String, RevisionInfo> allRevisions;
  @Key("_more_changes") private boolean moreChanges;
  @Key private Map<String, List<AccountInfo>> reviewers;

  @StarlarkMethod(
      name = "id",
      doc =
          "The ID of the change in the format \"`<project>~<branch>~<Change-Id>`\", where "
              + "'project', 'branch' and 'Change-Id' are URL encoded. For 'branch' the "
              + "refs/heads/ prefix is omitted.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getId() {
    return id;
  }

  @StarlarkMethod(
      name = "project",
      doc = "The name of the project.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getProject() {
    return project;
  }

  @StarlarkMethod(
      name = "branch",
      doc = "The name of the target branch.\n" + "The refs/heads/ prefix is omitted.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getBranch() {
    return branch;
  }

  @StarlarkMethod(
      name = "topic",
      doc = "The topic to which this change belongs.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getTopic() {
    return topic;
  }

  @StarlarkMethod(
      name = "change_id",
      doc = "The Change-Id of the change.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getChangeId() {
    return changeId;
  }

  @StarlarkMethod(
      name = "subject",
      doc = "The subject of the change (header line of the commit message).",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getSubject() {
    return subject;
  }

  public ChangeStatus getStatus() {
    return ChangeStatus.valueOf(status);
  }

  @StarlarkMethod(
      name = "status",
      doc = "The status of the change (NEW, MERGED, ABANDONED).",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getStatusAsString() {
    return status;
  }

  public ZonedDateTime getCreated() {
    return parseTimestamp(created);
  }

  @StarlarkMethod(
      name = "created",
      doc = "The timestamp of when the change was created.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getCreatedForSkylark() {
    return created;
  }

  public ZonedDateTime getUpdated() {
    return parseTimestamp(updated);
  }

  @StarlarkMethod(
      name = "updated",
      doc = "The timestamp of when the change was last updated.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getUpdatedForSkylark() {
    return updated;
  }

  public ZonedDateTime getSubmitted() {
    return parseTimestamp(submitted);
  }

  @StarlarkMethod(
      name = "submitted",
      doc = "The timestamp of when the change was submitted.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getSubmittedForSkylark() {
    return submitted;
  }

  @StarlarkMethod(
      name = "submittable",
      doc =
          "Whether the change has been approved by the project submit rules. Only set if "
              + "requested via additional field SUBMITTABLE.",
      structField = true)
  public boolean isSubmittable() {
    return submittable;
  }

  public long getNumber() {
    return number;
  }

  @StarlarkMethod(name = "number", doc = "The legacy numeric ID of the change.", structField = true)
  public String getNumberAsString() {
    return Long.toString(number);
  }

  @StarlarkMethod(
      name = "owner",
      doc = "The owner of the change as an AccountInfo entity.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public AccountInfo getOwner() {
    return owner;
  }

  public ImmutableMap<String, LabelInfo> getLabels() {
    return labels == null ? ImmutableMap.of() : ImmutableMap.copyOf(labels);
  }

  @StarlarkMethod(
      name = "labels",
      doc =
          "The labels of the change as a map that maps the label names to LabelInfo entries.\n"
              + "Only set if labels or detailed labels are requested.",
      structField = true)
  public Dict<String, LabelInfo> getLabelsForSkylark() {
    return Dict.immutableCopyOf(getLabels());
  }

  public List<ChangeMessageInfo> getMessages() {
    return messages == null ? ImmutableList.of() : ImmutableList.copyOf(messages);
  }

  @StarlarkMethod(
      name = "messages",
      doc =
          "Messages associated with the change as a list of ChangeMessageInfo entities.\n"
              + "Only set if messages are requested.",
      structField = true)
  public Sequence<ChangeMessageInfo> getMessagesForSkylark() {
    return StarlarkList.immutableCopyOf(getMessages());
  }

  @StarlarkMethod(
      name = "current_revision",
      doc =
          "The commit ID of the current patch set of this change.\n"
              + "Only set if the current revision is requested or if all revisions are requested.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getCurrentRevision() {
    return currentRevision;
  }

  public ImmutableMap<String, RevisionInfo> getAllRevisions() {
    return allRevisions == null ? ImmutableMap.of() : ImmutableMap.copyOf(allRevisions);
  }

  @StarlarkMethod(
      name = "revisions",
      doc =
          "All patch sets of this change as a map that maps the commit ID of the patch set to a "
              + "RevisionInfo entity.\n"
              + "Only set if the current revision is requested (in which case it will only contain "
              + "a key for the current revision) or if all revisions are requested.",
      structField = true)
  public Dict<String, RevisionInfo> getAllRevisionsForSkylark() {
    return Dict.immutableCopyOf(getAllRevisions());
  }

  public ImmutableMap<String, List<AccountInfo>> getReviewers() {
    return reviewers == null? ImmutableMap.of(): ImmutableMap.copyOf(reviewers);
  }

  public boolean isMoreChanges() {
    return moreChanges;
  }

  @Override
  public void repr(Printer printer) {
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
        .add("submittable", submittable)
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
