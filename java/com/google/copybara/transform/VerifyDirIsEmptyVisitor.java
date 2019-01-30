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
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import javax.annotation.Nullable;

/**
 * A visitor which recursively verifies there are no files or symlinks in a directory tree.
 */
final class VerifyDirIsEmptyVisitor extends SimpleFileVisitor<Path> {
  private final Path root;
  @Nullable
  private final PathMatcher pathMatcher;
  private final ArrayList<String> existingFiles = new ArrayList<>();

  VerifyDirIsEmptyVisitor(Path root, @Nullable PathMatcher pathMatcher) {
    this.root = Preconditions.checkNotNull(root);
    this.pathMatcher = pathMatcher;
  }

  @Override
  public FileVisitResult visitFile(Path source, BasicFileAttributes attrs) {
    Path relative = root.relativize(source);
    if (pathMatcher == null || pathMatcher.matches(relative)) {
      existingFiles.add(relative.toString());
    }
    return FileVisitResult.CONTINUE;
  }

  void walk() throws IOException, ValidationException {
    Files.walkFileTree(root, this);
    if (!existingFiles.isEmpty()) {
      Collections.sort(existingFiles);
      throw new ValidationException(String.format("Files already exist in %s: %s",
          root, existingFiles));
    }
  }
}
