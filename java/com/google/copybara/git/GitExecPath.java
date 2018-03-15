/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git;

import java.nio.file.FileSystems;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Utility for getting the git binary executable path.
 */
public final class GitExecPath {

  private GitExecPath() {
  }

  /**
   * Returns a String representing the git binary to be executed.
   *
   * <p>The env var {@code GIT_EXEC_PATH} determines where Git looks for its sub-programs, but also
   * the regular git binaries (git, git-upload-pack, etc) are duplicated in {@code GIT_EXEC_PATH}.
   *
   * <p>If the env var is not set, then we will execute "git", that it will be resolved in the path
   * as usual.
   */
  public static String resolveGitBinary(@Nullable Map<String, String> environment) {
    if (environment != null && environment.containsKey("GIT_EXEC_PATH")) {
      return FileSystems.getDefault()
          .getPath(environment.get("GIT_EXEC_PATH"))
          .resolve("git")
          .toString();
    }
    return "git";
  }

}
