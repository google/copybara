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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.RegexTemplateTokens;
import com.google.copybara.util.FileUtil;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** A visitor which copy or moves files recursively from the path it is visiting. */
final class CopyMoveRegexVisitor extends SimpleFileVisitor<Path> {
  private final RegexTemplateTokens before;
  private final RegexTemplateTokens after;
  private final PathMatcher pathMatcher;
  private final Path workDir;
  private final boolean isCopy;
  private final CopyOption[] moveMode;

  private final List<Action> actionsToTake = new ArrayList<>();
  @Nullable private ValidationException validationError;

  private CopyMoveRegexVisitor(
      RegexTemplateTokens before,
      RegexTemplateTokens after,
      PathMatcher pathMatcher,
      Path workDir,
      boolean overwrite,
      boolean isCopy) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
    this.workDir = Preconditions.checkNotNull(workDir).normalize();
    this.isCopy = isCopy;
    if (overwrite) {
      moveMode = new CopyOption[] {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING};
    } else {
      moveMode = new CopyOption[] {LinkOption.NOFOLLOW_LINKS};
    }
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    if (pathMatcher.matches(file)) {
      String relativeFile = workDir.relativize(file).toString();
      String relativeDest =
          before.replacer(after, true, false, ImmutableList.of()).replace(relativeFile);
      if (relativeFile.equals(relativeDest)) {
        // Either the regex didn't match, or it did match but returned the same file name
        return FileVisitResult.CONTINUE;
      }
      Path dest;
      try {
        dest = resolveDestination(relativeFile, relativeDest);
      } catch (ValidationException e) {
        this.validationError = e;
        return FileVisitResult.TERMINATE;
      }
      this.actionsToTake.add(new CopyOrMoveAction(file, dest));
    }
    return FileVisitResult.CONTINUE;
  }

  private Path resolveDestination(String relativeFile, String relativeDest)
      throws ValidationException {
    try {
      FileUtil.checkNormalizedRelative(relativeDest);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(invalidDestMessage(relativeFile, e.getMessage()), e);
    }
    Path dest = workDir.resolve(relativeDest).normalize();
    if (!dest.startsWith(workDir)) {
      throw new ValidationException(
          invalidDestMessage(
              relativeFile, String.format("'%s' is outside the checkout directory", relativeDest)));
    }
    return dest;
  }

  private String invalidDestMessage(String relativeFile, String reason) {
    return String.format(
        "Regex substitution on '%s' (with after = '%s') produced invalid destination: %s",
        relativeFile, after, reason);
  }

  @Override
  public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
    if (exc != null) {
      throw exc;
    }
    if (!isCopy) {
      this.actionsToTake.add(new DeleteDirectoryAction(dir));
    }
    return FileVisitResult.CONTINUE;
  }

  public static boolean run(
      Path root,
      RegexTemplateTokens before,
      RegexTemplateTokens after,
      PathMatcher pathMatcher,
      Path workDir,
      boolean overwrite,
      boolean isCopy)
      throws IOException, ValidationException {
    CopyMoveRegexVisitor visitor =
        new CopyMoveRegexVisitor(before, after, pathMatcher, workDir, overwrite, isCopy);
    Files.walkFileTree(root, visitor);
    if (visitor.validationError != null) {
      throw visitor.validationError;
    }

    // Start to execute actions only after we finish walking the tree, to make sure we don't
    // copy/move the same file twice.
    boolean someActionSucceeded = false;
    for (Action action : visitor.actionsToTake) {
      someActionSucceeded |= action.run();
    }
    return someActionSucceeded;
  }

  private interface Action {
    public boolean run() throws IOException;
  }

  private class CopyOrMoveAction implements Action {
    private final Path file;
    private final Path dest;

    private CopyOrMoveAction(Path file, Path dest) {
      this.file = file;
      this.dest = dest;
    }

    @Override
    public boolean run() throws IOException {
      Files.createDirectories(dest.getParent());
      if (isCopy) {
        Files.copy(file, dest, moveMode);
      } else {
        Files.move(file, dest, moveMode);
      }
      return true;
    }
  }

  private static class DeleteDirectoryAction implements Action {
    private final Path dir;

    private DeleteDirectoryAction(Path dir) {
      this.dir = dir;
    }

    @Override
    public boolean run() throws IOException {
      try {
        Files.delete(dir);
        return true;
      } catch (DirectoryNotEmptyException ignore) {
        return false;
      }
    }
  }
}
