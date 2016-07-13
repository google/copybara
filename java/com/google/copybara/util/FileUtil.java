// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util;

import com.google.common.base.Joiner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility methods for files
 */
public final class FileUtil {

  private static final PathMatcher ALL_FILES = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return true;
    }

    @Override
    public String toString() {
      return "**";
    }
  };

  private FileUtil() {
  }

  public static int deleteAllFilesRecursively(Path path) throws IOException {
    return deleteFilesRecursively(path, ALL_FILES);
  }
  /**
   * Deletes the files that match the PathMatcher.
   *
   * <p> Note that this method doesn't delete the directories, only the files inside those
   * directories. This is fine for our use case since the majority of SCMs don't care about empty
   * directories.
   *
   * @throws IOException If it fails traversing or deleting the tree.
   */
  public static int deleteFilesRecursively(Path path, final PathMatcher pathMatcher)
      throws IOException {
    final AtomicInteger counter = new AtomicInteger();
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (pathMatcher.matches(file)) {
          Files.delete(file);
          counter.incrementAndGet();
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return counter.get();
  }

  /**
   * A {@link PathMatcher} that returns true if any of the delegate {@code pathMatchers} returns
   * true.
   */
  static PathMatcher anyPathMatcher(final Iterable<PathMatcher> pathMatchers) {
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        for (PathMatcher pathMatcher : pathMatchers) {
          if (pathMatcher.matches(path)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public String toString() {
        return "anyOf[" + Joiner.on(", ").join(pathMatchers) + "]";
      }
    };
  }

  /**
   * Returns {@link PathMatcher} that negates {@code pathMatcher}
   */
  public static PathMatcher notPathMatcher(final PathMatcher pathMatcher) {
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        return !pathMatcher.matches(path);
      }

      @Override
      public String toString() {
        return "not(" + pathMatcher + ")";
      }
    };
  }

}
