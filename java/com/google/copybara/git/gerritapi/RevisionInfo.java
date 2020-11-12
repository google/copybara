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
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.RevisionInfo",
    doc = "Gerrit revision information.")
public class RevisionInfo implements StarlarkValue {

  @Key private String kind;
  @Key("_number") private int patchsetNumber;
  @Key private String created;
  @Key private AccountInfo uploader;
  @Key private String ref;
  @Key private Map<String, FetchInfo> fetch;
  @Key private CommitInfo commit;

  public Kind getKind() {
    return Kind.valueOf(kind);
  }

  @StarlarkMethod(
      name = "kind",
      doc =
          "The change kind. Valid values are REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, "
              + "NO_CODE_CHANGE, and NO_CHANGE.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getKindAsString() {
    return kind;
  }

  @StarlarkMethod(
      name = "patchset_number",
      doc = "The patch set number, or edit if the patch set is an edit.",
      structField = true)
  public int getPatchsetNumber() {
    return patchsetNumber;
  }

  @StarlarkMethod(
      name = "created",
      doc = "The timestamp of when the patch set was created.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getCreated() {
    return created;
  }

  @StarlarkMethod(
      name = "uploader",
      doc = "The uploader of the patch set as an AccountInfo entity.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public AccountInfo getUploader() {
    return uploader;
  }

  @StarlarkMethod(
      name = "ref",
      doc = "The Git reference for the patch set.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getRef() {
    return ref;
  }

  public ImmutableMap<String, FetchInfo> getFetch() {
    return fetch == null ? ImmutableMap.of() : ImmutableMap.copyOf(fetch);
  }

  @StarlarkMethod(
      name = "commit",
      doc = "The commit of the patch set as CommitInfo entity.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public CommitInfo getCommit() {
    return commit;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  /**
   * See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info
   */
  public enum Kind {
    REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, NO_CODE_CHANGE, NO_CHANGE
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("kind", kind)
        .add("patchsetNumber", patchsetNumber)
        .add("created", created)
        .add("uploader", uploader)
        .add("ref", ref)
        .add("fetch", fetch)
        .add("commit", commit)
        .toString();
  }
}
