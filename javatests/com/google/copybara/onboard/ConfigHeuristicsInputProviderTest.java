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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigHeuristicsInputProviderTest {
  protected String url;
  protected Path workDir;
  protected Path repoGitDir;
  protected Path destination;
  protected GitRepository origin;
  protected GitOptions gitOptions;
  protected TestingConsole console;
  protected OptionsBuilder optionsBuilder;
  protected GeneralOptions generalOptions;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workDir = Files.createTempDirectory("workdir");
    destination = Files.createTempDirectory("destination");
    
    optionsBuilder = getOptionsBuilder(console);
    String unused = git("init", "--bare", repoGitDir.toString());
    generalOptions = optionsBuilder.general;
    gitOptions = optionsBuilder.git;
    origin =
        GitRepository.newBareRepo(
            repoGitDir,
            new GitEnvironment(optionsBuilder.general.getEnvironment()),
            /*verbose*/ true,
            DEFAULT_TIMEOUT,
            false);
    url = "file:///" + origin.getGitDir();
    origin = repo().withWorkTree(workDir);
  }

  @Test
  public void doubleWildcardOriginGlobTest() throws Exception {
    Files.writeString(workDir.resolve("foo.txt"), "hi");
    origin.add().files("foo.txt").run();
    origin.simpleCommand("commit", "foo.txt", "-m", "message");

    origin.tag("1.0.0").run();

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

    ConfigHeuristicsInputProvider inputProvider =
        new ConfigHeuristicsInputProvider(
            gitOptions, generalOptions, ImmutableSet.of(), 30, console);
    Optional<Glob> glob = inputProvider.resolve(Inputs.ORIGIN_GLOB, resolver);

    // The result is an empty glob rather than glob(include = ["**"], exclude = ["**"])
    assertThat(Files.isDirectory(workDir)).isTrue();
    assertThat(glob).isEmpty();
  }

  @Test
  public void gitFuzzyLastRevTest() throws Exception {
    Files.writeString(workDir.resolve("foo.txt"), "hi");
    Files.writeString(workDir.resolve("bar.txt"), "bye");
    origin.add().files("foo.txt", "bar.txt").run();
    origin.simpleCommand("commit", "foo.txt", "-m", "message");
    origin.simpleCommand("commit", "bar.txt", "-m", "message");

    Files.writeString(destination.resolve("foo.txt"), "hi");

    origin.tag("v1.0.0").run();

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

    ConfigHeuristicsInputProvider inputProvider =
        new ConfigHeuristicsInputProvider(
            gitOptions, generalOptions, ImmutableSet.of(), 30, console);
    Glob expectedGlob = Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("bar.txt"));
    Optional<Glob> glob = inputProvider.resolve(Inputs.ORIGIN_GLOB, resolver);

    // The glob was computed and the version was matched with the git tag
    assertThat(Files.isDirectory(workDir)).isTrue();
    assertThat(glob).hasValue(expectedGlob);
    assertThat(console.getMessages())
        .contains(
            new Message(MessageType.INFO, "Assuming version 1.0.0 references v1.0.0 (1.0.0)"));
  }

  public OptionsBuilder getOptionsBuilder(TestingConsole console) throws IOException {
    return new OptionsBuilder().setConsole(this.console).setOutputRootToTmpDir();
  }

  private String git(String... argv) throws RepoException {
    return repo().git(repoGitDir, argv).getStdout();
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(
        path, getEnv(), /* verbose= */ true, DEFAULT_TIMEOUT, /* noVerify= */ false);
  }

  public GitEnvironment getEnv() {
    Map<String, String> joinedEnv = Maps.newHashMap(optionsBuilder.general.getEnvironment());
    joinedEnv.putAll(getGitEnv().getEnvironment());
    return new GitEnvironment(joinedEnv);
  }
}
