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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.copybara.util.console.Console;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/**
 * Select a version from a list of versions, using custom logic (For example,
 * semantic versioning, etc.).
 */
public interface VersionSelector extends StarlarkValue {

  Optional<String> select(VersionList versionList, @Nullable String requestedRef,
      Console console)
      throws ValidationException, RepoException;

  /**
   * Give a hint on what the Version selector is interested so that a
   * {@link VersionList} can be more efficient in listing valid versions.
   *
   * A SearchPattern is composed of tokens that are either a literal or an
   * interpolation. The interpolation name is not important and might be ignored
   * by the {@link VersionList}. But if present, it can be used to report debugging
   * information about a particular part of a version found.
   *
   * Two edge cases:
   *
   * - Empty list: Means that the selector doesn't use patterns (For example,
   * because it uses the CLI reference.
   * - Single interpolation token: Means that it is interested in all the
   * references. Equivalent to '*'
   */
  default ImmutableSet<SearchPattern> searchPatterns() {
    return SearchPattern.NONE;
  }

  /**
   * A search pattern is wrapper class of {@link Token} list that expresses the pattern of the
   * versions the {@link VersionSelector} is interested. This allows to have literals mixed with
   * interpolations (e.g. foo.*bar-> [foo, .*, bar]).
   */
  class SearchPattern {

    private final ImmutableList<Token> tokens;
    public static final ImmutableSet<SearchPattern> ALL =
        ImmutableSet.of(new SearchPattern(ImmutableList.of(Token.interpolation("all"))));

    public static final ImmutableSet<SearchPattern> NONE = ImmutableSet.of();

    public SearchPattern(ImmutableList<Token> tokens) {
      this.tokens = tokens;
    }

    public ImmutableList<Token> tokens() {
      return tokens;
    }

    /** Returns true if the search pattern is interested in all references */
    public boolean isAll() {
      return tokens.stream().allMatch(t -> t.getType() == TokenType.INTERPOLATION);
    }

    /**
     * Returns true if the search pattern doesn't use the references from the
     * {@link VersionList} as a primary data for selecting the version.
     */
    public boolean isNone() {
      return tokens.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SearchPattern)) {
        return false;
      }
      SearchPattern that = (SearchPattern) o;
      return tokens.equals(that.tokens);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(tokens);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("tokens", tokens)
          .toString();
    }
  }

  @Override
  default void repr(Printer printer) {
    printer.append(toString());
  }

}
