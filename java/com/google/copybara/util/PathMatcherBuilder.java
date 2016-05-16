// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

/**
 * A {@link PathMatcher} builder that creates a PathMatcher relative to a {@link Path}.
 */
public final class PathMatcherBuilder {

  private final ImmutableList<String> paths;

  private PathMatcherBuilder(ImmutableList<String> paths) {
    this.paths = paths;
  }

  /**
   * Creates a function {@link PathMatcherBuilder} that when a {@link Path} is passed it returns a
   * {@link PathMatcher} relative to the path.
   *
   * @param validationFs filesystem used for validating the {@code PathMather} construction. Usually
   * this should be the same as the one of the {@link Path} passed to {@link
   * PathMatcherBuilder#relativeTo(Path)}.
   * @param paths list of strings representing globs
   */
  public static PathMatcherBuilder create(FileSystem validationFs, final List<String> paths)
      throws ConfigValidationException {
    ImmutableList<String> pathsCopy = ImmutableList.copyOf(paths);
    // Validate the paths so that they don't contain invalid patterns.
    try {
      listOfGlobsPathMatcher(validationFs.getPath("/does/not/matter"), pathsCopy);
    } catch (IllegalArgumentException e) {
      throw new ConfigValidationException(
          "Cannot create a list of globs from: '" + pathsCopy + "': " + e.getMessage(), e);
    }

    return new PathMatcherBuilder(pathsCopy);
  }

  public PathMatcher relativeTo(Path path) {
    try {
      return listOfGlobsPathMatcher(path, paths);
    } catch (ConfigValidationException e) {
      throw new IllegalStateException("Should never happen", e);
    }
  }

  public boolean isEmpty() {
    return paths.isEmpty();
  }

  @Override
  public String toString() {
    return "[" + Joiner.on(" ,").join(paths) + "]";
  }

  private static PathMatcher listOfGlobsPathMatcher(Path basePath, Iterable<String> paths)
      throws ConfigValidationException {
    Preconditions.checkNotNull(paths, "paths argument cannot be null");
    ImmutableList.Builder<PathMatcher> pathMatchers = ImmutableList.builder();
    for (String path : paths) {
      pathMatchers.add(ReadablePathMatcher.relativeGlob(basePath, path));
    }
    return FileUtil.anyPathMatcher(pathMatchers.build());
  }
}
