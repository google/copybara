/*
 * Copyright (C) 2022 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.nio.file.FileSystems;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public class GitHubPrIntegrateLabelTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock GitRepository repo;
  private final TestingConsole console = new TestingConsole();

  @Test
  public void parseLabel_sha1() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    GitHubPrIntegrateLabel label =
        GitHubPrIntegrateLabel.parse(
            "https://github.com/foo/bar/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc   ",
            repo,
            options);

    assertThat(label).isNotNull();
    assertThat(label.matchesDestinationUrl("https://github.com/foo/bar")).isTrue();
  }

  @Test
  public void parseLabel_containsDot() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    GitHubPrIntegrateLabel label =
        GitHubPrIntegrateLabel.parse(
            "https://github.com/foo/bar.cpp/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc   ",
            repo,
            options);

    assertThat(label).isNotNull();
    assertThat(label.matchesDestinationUrl("https://github.com/foo/bar.cpp")).isTrue();
  }

  @Test
  public void parseLabel_customHost() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    GitHubPrIntegrateLabel label =
        GitHubPrIntegrateLabel.parse(
            "https://some.github.enterprise.instance/foo/bar/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc   ",
            repo,
            options);

    assertThat(label).isNotNull();
    assertThat(label.matchesDestinationUrl("https://some.github.enterprise.instance/foo/bar"))
        .isTrue();
    assertThat(label.getProjectId()).isEqualTo("foo/bar");
    assertThat(label.toString())
        .isEqualTo(
            "https://some.github.enterprise.instance/foo/bar/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc");
  }

  @Test
  public void parseLabel_invalidUrl_returnsNull(
      @TestParameter({
            "https://github.com/foo.cpp/bar",
            "https://github.com/foo",
            "https://github.com/foo/bar/baz"
          })
          String invalidRepoUrl) {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    assertThat(
            GitHubPrIntegrateLabel.parse(
                invalidRepoUrl
                    + "/pull/18 from copybarrista:main"
                    + " dbb8386719596088dbf7513fad87559b2ff796cc",
                repo,
                options))
        .isNull();
    console
        .assertThat()
        .onceInLog(MessageType.WARNING, "Invalid GitHub URL in integrate label '.*': .*");
  }

  @Test
  public void matchesDestinationUrl_mismatchedDestinationUrl_returnsFalse(
      @TestParameter({
            "https://some.github.enterprise.instance/foo/bar",
            "https://github.com/other/bar",
            "https://github.com/foo/other"
          })
          String destinationUrl) {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    GitHubPrIntegrateLabel label =
        GitHubPrIntegrateLabel.parse(
            "https://github.com/foo/bar/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc",
            repo,
            options);

    assertThat(label).isNotNull();
    assertThat(label.matchesDestinationUrl(destinationUrl)).isFalse();
    console
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "GitHub PR integrate label URL 'https://github.com/foo/bar' does not match destination"
                + " URL '"
                + destinationUrl
                + "'");
  }

  @Test
  public void matchesDestinationUrl_invalidDestinationUrl_returnsFalse() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    GitHubPrIntegrateLabel label =
        GitHubPrIntegrateLabel.parse(
            "https://github.com/foo/bar/pull/18"
                + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc",
            repo,
            options);

    assertThat(label).isNotNull();
    assertThat(label.matchesDestinationUrl("file:///tmp/foo/bar")).isFalse();
    console
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "Destination URL 'file:///tmp/foo/bar' is not a valid GitHub URL for integrate label"
                + " '.*': .*");
  }

  @Test
  public void parseLabel_hyphenatedUser() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    assertThat(
            GitHubPrIntegrateLabel.parse(
                "https://github.com/foo/bar/pull/18"
                    + " from hyphenated-user:main dbb8386719596088dbf7513fad87559b2ff796cc   ",
                repo,
                options))
        .isNotNull();
  }

  @Test
  public void parseLabel_sha256() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    assertThat(
            GitHubPrIntegrateLabel.parse(
                "https://github.com/foo/bar/pull/18 from copybarrista:main"
                    + " dbb8386719596088dbf7513fad87559b2ff796ccdbb8386719596088dbf7513f   ",
                repo,
                options))
        .isNotNull();
  }
}
