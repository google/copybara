/*
 * Copyright (C) 2025 Google LLC.
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
import java.util.regex.Pattern;

/** Class that represents an "epoch" version requirement.
 * 
 * This is distinct from DefaultVersionRequirement, a DefaultVersionRequirement can be e.g.
 * `2.1.0`, whereas here the epoch is the same for `2.0`.
 * An additional benefit of using epoch version requirements is that they allow prereleases
 * to match the main release branch, so `2.0.0-pre` will match the requirement `2`.
 * 
 * This has a pretty restrictive set of versions allowed: only `x`, `0.x`, and `0.0.x` are supported.
 * 
 * This should not be used with version numbers that come from Cargo, but may be used with
 * version numbers in copy.bara.sky.
 * 
 * This type of version requirement is sometimes found in projects that vendor at most one copy
 * per major version stream (like google3). */
public class EpochRustVersionRequirement extends RustVersionRequirement {
  static final Pattern VALID_DEFAULT_FORMAT_REGEX =
      Pattern.compile("^(0\\.)?(0\\.)?[0-9]$");

  private EpochRustVersionRequirement(String requirement) throws ValidationException {
    super(requirement);
    ValidationException.checkCondition(
        VALID_DEFAULT_FORMAT_REGEX.matcher(requirement).matches(),
        String.format(
            "The string %s is not a valid default or caret version requirement.", requirement));
    this.requirement = requirement.replace("^", "");
  }

  public static EpochRustVersionRequirement create(String requirement)
      throws ValidationException {
    return new EpochRustVersionRequirement(requirement);
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

  @Override
  public boolean fulfills(String version) throws ValidationException {
    SemanticVersion requiredVersion = getRequiredVersion();
    SemanticVersion currVersion = SemanticVersion.createFromVersionString(version);

    if (requiredVersion.majorVersion() != currVersion.majorVersion()) {
      return false;
    }
    if (requiredVersion.minorVersion().isPresent()
        && !requiredVersion.minorVersion().equals(currVersion.minorVersion())) {
      return false;
    }
    if (requiredVersion.patchVersion().isPresent()
        && !requiredVersion.patchVersion().equals(currVersion.patchVersion())) {
      return false;
    }
    // Explicitly doesn't handle prereleases: all prereleases are compatible within an epoch
    // even though they are not semver-compatible.
    return true;
  }
}
