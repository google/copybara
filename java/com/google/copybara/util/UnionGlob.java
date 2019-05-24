/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;

/**
 * A glob that is the union of two globs. It matches if any of the two globs matches.
 */
public class UnionGlob extends Glob {

  private final Glob lval;
  private final Glob rval;

  UnionGlob(Glob lval, Glob rval) {
    this.lval = Preconditions.checkNotNull(lval);
    this.rval = Preconditions.checkNotNull(rval);
  }

  @Override
  public PathMatcher relativeTo(Path base) {
    PathMatcher leftMatcher = lval.relativeTo(base);
    PathMatcher rightMatcher = rval.relativeTo(base);
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        return leftMatcher.matches(path) || rightMatcher.matches(path);
      }

      @Override
      public String toString() {
        return UnionGlob.this.toString();
      }
    };
  }

  @Override
  public ImmutableSet<String> roots() {
    return computeRootsFromIncludes(getIncludes());
  }

  @Override
  protected Iterable<String> getIncludes() {
    return Iterables.concat(lval.getIncludes(), rval.getIncludes());
  }

  @Override
  public String toString() {
    return lval + " + " + rval;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UnionGlob unionGlob = (UnionGlob) o;
    return Objects.equals(lval, unionGlob.lval)
        && Objects.equals(rval, unionGlob.rval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lval, rval);
  }
}
