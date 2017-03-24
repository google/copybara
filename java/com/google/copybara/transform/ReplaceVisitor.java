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
import com.google.copybara.transform.TemplateTokens.Replacer;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;

final class ReplaceVisitor extends SimpleFileVisitor<Path> {

  private final Replacer replacer;
  private final PathMatcher pathMatcher;

  int filesVisited = 0;
  int filesChanged = 0;

  ReplaceVisitor(Replacer replacer, PathMatcher pathMatcher) {
    this.replacer = Preconditions.checkNotNull(replacer);
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    if (!pathMatcher.matches(file)) {
      return FileVisitResult.CONTINUE;
    }
    filesVisited++;
    String originalFileContent = new String(Files.readAllBytes(file), UTF_8);
    String transformed = replacer.replace(originalFileContent);
    if (!originalFileContent.equals(transformed)) {
      filesChanged++;
      Files.write(file, transformed.getBytes(UTF_8));
    }

    return FileVisitResult.CONTINUE;
  }
}
