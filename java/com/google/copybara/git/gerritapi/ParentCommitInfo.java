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

/**
 * Restricted version of {@link CommitInfo} for describing parents
 */
public class ParentCommitInfo {
  @Key private String commit;
  @Key private String subject;

  public String getCommit() {
    return commit;
  }

  public String getSubject() {
    return subject;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commit", commit)
        .add("subject", subject)
        .toString();
  }
}
