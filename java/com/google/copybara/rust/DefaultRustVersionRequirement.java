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

import com.google.copybara.exception.ValidationException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Class that represents the default cargo version requirement. e.g., "1.2.3" or "^1.2.3" */
public class DefaultRustVersionRequirement extends RustVersionRequirement {
  static final Pattern VALID_DEFAULT_FORMAT_REGEX =
      Pattern.compile("^\\^?[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?(-(.*))?(-(.*))?$");

  private DefaultRustVersionRequirement(String requirement) throws ValidationException {
    super(requirement);
    ValidationException.checkCondition(
        VALID_DEFAULT_FORMAT_REGEX.matcher(requirement).matches(),
        String.format(
            "The string %s is not a valid default or caret version requirement.", requirement));
    this.requirement = requirement.replace("^", "");
  }

  public static DefaultRustVersionRequirement create(String requirement)
      throws ValidationException {
    return new DefaultRustVersionRequirement(requirement);
  }

  /**
   * Returns true if this class can handle the given Cargo version requirement.
   *
   * @param requirement The version requirement to check.
   * @return A boolean indicating whether the requirement string can be handled by this class.
   */
  public static boolean handlesRequirement(String requirement) {
    return VALID_DEFAULT_FORMAT_REGEX.matcher(requirement).matches();
  }

  private SemanticVersion getRequiredVersion() throws ValidationException {
    return SemanticVersion.createFromVersionString(requirement);
  }

  /* Gets the next version, according to the passed in required version.
  This is the earliest version that no longer satisfies the requirement.
  Therefore, any acceptable version must be less than this. */
  private SemanticVersion getNextVersion() throws ValidationException {
    // Handle special cases: 0 and 0.0
    if (requirement.equals("0")) {
      return SemanticVersion.create(1, 0, 0, Optional.empty());
    } else if (requirement.equals("0.0")) {
      return SemanticVersion.create(0, 1, 0, Optional.empty());
    }

    SemanticVersion requiredVersion = getRequiredVersion();
    if (requiredVersion.majorVersion() > 0) {
      return SemanticVersion.create(requiredVersion.majorVersion() + 1, 0, 0, Optional.empty());
    } else if (requiredVersion.minorVersion().orElse(0) > 0) {
      return SemanticVersion.create(
          0, requiredVersion.minorVersion().orElse(0) + 1, 0, Optional.empty());
    }

    return SemanticVersion.create(
        0, 0, requiredVersion.patchVersion().orElse(0) + 1, Optional.empty());
  }

  @Override
  public boolean fulfills(String version) throws ValidationException {
    SemanticVersion requiredVersion = getRequiredVersion();
    SemanticVersion currVersion = SemanticVersion.createFromVersionString(version);
    // Ensure that a pre-release of a next major version (which compares less than the next major
    // version) doesn't fulfill a requirement for the previous major version.
    SemanticVersion currVersionNoPreRelease = currVersion.toBuilder()
        .setPreReleaseIdentifier(Optional.empty())
        .build();
    SemanticVersion nextVersion = getNextVersion();

    return currVersion.compareTo(requiredVersion) >= 0 && currVersionNoPreRelease.compareTo(nextVersion) < 0;
  }
}
