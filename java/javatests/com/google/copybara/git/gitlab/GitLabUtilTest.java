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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitLabUtilTest {
  @Test
  public void getUrlEncodedProjectPath() {
    URI url = URI.create("https://gitlab.com/project/repo");

    String result = GitLabUtil.getUrlEncodedProjectPath(url);

    assertThat(result).isEqualTo(URLEncoder.encode("project/repo", UTF_8));
  }

  @Test
  public void getUrlEncodedProjectPath_handleTrailingGit() {
    URI url = URI.create("https://gitlab.com/project/repo.git");

    String result = GitLabUtil.getUrlEncodedProjectPath(url);

    assertThat(result).isEqualTo(URLEncoder.encode("project/repo", UTF_8));
  }

  @Test
  public void getUrlEncodedProjectPath_handleLeadingSlash() {
    URI url = URI.create("https://gitlab.com//project/repo");

    String result = GitLabUtil.getUrlEncodedProjectPath(url);

    assertThat(result).isEqualTo(URLEncoder.encode("project/repo", UTF_8));
  }
}
