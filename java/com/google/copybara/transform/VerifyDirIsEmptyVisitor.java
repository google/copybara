// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.ValidationException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A visitor which recursively verifies there are no files or symlinks in a directory tree.
 */
final class VerifyDirIsEmptyVisitor extends SimpleFileVisitor<Path> {
  private final Path root;
  private final ArrayList<String> existingFiles = new ArrayList<>();

  VerifyDirIsEmptyVisitor(Path root) {
    this.root = root;
  }

  @Override
  public FileVisitResult visitFile(Path source, BasicFileAttributes attrs) throws IOException {
    existingFiles.add(root.relativize(source).toString());
    return FileVisitResult.CONTINUE;
  }

  void walk() throws IOException, ValidationException {
    Files.walkFileTree(root, this);
    if (!existingFiles.isEmpty()) {
      Collections.sort(existingFiles);
      throw new ValidationException(
          String.format("Files already exist in %s: %s", root, existingFiles));
    }
  }
}
