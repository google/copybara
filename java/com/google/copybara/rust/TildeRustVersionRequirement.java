/*
 * Copyright (C) 2023 Google LLC
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

/**
 * A {@link RustVersionRequirement} class that supports tilde requirements. Review <a
 * href="https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#tilde-requirements">the
 * Rust tilde requirements reference</a> for more information.
 */
public class TildeRustVersionRequirement extends RustVersionRequirement {
  static final Pattern VALID_TILDE_FORMAT_REGEX =
      Pattern.compile("~\\^?[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?(\\+?.*)?$");
  private final String requirement;

  private TildeRustVersionRequirement(String requirement) throws ValidationException {
    ValidationException.checkCondition(
        VALID_TILDE_FORMAT_REGEX.matcher(requirement).matches(),
        String.format("The string %s is not a valid tilde version requirement.", requirement));
    this.requirement = requirement;
  }

  public static TildeRustVersionRequirement create(String requirement) throws ValidationException {
    return new TildeRustVersionRequirement(requirement);
  }

  /**
   * Returns true if this class can handle the given Cargo version requirement.
   *
   * @param requirement The version requirement to check.
   * @return A boolean indicating whether the requirement string can be handled by this class.
   */
  public static boolean handlesRequirement(String requirement) {
    return VALID_TILDE_FORMAT_REGEX.matcher(requirement).matches();
  }

  private SemanticVersion getRequiredVersion() throws ValidationException {
    return SemanticVersion.createFromVersionString(requirement.replace("~", ""));
  }

  private SemanticVersion getNextVersion() throws ValidationException {
    SemanticVersion required = getRequiredVersion();

    if (required.minorVersion().isPresent()) {
      return SemanticVersion.create(
          required.majorVersion(), required.minorVersion().orElse(0) + 1, 0, Optional.empty());
    } else {
      return SemanticVersion.create(required.majorVersion() + 1, 0, 0, Optional.empty());
    }
  }

  @Override
  public boolean fulfills(String version) throws ValidationException {
    SemanticVersion requiredVersion = getRequiredVersion();
    SemanticVersion currVersion = SemanticVersion.createFromVersionString(version);
    SemanticVersion nextVersion = getNextVersion();

    return currVersion.compareTo(requiredVersion) >= 0 && currVersion.compareTo(nextVersion) < 0;
  }
}
