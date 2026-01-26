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
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubHostTest {

  private GitHubHost githubHost;

  @Before
  public void setup() throws Exception {
    githubHost = new GitHubHost("github.com");
  }

  @Test
  public void testGetProjectNameFromUrl() throws Exception {
    assertThat(githubHost.getProjectNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(githubHost.getProjectNameFromUrl("http://github.com/foo")).isEqualTo("foo");
    assertThat(githubHost.getProjectNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(githubHost.getProjectNameFromUrl("http://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(githubHost.getProjectNameFromUrl("http://github.com/foo/bar/foobar"))
        .isEqualTo("foo/bar");
    assertThat(githubHost.getProjectNameFromUrl("ssh://git@github.com/foo/bar.git"))
        .isEqualTo("foo/bar");
    assertThat(githubHost.getProjectNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(githubHost.getProjectNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo/bar");
    ValidationException noProject =
        assertThrows(
            ValidationException.class, () -> githubHost.getProjectNameFromUrl("foo@bar:baz"));
    assertThat(noProject).hasMessageThat().contains("Cannot find project name");
    ValidationException notGitHub =
        assertThrows(
            ValidationException.class,
            () -> githubHost.getProjectNameFromUrl("file://some/local/dir"));
    assertThat(notGitHub).hasMessageThat().contains("Not a github url");
    ValidationException noUrl =
        assertThrows(ValidationException.class, () -> githubHost.getProjectNameFromUrl(""));
    assertThat(noUrl).hasMessageThat().contains("Empty url");

    // While www.github.com is a valid host, we prefer the non-www version for consistency.
    assertThat(
            assertThrows(
                ValidationException.class,
                () -> githubHost.getProjectNameFromUrl("https://www.github.com/foo")))
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
    assertThat(githubHost.getUserNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(githubHost.getUserNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo");
    assertThat(githubHost.getUserNameFromUrl("ssh://git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(githubHost.getUserNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo");
    assertThat(githubHost.getUserNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo");
    ValidationException e =
        assertThrows(ValidationException.class, () -> githubHost.getUserNameFromUrl("foo@bar:baz"));
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

  @Test
  public void testIsGithubUrl() throws Exception {
    GitHubHost ghesHost = new GitHubHost("depot.code.corp.goog");
    assertThat(ghesHost.isGitHubUrl("git@depot.code.corp.goog:si/copybara")).isTrue();
    assertThat(ghesHost.isGitHubUrl("git@github.com:foo/bar.git")).isFalse();
  }
}
