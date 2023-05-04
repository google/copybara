/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRevision;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConfigHeuristicsInputProviderTest {
  private String url;
  private Path destination;
  private TestingConsole console;
  private GeneralOptions generalOptions;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    url = "https://example.com/origin";
    destination = Files.createTempDirectory("destination");
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    generalOptions = optionsBuilder.general;

    Path userHomeForTest = Files.createTempDirectory("home");
    optionsBuilder.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    optionsBuilder.setHomeDir(userHomeForTest.toString());
  }

  @Test
  public void doubleWildcardOriginGlobTest() throws Exception {
    InputProviderResolver resolver =
        new InputProviderResolver() {
          @Override
          public <T> T resolve(Input<T> input) throws CannotProvideException {
            try {
              if (input == Inputs.GIT_ORIGIN_URL) {
                return Inputs.GIT_ORIGIN_URL.asValue(new URL(url));
              }
              if (input == Inputs.CURRENT_VERSION) {
                return Inputs.CURRENT_VERSION.asValue("1.0.0");
              }
              if (input == Inputs.GENERATOR_FOLDER) {
                return Inputs.GENERATOR_FOLDER.asValue(destination);
              }
              if (input == Inputs.ORIGIN_GLOB) {
                return Inputs.ORIGIN_GLOB.asValue(Glob.ALL_FILES);
              }
            } catch (MalformedURLException e) {
              Assert.fail("Malformed url, shouldn't happen: " + e);
            }
            throw new CannotProvideException("Cannot provide " + input);
          }
        };

    GitOptions mockGitOptions = mock(GitOptions.class);
    GitRepository mockGitRepository = mock(GitRepository.class);
    ConfigHeuristicsInputProvider inputProvider =
        new ConfigHeuristicsInputProvider(
            mockGitOptions, generalOptions, ImmutableSet.of(), 30, console);
    GitRevision gitRevision =
        new GitRevision(
            mockGitRepository, "a".repeat(40), null, null, ImmutableListMultimap.of(), url);

    when(mockGitOptions.cachedBareRepoForUrl(anyString())).thenReturn(mockGitRepository);
    when(mockGitRepository.withWorkTree(any(Path.class))).thenReturn(mockGitRepository);
    when(mockGitRepository.fetchSingleRef(
            anyString(), anyString(), anyBoolean(), eq(Optional.empty())))
        .thenReturn(gitRevision);

    Optional<Glob> glob = inputProvider.resolve(Inputs.ORIGIN_GLOB, resolver);

    // If the destination is a directory and no exception is thrown, we know that the heuristics was
    // computed
    assertThat(Files.isDirectory(destination)).isTrue();
    assertThat(glob).isEmpty();
  }
}
