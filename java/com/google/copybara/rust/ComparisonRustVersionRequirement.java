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

import static com.google.common.collect.Comparators.emptiesLast;

import com.google.copybara.exception.ValidationException;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Class that represents a Cargo comparison version requirement, e.g. >= 1.2.0. */
public class ComparisonRustVersionRequirement extends RustVersionRequirement {
  static final Pattern VALID_COMPARISON_FORMAT_REGEX =
      Pattern.compile("^([<>=]=?)\\s*?([0-9]+(\\.[0-9]+)?(\\.[0-9]+)?(-(.*))?)");
  private static final Comparator<Optional<Integer>> KEY_COMPARATOR =
      (k1, k2) -> (k1.isEmpty() || k2.isEmpty() ? 0 : Integer.compare(k1.get(), k2.get()));
  private static final Comparator<SemanticVersion> COMPARISON_VERSION_COMPARATOR =
      Comparator.comparing(SemanticVersion::majorVersion)
          .thenComparing(SemanticVersion::minorVersion, KEY_COMPARATOR)
          .thenComparing(SemanticVersion::patchVersion, KEY_COMPARATOR)
          .thenComparing(
              SemanticVersion::preReleaseIdentifier,
              emptiesLast(SemanticVersion.getPreReleaseComparator()));
  private final String operator;
  private final SemanticVersion requirementVersion;

  private ComparisonRustVersionRequirement(String requirement) throws ValidationException {
    Matcher matcher = VALID_COMPARISON_FORMAT_REGEX.matcher(requirement);
    ValidationException.checkCondition(
        matcher.matches(),
        String.format(
            "The string %s is not a valid default or caret version requirement.", requirement));
    this.operator = matcher.group(1);
    this.requirementVersion = SemanticVersion.createFromVersionString(matcher.group(2));
  }

  public static ComparisonRustVersionRequirement create(String requirement)
      throws ValidationException {
    return new ComparisonRustVersionRequirement(requirement);
  }

  /**
   * Returns true if this class can handle the given Cargo version requirement.
   *
   * @param requirement The version requirement to check.
   * @return A boolean indicating whether the requirement string can be handled by this class.
   */
  public static boolean handlesRequirement(String requirement) {
    return VALID_COMPARISON_FORMAT_REGEX.matcher(requirement).matches();
  }

  @Override
  public boolean fulfills(String version) throws ValidationException {
    SemanticVersion currentVersion = SemanticVersion.createFromVersionString(version);

    switch (operator) {
      case ">":
        return compareVersions(currentVersion, requirementVersion) > 0;
      case ">=":
        return compareVersions(currentVersion, requirementVersion) >= 0;
      case "<":
        return compareVersions(currentVersion, requirementVersion) < 0;
      case "<=":
        return compareVersions(currentVersion, requirementVersion) <= 0;
      case "=":
        return compareVersions(currentVersion, requirementVersion) == 0;
      default:
        break;
    }
    return false;
  }

  private int compareVersions(SemanticVersion currentVersion, SemanticVersion requirementVersion) {
    return COMPARISON_VERSION_COMPARATOR.compare(currentVersion, requirementVersion);
  }
}
