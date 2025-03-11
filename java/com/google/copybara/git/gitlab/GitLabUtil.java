/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/** Utility class for GitLab endpoints. */
public class GitLabUtil {
  private GitLabUtil() {}

  /**
   * Returns the URL-encoded project path for a given GitLab repository URL.
   *
   * <p>The project path is defined as the namespace plus the project name.
   *
   * <p>The URL encoded project path is used for querying merge requests, and possibly other
   * entities, from the public REST API.
   *
   * @param repoUrl the URL to extract the project path from
   * @return the encoded project path
   * @see <a href="https://docs.gitlab.com/api/rest/#namespaced-paths">GitLAb API Namespaced paths
   *     docs</a>
   */
  public static String getUrlEncodedProjectPath(URI repoUrl) {
    String path = repoUrl.getPath().trim().toLowerCase(Locale.ROOT);
    // Remove any leading '/'
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    // Remove any trailing .git
    if (path.endsWith(".git")) {
      path = path.substring(0, path.length() - ".git".length());
    }
    return URLEncoder.encode(path, UTF_8);
  }
}
