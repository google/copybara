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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Visitor for the {@link VerifyMatch} transformation. Verifies that a regular expression matches
 * content in every visited file (or in no visited file, if {@code verifyNoMatch} is set).
 */
final class VerifyMatchVisitor extends SimpleFileVisitor<Path> {

  private final Pattern regEx;
  private final boolean verifyNoMatch;
  private final PathMatcher pathMatcher;

  private final ImmutableList.Builder<String> errorBuilder = ImmutableList.builder();

  VerifyMatchVisitor(Pattern regEx, PathMatcher pathMatcher, boolean verifyNoMatch) {
    this.regEx = Preconditions.checkNotNull(regEx);
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
    this.verifyNoMatch = verifyNoMatch;
  }

  ImmutableList<String> getErrors() {
    return errorBuilder.build();
  }
  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    if (!pathMatcher.matches(file)) {
      return FileVisitResult.CONTINUE;
    }
    String originalFileContent = new String(Files.readAllBytes(file), UTF_8);
    if (verifyNoMatch == regEx.matcher(originalFileContent).find()) {
      errorBuilder.add(file.toString());
    }
    return FileVisitResult.CONTINUE;
  }
}
