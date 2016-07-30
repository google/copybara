// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Core;
import com.google.copybara.Options;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.config.skylark.OptionsAwareModule;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
