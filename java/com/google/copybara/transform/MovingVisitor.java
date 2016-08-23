// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import javax.annotation.Nullable;

/**
 * A visitor which moves files recursively from the path it is visiting.
 */
final class MovingVisitor extends SimpleFileVisitor<Path> {
  private final Path before;
  private final Path after;
  @Nullable
  private final PathMatcher pathMatcher;

  MovingVisitor(Path before, Path after, @Nullable PathMatcher pathMatcher) {
    this.before = before;
    this.after = after;
    this.pathMatcher = pathMatcher;
  }

  @Override
  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
    return dir.equals(after)
        ? FileVisitResult.SKIP_SUBTREE
        : FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFile(Path source, BasicFileAttributes attrs) throws IOException {
    if (pathMatcher==null || pathMatcher.matches(source)) {
      Path relative = before.relativize(source);
      Path dest = after.resolve(relative);
      Files.createDirectories(dest.getParent());
      Files.move(source, dest, LinkOption.NOFOLLOW_LINKS);
    }
    return FileVisitResult.CONTINUE;
  }
}
