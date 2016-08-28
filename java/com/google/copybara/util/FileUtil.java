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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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

  private FileUtil() {}

  private static final Pattern RELATIVISM = Pattern.compile("(.*/)?[.][.]?(/.*)?");

  /**
   * Checks that the given path is relative and does not contain any {@code .} or {@code ..}
   * components.
   *
   * @returns the {@code path} passed
   */
  public static String checkNormalizedRelative(String path) {
    Preconditions.checkArgument(!RELATIVISM.matcher(path).matches(),
        "path has unexpected . or .. components: %s", path);
    Preconditions.checkArgument(!path.startsWith("/"),
        "path must be relative, but it starts with /: %s", path);
    return path;
  }

  /**
   * Checks that the given path is relative and does not contain any {@code .} or {@code ..}
   * components.
   *
   * @returns the {@code path} passed
   */
  public static Path checkNormalizedRelative(Path path) {
    checkNormalizedRelative(path.toString());
    return path;
  }

  /**
   * Copies files from {@code from} directory to {@code to} directory. If any file exist in the
   * destination it fails instead of overwriting.
   *
   * <p>File attributes are also copied.
   *
   * <p>Symlinks are kept as in the origin. If a symlink points to "../foo" it will point to
   * that "../foo" in the destination. If it points to "/usr/bin/foo" it will point to
   * "/usr/bin/foo"
   */
  public static void copyFilesRecursively(final Path from, final Path to) throws IOException {
    Preconditions.checkArgument(Files.isDirectory(from), "%s (from) is not a directory");
    Preconditions.checkArgument(Files.isDirectory(to), "%s (to) is not a directory");
    Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path destFile = to.resolve(from.relativize(file));
        Files.createDirectories(destFile.getParent());

        if (Files.isSymbolicLink(file)) {
          Path realDestination = Files.readSymbolicLink(file);
          Files.createSymbolicLink(destFile, realDestination);
          return FileVisitResult.CONTINUE;
        }
        Files.copy(file, destFile, StandardCopyOption.COPY_ATTRIBUTES);
        return FileVisitResult.CONTINUE;
      }
    });
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
  static PathMatcher anyPathMatcher(final ImmutableList<PathMatcher> pathMatchers) {
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
