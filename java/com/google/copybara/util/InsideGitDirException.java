/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.util;

import java.nio.file.Path;

/**
 * Thrown when trying to run git diff/apply inside a git directory for directories that shouldn't
 * be inside a git dir.
 */
public class InsideGitDirException extends Exception {

  private String gitDirPath;
  private Path path;

  InsideGitDirException(String message, String gitDirPath, Path path) {
    super(message);
    this.gitDirPath = gitDirPath;
    this.path = path;
  }

  /**
   * The git directory that contains {@link #path}
   */
  public String getGitDirPath() {
    return gitDirPath;
  }

  /**
   * The offending path that is inside a git directory
   */
  public Path getPath() {
    return path;
  }
}
