package com.google.copybara.util;

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

  public static final PathMatcher ALL_FILES_MATCHER = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return true;
    }
  };

  private FileUtil() {
  }

  /**
   * Deletes the files that match the PathMatcher.
   *
   * <p> Note that this method doesn't delete the directories, only the files inside
   * those directories. This is fine for our use case since the majority of SCMs don't
   * care about empty directories.
   *
   * @throws IOException If it fails traversing or deleting the tree.
   */
  public static int deleteFilesRecursively(Path path, final PathMatcher pathMatcher) throws IOException {
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
}
