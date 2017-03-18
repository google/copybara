package com.google.copybara.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

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
    return path -> leftMatcher.matches(path) || rightMatcher.matches(path);
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
}
