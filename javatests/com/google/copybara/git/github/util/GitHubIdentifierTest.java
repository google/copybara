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

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GitHubIdentifierTest {

  @Test
  public void testValidUrl() {
    String url = "https://github.com/ownername/reponame";

    GitHubIdentifier identifier = GitHubIdentifier.create(url);

    assertThat(identifier.getHostName()).isEqualTo("github.com");
    assertThat(identifier.getOwnerOrOrganizationName()).isEqualTo("ownername");
    assertThat(identifier.getRepoName()).isEqualTo("reponame");
    assertThat(identifier.getPath()).isEqualTo("ownername/reponame");
    assertThat(identifier.getUrl()).isEqualTo(url);
  }

  @Test
  public void testInvalidPath_tooManySlashes() {
    String url = "https://github.com/ownername/reponame/extra";

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> GitHubIdentifier.create(url));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "URL path must contain exactly one '/' separating the owner or organization and the"
                + " repo name.");
  }

  @Test
  public void testInvalidPath_tooFewSlashes() {
    String url = "https://github.com/ownername";

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> GitHubIdentifier.create(url));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "URL path must contain exactly one '/' separating the owner or organization and the"
                + " repo name.");
  }

  @Test
  public void testMissingUrl() {
    assertThrows(NullPointerException.class, () -> GitHubIdentifier.create(null));
  }

  @Test
  public void testTrailingSlash() {
    String url = "https://github.com/ownername/reponame/";

    GitHubIdentifier identifier = GitHubIdentifier.create(url);

    assertThat(identifier.getOwnerOrOrganizationName()).isEqualTo("ownername");
    assertThat(identifier.getRepoName()).isEqualTo("reponame");
  }

  @Test
  public void standardFormats_github() {
    String githubCom = "github.com";
    String path = "foo/bar";
    ImmutableList<String> acceptedFormats =
        ImmutableList.of(
            "http://" + githubCom + "/" + path,
            "https://" + githubCom + "/" + path,
            "http://www." + githubCom + "/" + path,
            "https://www." + githubCom + "/" + path);

    for (String acceptedFormat : acceptedFormats) {
      GitHubIdentifier unused = GitHubIdentifier.create(acceptedFormat);
    }
  }

  @Test
  public void legacyFormats() {
    String githubCom = "github.com";
    String path = "foo/bar";
    ImmutableList<String> acceptedFormats =
        ImmutableList.of(
            "ssh://git@" + githubCom + "/" + path,
            "sso://git@" + githubCom + "/" + path,
            "git://" + githubCom + "/" + path,
            "rpc://" + githubCom + "/" + path,
            "git@" + githubCom + "/" + path,
            "git@" + githubCom + ":" + path);

    for (String acceptedFormat : acceptedFormats) {
      GitHubIdentifier unused = GitHubIdentifier.create(acceptedFormat);
    }
  }

  @Test
  public void standardFormats_ghes() {
    String ghes = "depot.code.corp.goog";
    String path = "foo/bar";
    ImmutableList<String> acceptedFormats =
        ImmutableList.of(
            "http://" + ghes + "/" + path,
            "https://" + ghes + "/" + path,
            "http://www." + ghes + "/" + path,
            "https://www." + ghes + "/" + path,
            "http://" + ghes + "/" + path + ".git");

    for (String acceptedFormat : acceptedFormats) {
      GitHubIdentifier unused = GitHubIdentifier.create(acceptedFormat);
    }
  }
}
