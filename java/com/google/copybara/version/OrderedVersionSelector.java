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

package com.google.copybara.version;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A selector of selectors that traverses all the selectors in order
 * and returns the first result that is found.
 */
public class OrderedVersionSelector implements VersionSelector {

  private final ImmutableList<VersionSelector> selectors;

  public OrderedVersionSelector(
      ImmutableList<VersionSelector> selectors) {
    this.selectors = selectors;
  }

  @Override
  public Optional<String> select(VersionList versionList, @Nullable String requestedRef,
      Console console) throws ValidationException, RepoException {
    for (VersionSelector selector : selectors) {
      Optional<String> selection = selector.select(versionList, requestedRef, console);
      if (selection.isPresent()) {
        return selection;
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the union of all inner {@code searchPattern}s.
   *
   * Any searchPattern that is "none" is ignored (So composition of selectors that use the
   * requestedRef can be mixed with version selectors that use {@link VersionList}).
   *
   * If any searchPattern is "all", it returns "all" (Version selector is interested in all the
   * versions).
   */
  @Override
  public ImmutableSet<SearchPattern> searchPatterns() {
    ImmutableSet<SearchPattern> result =
        selectors.stream()
            .map(VersionSelector::searchPatterns)
            .flatMap(Collection::stream)
            .filter(p -> !p.isNone())
            .collect(toImmutableSet());

    if (result.stream().anyMatch(SearchPattern::isAll)) {
      return SearchPattern.ALL;
    }
    return result;
  }
}
