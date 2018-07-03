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

/**
 * A Hg repository revision (changeset)
 */
public class HgRevision implements Revision {

  private final String globalId;

  /**
   * Creates a hg revision from a hexadecimal string identifier. Currently, Mercurial uses SHA1 to
   * hash revisions.
   *
   * @param globalId global identifier for the revision
   */
  public HgRevision(String globalId) {
    this.globalId = Preconditions.checkNotNull(globalId);
  }

  @Override
  public String asString() { return globalId; }

  @Override
  public String getLabelName() { return HgRepository.HG_ORIGIN_REV_ID; }

  @Override
  //TODO(jlliu): properly implement after LogCmd is implemented
  public ZonedDateTime readTimestamp() { return null; }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("global ID", globalId)
        .toString();
  }

  public String getGlobalId() { return globalId; }
}
