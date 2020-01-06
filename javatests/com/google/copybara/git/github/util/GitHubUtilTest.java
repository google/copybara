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

package com.google.copybara.git.github.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.github.util.GitHubUtil.getProjectNameFromUrl;
import static com.google.copybara.git.github.util.GitHubUtil.getUserNameFromUrl;
import static com.google.copybara.git.github.util.GitHubUtil.getValidBranchName;
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubUtilTest {

  @Test
  public void testGetProjectNameFromUrl() throws Exception {
    assertThat(getProjectNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(getProjectNameFromUrl("http://github.com/foo")).isEqualTo("foo");
    assertThat(getProjectNameFromUrl("https://www.github.com/foo")).isEqualTo("foo");
    assertThat(getProjectNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("http://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("https://www.github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("ssh://git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo/bar");
    ValidationException noProject =
        assertThrows(ValidationException.class, () -> getProjectNameFromUrl("foo@bar:baz"));
    assertThat(noProject).hasMessageThat().contains("Cannot find project name");
    ValidationException notGitHub =
        assertThrows(
            ValidationException.class, () -> getProjectNameFromUrl("file://some/local/dir"));
    assertThat(notGitHub).hasMessageThat().contains("Not a github url");
    ValidationException noUrl =
        assertThrows(ValidationException.class, () -> getProjectNameFromUrl(""));
    assertThat(noUrl).hasMessageThat().contains("Empty url");
  }

  @Test
  public void testGetUserNameFromUrl() throws Exception {
    assertThat(getUserNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(getUserNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo");
    assertThat(getUserNameFromUrl("ssh://git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(getUserNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(getUserNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo");
    ValidationException e =
        assertThrows(ValidationException.class, () -> getUserNameFromUrl("foo@bar:baz"));
    assertThat(e).hasMessageThat().contains("Cannot find project name");
  }

  @Test
  public void testGetValidBranchName() throws ValidationException {
    assertThat(getValidBranchName("test/cl_1234")).isEqualTo("test/cl_1234");
    assertThat(getValidBranchName("test/cl*1234")).isEqualTo("test/cl_1234");
    ValidationException slash =
        assertThrows(ValidationException.class, () -> getValidBranchName("/test/cl*1234"));
    assertThat(slash).hasMessageThat()
        .isEqualTo("Branch name has invalid prefix: \"/\" or \"refs/\"");
    ValidationException refs =
        assertThrows(ValidationException.class, () -> getValidBranchName("refs/cl_1234"));
    assertThat(refs).hasMessageThat()
        .isEqualTo("Branch name has invalid prefix: \"/\" or \"refs/\"");
  }
}
