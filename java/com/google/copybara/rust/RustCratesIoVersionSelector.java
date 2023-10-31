/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.rust.RustVersionRequirement.SemanticVersion;
import com.google.copybara.util.console.Console;
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionSelector;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A {@link VersionSelector} that selects the latest version that satisfies the given cargo version
 * requirement.
 */
public class RustCratesIoVersionSelector implements VersionSelector {

  private final RustVersionRequirement requirement;

  public RustCratesIoVersionSelector(RustVersionRequirement requirement) {
    this.requirement = requirement;
  }

  @Override
  public Optional<String> select(
      VersionList versionList, @Nullable String requestedRef, Console console)
      throws ValidationException, RepoException {
    String latestVersion = null;

    for (String ref : versionList.list()) {
      if (requirement.fulfills(ref)) {
        if (requestedRef != null
            && SemanticVersion.createFromVersionString(requestedRef)
                    .compareTo(SemanticVersion.createFromVersionString(ref))
                == 0) {
          latestVersion = ref;
          break;
        }

        if (latestVersion == null
            || SemanticVersion.createFromVersionString(ref)
                    .compareTo(SemanticVersion.createFromVersionString(latestVersion))
                > 0) {
          latestVersion = ref;
        }
      }
    }
    return Optional.ofNullable(latestVersion);
  }

  @Override
  public String toString() {
    return String.format(
        "rust.crates_io_version_selector(requirement = \"%s\")",
        requirement.getRequirementString());
  }
}
