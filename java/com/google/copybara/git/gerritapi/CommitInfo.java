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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#commit-info */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.CommitInfo",
    doc = "Gerrit commit information.")
public class CommitInfo implements StarlarkValue {
  @Key private String commit;
  @Key private List<ParentCommitInfo> parents;
  @Key private GitPersonInfo author;
  @Key private GitPersonInfo committer;
  @Key private String subject;
  @Key private String message;

  @StarlarkMethod(
      name = "commit",
      doc =
          "The commit ID. Not set if included in a RevisionInfo entity that is contained "
              + "in a map which has the commit ID as key.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getCommit() {
    return commit;
  }

  public List<ParentCommitInfo> getParents() {
    return parents == null ? ImmutableList.of() : ImmutableList.copyOf(parents);
  }

  @StarlarkMethod(
      name = "parents",
      doc =
          "The parent commits of this commit as a list of CommitInfo entities. "
              + "In each parent only the commit and subject fields are populated.",
      structField = true)
  public Sequence<ParentCommitInfo> getMessagesForSkylark() {
    return StarlarkList.immutableCopyOf(getParents());
  }

  @StarlarkMethod(
      name = "author",
      doc = "The author of the commit as a GitPersonInfo entity.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public GitPersonInfo getAuthor() {
    return author;
  }

  @StarlarkMethod(
      name = "committer",
      doc = "The committer of the commit as a GitPersonInfo entity.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public GitPersonInfo getCommitter() {
    return committer;
  }

  @StarlarkMethod(
      name = "subject",
      doc = "The subject of the commit (header line of the commit message).",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getSubject() {
    return subject;
  }

  @StarlarkMethod(
      name = "message",
      doc = "The commit message.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getMessage() {
    return message;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commit", commit)
        .add("parents", parents)
        .add("author", author)
        .add("committer", committer)
        .add("subject", subject)
        .add("message", message)
        .toString();
  }
}
