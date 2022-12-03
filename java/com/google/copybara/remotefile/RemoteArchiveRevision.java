/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.remotefile;

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.revision.Revision;
import java.time.ZonedDateTime;

/** A Revision for a remote file */
public class RemoteArchiveRevision implements Revision {

  private static final String ARCHIVE_VERSION_LABEL = "ARCHIVE_VERSION";
  private static final String ARCHIVE_FULL_URL_LABEL = "ARCHIVE_FULL_URL ";

  final RemoteArchiveVersion version;

  public RemoteArchiveRevision(RemoteArchiveVersion version) {
    this.version = version;
  }

  @Override
  public String getUrl() {
    return version.getFullUrl();
  }

  @Override
  public ZonedDateTime readTimestamp() throws RepoException {
    return null;
  }

  @Override
  public String asString() {
    return version.getVersion();
  }

  @Override
  public ImmutableListMultimap<String, String> associatedLabels() {
    return ImmutableListMultimap.of(
        ARCHIVE_VERSION_LABEL,
        nullToEmpty(version.getVersion()),
        ARCHIVE_FULL_URL_LABEL,
        nullToEmpty(getUrl()));
  }
}
