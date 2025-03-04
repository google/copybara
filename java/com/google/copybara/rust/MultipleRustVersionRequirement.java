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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.ValidationException;

/**
 * A class that handles multiple {@link RustVersionRequirement} instances.
 */
public class MultipleRustVersionRequirement extends RustVersionRequirement {
  private final ImmutableList<RustVersionRequirement> requirements;

  /**
   * Returns true if this class can handle the given Cargo version requirement.
   *
   * @param requirement The version requirement to check.
   * @return A boolean indicating whether the requirement string can be handled by this class.
   */
  public static boolean handlesRequirement(String requirement) {
    ImmutableList<String> requirementStrings = splitMultipleRequirements(requirement);

    return requirementStrings.size() > 1;
  }

  public static MultipleRustVersionRequirement create(String requirement)
      throws ValidationException {
    return new MultipleRustVersionRequirement(requirement);
  }

  private MultipleRustVersionRequirement(String requirement) throws ValidationException {
    super(requirement);
    ImmutableList<String> requirementStrings = splitMultipleRequirements(requirement);

    ImmutableList.Builder<RustVersionRequirement> requirementsBuilder = ImmutableList.builder();
    try {
      for (String requirementString : requirementStrings) {
        requirementsBuilder.add(RustVersionRequirement.getVersionRequirement(requirementString, false));
      }
    } catch (ValidationException e) {
      throw new ValidationException(
          String.format(
              "The requirement %s is not a valid multiple version requirement.", requirement), e);
    }

    requirements = requirementsBuilder.build();
  }

  private static ImmutableList<String> splitMultipleRequirements(String requirement) {
    return ImmutableList.copyOf(
        Splitter.on(",").trimResults().omitEmptyStrings().split(requirement));
  }

  @Override
  public boolean fulfills(String version) throws ValidationException {
    for (RustVersionRequirement requirement : requirements) {
      if (!requirement.fulfills(version)) {
        return false;
      }
    }
    return true;
  }
}
