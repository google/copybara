/*
 * Copyright (C) 2020 Google Inc.
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
import static com.google.copybara.git.github.util.GitHubHost.GITHUB_COM;
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubHostTest {

  @Test
  public void testGetProjectNameFromUrl() throws Exception {
    assertThat(GITHUB_COM.getProjectNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(GITHUB_COM.getProjectNameFromUrl("http://github.com/foo")).isEqualTo("foo");
    assertThat(GITHUB_COM.getProjectNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(GITHUB_COM.getProjectNameFromUrl("http://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(GITHUB_COM.getProjectNameFromUrl("http://github.com/foo/bar/foobar"))
        .isEqualTo("foo/bar");
    assertThat(GITHUB_COM.getProjectNameFromUrl("ssh://git@github.com/foo/bar.git"))
        .isEqualTo("foo/bar");
    assertThat(GITHUB_COM.getProjectNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(GITHUB_COM.getProjectNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo/bar");
    ValidationException noProject =
        assertThrows(
            ValidationException.class, () -> GITHUB_COM.getProjectNameFromUrl("foo@bar:baz"));
    assertThat(noProject).hasMessageThat().contains("Cannot find project name");
    ValidationException notGitHub =
        assertThrows(
            ValidationException.class,
            () -> GITHUB_COM.getProjectNameFromUrl("file://some/local/dir"));
    assertThat(notGitHub).hasMessageThat().contains("Not a github url");
    ValidationException noUrl =
        assertThrows(ValidationException.class, () -> GITHUB_COM.getProjectNameFromUrl(""));
    assertThat(noUrl).hasMessageThat().contains("Empty url");

    // While www.github.com is a valid host, we prefer the non-www version for consistency.
    assertThat(
            assertThrows(
                ValidationException.class,
                () -> GITHUB_COM.getProjectNameFromUrl("https://www.github.com/foo")))
        .hasMessageThat()
        .contains("Expected host: github.com");
  }

  @Test
  public void testGetProjectNameFromUrlEnterpriseHost() throws Exception {
    GitHubHost host = new GitHubHost("some.company.example.com");
    assertThat(host.getProjectNameFromUrl("https://some.company.example.com/foo")).isEqualTo("foo");
    assertThat(host.getProjectNameFromUrl("http://some.company.example.com/foo")).isEqualTo("foo");
    assertThat(host.getProjectNameFromUrl("https://some.company.example.com/foo/bar"))
        .isEqualTo("foo/bar");
    assertThat(host.getProjectNameFromUrl("http://some.company.example.com/foo/bar"))
        .isEqualTo("foo/bar");
    assertThat(host.getProjectNameFromUrl("ssh://git@some.company.example.com/foo/bar.git"))
        .isEqualTo("foo/bar");
    assertThat(host.getProjectNameFromUrl("git@some.company.example.com/foo/bar.git"))
        .isEqualTo("foo/bar");
    assertThat(host.getProjectNameFromUrl("git@some.company.example.com:foo/bar.git"))
        .isEqualTo("foo/bar");
  }

  @Test
  public void testGetUserNameFromUrl() throws Exception {
    GitHubHost githubCom = GITHUB_COM;
    assertThat(githubCom.getUserNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(githubCom.getUserNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo");
    assertThat(githubCom.getUserNameFromUrl("ssh://git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(githubCom.getUserNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(githubCom.getUserNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo");
    ValidationException e =
        assertThrows(ValidationException.class, () -> githubCom.getUserNameFromUrl("foo@bar:baz"));
    assertThat(e).hasMessageThat().contains("Cannot find project name");
  }

  @Test
  public void testGetUserNameFromUrlEnterpriseHost() throws Exception {
    GitHubHost host = new GitHubHost("some.company.example.com");
    assertThat(host.getUserNameFromUrl("https://some.company.example.com/foo")).isEqualTo("foo");
    assertThat(host.getUserNameFromUrl("https://some.company.example.com/foo/bar"))
        .isEqualTo("foo");
    assertThat(host.getUserNameFromUrl("ssh://git@some.company.example.com/foo/bar.git"))
        .isEqualTo("foo");
    assertThat(host.getUserNameFromUrl("git@some.company.example.com/foo/bar.git"))
        .isEqualTo("foo");
    assertThat(host.getUserNameFromUrl("git@some.company.example.com:foo/bar.git"))
        .isEqualTo("foo");
  }
}
