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
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.primitives.Ints;
import com.google.copybara.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
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
public abstract class RustVersionRequirement implements StarlarkValue {
  String requirement;

  RustVersionRequirement(String requirement) {
    this.requirement = requirement;
  }

  public static RustVersionRequirement getVersionRequirement(
      String requirement, boolean allowEpochs) throws ValidationException {
    // TODO(chriscampos): Support additional types of version requirements
    if (allowEpochs && EpochRustVersionRequirement.handlesRequirement(requirement)) {
      return EpochRustVersionRequirement.create(requirement);
    } else if (DefaultRustVersionRequirement.handlesRequirement(requirement)) {
      return DefaultRustVersionRequirement.create(requirement);
    } else if (ComparisonRustVersionRequirement.handlesRequirement(requirement)) {
      return ComparisonRustVersionRequirement.create(requirement);
    } else if (MultipleRustVersionRequirement.handlesRequirement(requirement)) {
      return MultipleRustVersionRequirement.create(requirement);
    } else if (TildeRustVersionRequirement.handlesRequirement(requirement)) {
      return TildeRustVersionRequirement.create(requirement);
    } else if (WildcardRustVersionRequirement.handlesRequirement(requirement)) {
      return WildcardRustVersionRequirement.create(requirement);
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

  public String getRequirementString() {
    return this.requirement;
  }

  /** Represents a semantic version for a Rust crate. */
  @AutoValue
  public abstract static class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern VALID_VERSION_PATTERN =
        Pattern.compile("^([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?(-(.*))?(\\+?.*)?$");

    private static final Comparator<SemanticVersion> VERSION_COMPARATOR =
        Comparator.comparing(SemanticVersion::majorVersion)
            .thenComparing(SemanticVersion::minorVersion, Comparator.comparingInt(k -> k.orElse(0)))
            .thenComparing(SemanticVersion::patchVersion, Comparator.comparingInt(k -> k.orElse(0)))
            .thenComparing(
                SemanticVersion::preReleaseIdentifier, emptiesLast(getPreReleaseComparator()));

    public static SemanticVersion create(
        int majorVersion,
        int minorVersion,
        int patchVersion,
        Optional<String> preReleaseIdentifier) {
      return builder()
          .setMajorVersion(majorVersion)
          .setMinorVersion(Optional.of(minorVersion))
          .setPatchVersion(Optional.of(patchVersion))
          .setPreReleaseIdentifier(preReleaseIdentifier)
          .build();
    }

    public static SemanticVersion createFromVersionString(String version)
        throws ValidationException {
      Matcher matcher = VALID_VERSION_PATTERN.matcher(version);
      ValidationException.checkCondition(
          matcher.matches(),
          String.format("The string %s is not a valid Rust semantic version.", version));

      int majorVersion = Integer.parseInt(matcher.group(1));
      Optional<Integer> minorVersion =
          Optional.ofNullable(
              matcher.group(2) != null
                  ? Integer.parseInt(matcher.group(2).replace(".", ""))
                  : null);
      Optional<Integer> patchVersion =
          Optional.ofNullable(
              matcher.group(3) != null
                  ? Integer.parseInt(matcher.group(3).replace(".", ""))
                  : null);
      Optional<String> preReleaseIdentifier = Optional.ofNullable(matcher.group(5));

      return builder()
          .setMajorVersion(majorVersion)
          .setMinorVersion(minorVersion)
          .setPatchVersion(patchVersion)
          .setPreReleaseIdentifier(preReleaseIdentifier)
          .build();
    }

    @Override
    public int compareTo(SemanticVersion other) {
      return VERSION_COMPARATOR.compare(this, other);
    }

    public abstract int majorVersion();

    public abstract Optional<Integer> minorVersion();

    public abstract Optional<Integer> patchVersion();

    public abstract Optional<String> preReleaseIdentifier();

    public static Comparator<String> getPreReleaseComparator() {
      return new Comparator<>() {
        @Override
        public int compare(String o1, String o2) {
          // This follows the SemVer specification: https://semver.org/#spec-item-11
          if (o1.equals(o2)) {
            return 0;
          }

          // Split the pre-release strings into lists, separated by .
          ArrayList<String> list1 = new ArrayList<>(Arrays.asList(o1.split("\\.")));
          ArrayList<String> list2 = new ArrayList<>(Arrays.asList(o2.split("\\.")));

          for (int i = 0; i < min(list1.size(), list2.size()); i++) {
            // If both elements are numeric, they are compared as numbers.
            int result;
            String elem1 = list1.get(i);
            String elem2 = list2.get(i);
            // Numeric elements are compared as numbers.
            if (Ints.tryParse(elem1) != null && Ints.tryParse(elem2) != null) {
              result = Integer.compare(Integer.parseInt(elem1), Integer.parseInt(elem2));
            } else {
              result = elem1.compareTo(elem2);
            }

            if (result != 0) {
              return result;
            }
          }

          // If the pre-release identifiers are equal to this point, the larger identifier wins.
          return Integer.compare(list1.size(), list2.size());
        }
      };
    }

    public static Builder builder() {
      return new AutoValue_RustVersionRequirement_SemanticVersion.Builder();
    }

    abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder setMajorVersion(int majorVersion);
      abstract Builder setMinorVersion(Optional<Integer> minorVersion);
      abstract Builder setPatchVersion(Optional<Integer> patchVersion);
      abstract Builder setPreReleaseIdentifier(Optional<String> preReleaseIdentifier);

      abstract SemanticVersion build();
    }
  }
}
