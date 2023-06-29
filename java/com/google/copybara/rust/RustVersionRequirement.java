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

import com.google.auto.value.AutoValue;
import com.google.copybara.exception.ValidationException;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Represents a Cargo version requirement */
@StarlarkBuiltin(name = "rust_version_requirement", doc = "Represents a Cargo version requirement.")
abstract class RustVersionRequirement implements StarlarkValue {
  public static RustVersionRequirement getVersionRequirement(String requirement)
      throws ValidationException {
    // TODO(chriscampos): Support additional types of version requirements
    if (DefaultRustVersionRequirement.handlesRequirement(requirement)) {
      return DefaultRustVersionRequirement.create(requirement);
    } else if (ComparisonRustVersionRequirement.handlesRequirement(requirement)) {
      return ComparisonRustVersionRequirement.create(requirement);
    } else if (MultipleRustVersionRequirement.handlesRequirement(requirement)) {
      return MultipleRustVersionRequirement.create(requirement);
    }

    throw new ValidationException(
        String.format("The requirement %s is currently not supported.", requirement));
  }

  /**
   * Given a semantic version string, returns true if the version fulfills this version requirement.
   *
   * @param version The semantic version string.
   * @return A boolean indicating if the version fulfills this version requirement.
   * @throws ValidationException If there is an issue parsing the version string.
   */
  @StarlarkMethod(
      name = "fulfills",
      parameters = {@Param(name = "fulfills", named = true, doc = "The version requirement")},
      doc =
          "Given a semantic version string, returns true if the version fulfills this version"
              + " requirement.")
  public abstract boolean fulfills(String version) throws ValidationException;

  @AutoValue
  abstract static class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern VALID_VERSION_PATTERN =
        Pattern.compile("^([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?(\\+?.*)?$");

    private static final Comparator<SemanticVersion> VERSION_COMPARATOR =
        Comparator.comparing(SemanticVersion::majorVersion)
            .thenComparing(SemanticVersion::minorVersion, Comparator.comparingInt(k -> k.orElse(0)))
            .thenComparing(
                SemanticVersion::patchVersion, Comparator.comparingInt(k -> k.orElse(0)));

    public static SemanticVersion create(int majorVersion, int minorVersion, int patchVersion) {
      return new AutoValue_RustVersionRequirement_SemanticVersion(
          majorVersion, Optional.of(minorVersion), Optional.of(patchVersion));
    }

    public static SemanticVersion createFromVersionString(String version)
        throws ValidationException {
      Matcher matcher = VALID_VERSION_PATTERN.matcher(version);
      ValidationException.checkCondition(
          matcher.matches(),
          String.format("The string %s is not a valid Rust semantic version.", version));

      int majorVersion = Integer.parseInt(matcher.group(1));
      Optional<Integer> minorVersion =
          Optional.ofNullable(matcher.group(2) != null
              ? Integer.parseInt(matcher.group(2).replace(".", "")) : null);
      Optional<Integer> patchVersion =
          Optional.ofNullable(matcher.group(3) != null
              ? Integer.parseInt(matcher.group(3).replace(".", "")) : null);

      return new AutoValue_RustVersionRequirement_SemanticVersion(
          majorVersion, minorVersion, patchVersion);
    }

    @Override
    public int compareTo(SemanticVersion other) {
      return VERSION_COMPARATOR.compare(this, other);
    }

    public abstract int majorVersion();

    public abstract Optional<Integer> minorVersion();

    public abstract Optional<Integer> patchVersion();
  }
}
