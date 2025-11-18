/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.version;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterables;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;

/** A version selector that uses a custom comparator to select the version. */
public final class CustomVersionSelector implements VersionSelector {
  private final StarlarkCallable comparator;
  @Nullable private final String filterByRegex;

  /**
   * Creates a new CustomVersionSelector.
   *
   * @param comparator the custom comparator to use
   * @param filterByRegex the regex to filter the potential version candidates by
   * @throws IllegalArgumentException if the comparator is not a StarlarkFunction or if the
   *     comparator does not take two strings arguments named 'left' and 'right'
   */
  public CustomVersionSelector(StarlarkCallable comparator, @Nullable String filterByRegex) {
    this.comparator = enforceStarlarkCallable(comparator);
    this.filterByRegex = filterByRegex;
  }

  /**
   * Selects the latest version from the version list that matches the filter regex and the custom
   * comparator.
   *
   * @param versionList the list of versions to select from
   * @param requestedRef the reference of the requested version,
   * @param console the console to use for logging
   * @return the latest version that matches the filter regex and the custom comparator
   * @throws IllegalStateException if the comparator returns a comparison result outside of [-1, 1]
   *     or if the comparator throws an exception during execution
   */
  @Override
  public Optional<String> select(
      VersionList versionList, @Nullable String requestedRef, Console console)
      throws ValidationException, RepoException {
    ImmutableList<String> filteredVersions = filterByRegexMatch(versionList.list());

    if (versionList.list().isEmpty()) {
      return Optional.empty();
    }

    ImmutableList<String> sortedVersions =
        ImmutableList.sortedCopyOf(
            (left, right) -> {
              int result = callCustomComparator(left, right, console);
              if (result < -1 || result > 1) {
                throw new IllegalStateException(
                    String.format(
                        "Attempted to call comparator '%s' left=%s, right=%s and got a comparison"
                            + " result of %d",
                        comparator.getName(), left, right, result));
              }
              return result;
            },
            filteredVersions);

    return sortedVersions.isEmpty()
        ? Optional.empty()
        : Optional.of(Iterables.getLast(sortedVersions));
  }

  private ImmutableList<String> filterByRegexMatch(Set<String> versionList) {
    if (Strings.isNullOrEmpty(filterByRegex)) {
      return ImmutableList.copyOf(versionList);
    }
    Pattern pattern = Pattern.compile(filterByRegex);
    return versionList.stream()
        .filter(s -> pattern.matcher(s).matches())
        .collect(toImmutableList());
  }

  private int callCustomComparator(String left, String right, Console console) {
    try (Mutability mutability = Mutability.create("custom_version_selector_comparator")) {
      return ((StarlarkInt)
              Starlark.call(
                  StarlarkThread.createTransient(mutability, StarlarkSemantics.DEFAULT),
                  comparator,
                  /* args= */ ImmutableList.of(),
                  ImmutableMap.of("left", left, "right", right)))
          .toInt("user comparator");
    } catch (EvalException | InterruptedException e) {
      console.errorFmt(
          "Failed to excecute custom comparator. The exception was %s", e.getMessage());
      // this is going to throw progragate an upstream exception since it is not in [-1, 1]
      return -2;
    }
  }

  @CanIgnoreReturnValue
  private StarlarkCallable enforceStarlarkCallable(StarlarkCallable comparator) {
    Preconditions.checkArgument(
        comparator instanceof StarlarkFunction,
        "Comparator must be a StarlarkFunction but was %s",
        comparator.getClass().getName());
    ImmutableList<String> parameterNames = ((StarlarkFunction) comparator).getParameterNames();
    Preconditions.checkArgument(
        ImmutableMultiset.copyOf(parameterNames).equals(ImmutableMultiset.of("left", "right")),
        "The comparator must take two strings arguments named 'left' and 'right'");

    return comparator;
  }
}
