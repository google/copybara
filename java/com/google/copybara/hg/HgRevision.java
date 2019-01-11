/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.hg;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Revision;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * A Hg repository revision (changeset)
 */
public class HgRevision implements Revision {

  private final String globalId;
  @Nullable private final String reference;

  /**
   * Creates a hg revision from a hexadecimal string identifier. Currently, Mercurial uses SHA1 to
   * hash revisions.
   *
   * @param globalId global identifier for the revision
   */
  public HgRevision(String globalId) {
    this.globalId = Preconditions.checkNotNull(globalId);
    this.reference = null;
  }

  /**
   * Creates a hg revision from a hexadecimal string identifier. Currently, Mercurial uses SHA1 to
   * hash revisions.
   *
   * @param globalId global identifier for the revision
   * @param reference The reference provided by the user (i.e. 'tip')
   */
  public HgRevision(String globalId, String reference) {
    this.globalId = Preconditions.checkNotNull(globalId);
    this.reference = Preconditions.checkNotNull(reference);
  }

  @Override
  public String asString() {
    return globalId;
  }

  @Nullable
  @Override
  public String contextReference() {
    return reference;
  }

  @Override
  // TODO(jlliu): properly implement after LogCmd is implemented
  public ZonedDateTime readTimestamp() {
    return null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues().add("global ID", globalId).toString();
  }

  String getGlobalId() {
    return globalId;
  }
}
