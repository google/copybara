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

package com.google.copybara;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.copybara.exception.NotADestinationFileException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Visitor that verifies that all files in a checkout dir match the {@code destination_files} glob.
 */
class ValidateDestinationFilesVisitor extends SimpleFileVisitor<Path> {

  private final PathMatcher destinationFiles;
  private final Path checkoutDir;
  private ArrayList<Path> invalidPaths;

  ValidateDestinationFilesVisitor(Glob destinationFiles, Path checkoutDir) {
    this.destinationFiles = destinationFiles.relativeTo(checkoutDir);
    this.checkoutDir = checkNotNull(checkoutDir, "checkoutDir");
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
    if (!destinationFiles.matches(file)) {
      invalidPaths.add(checkoutDir.relativize(file));
    }
    return FileVisitResult.CONTINUE;
  }

  /**
   * Checks that all paths in the checkout dir match {@code destinationFiles}. If any one does not,
   * it throws an exception which indicates that the {@code destination_files} setting in the config
   * is incorrect.
   *
   * <p>This can be used to verify that a file that is being written to the destination is actually
   * intended to be written by the user. Also note that for some {@link Destination}
   * implementations, a {@code destination_files} that doesn't match some destination paths will
   * not be able to delete the path later if a commit removes it. This is because the
   * {@code destination_files} glob is often used to determine if a path not in the origin should be
   * preserved.
   *
   * @throws NotADestinationFileException if the given path does not match the {@link PathMatcher}
   */
  void verifyFilesToWrite() throws NotADestinationFileException, IOException {
    checkState(invalidPaths == null,
        "This method already ran with the following results: %s", invalidPaths);
    this.invalidPaths = new ArrayList<>();
    Files.walkFileTree(checkoutDir, this);

    if (!invalidPaths.isEmpty()) {
      Collections.sort(invalidPaths);
      throw new NotADestinationFileException(String.format(
          "Attempted to write these files in the destination, but they are not covered by "
          + "destination_files: %s.\nYour destination_files are %s.",
          invalidPaths, destinationFiles));
    }
  }
}
