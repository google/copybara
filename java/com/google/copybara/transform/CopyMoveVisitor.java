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

package com.google.copybara.transform;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import javax.annotation.Nullable;

/**
 * A visitor which copy or moves files recursively from the path it is visiting.
 */
final class CopyMoveVisitor extends SimpleFileVisitor<Path> {
  private final Path before;
  private final Path after;
  @Nullable
  private final PathMatcher pathMatcher;
  private final boolean isCopy;
  private final CopyOption[] moveMode;

  CopyMoveVisitor(Path before, Path after, @Nullable PathMatcher pathMatcher, boolean overwrite, boolean isCopy) {
    this.before = before;
    this.after = after;
    this.pathMatcher = pathMatcher;
    this.isCopy = isCopy;
    if (overwrite) {
      moveMode = new CopyOption[]{LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING};
    } else {
      moveMode = new CopyOption[]{LinkOption.NOFOLLOW_LINKS};
    }
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
      if (isCopy) {
        Files.copy(source, dest, moveMode);
      } else {
        Files.move(source, dest, moveMode);
      }
    }
    return FileVisitResult.CONTINUE;
  }
}
