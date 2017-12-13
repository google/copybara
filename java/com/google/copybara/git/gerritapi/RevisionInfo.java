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

/**
 * See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#revision-info
 */
public class RevisionInfo {

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

  public int getPatchsetNumber() {
    return patchsetNumber;
  }

  public String getCreated() {
    return created;
  }

  public AccountInfo getUploader() {
    return uploader;
  }

  public String getRef() {
    return ref;
  }

  public ImmutableMap<String, FetchInfo> getFetch() {
    return ImmutableMap.copyOf(fetch);
  }

  public CommitInfo getCommit() {
    return commit;
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
