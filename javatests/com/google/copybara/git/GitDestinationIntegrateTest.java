/*
 * Copyright (C) 2016 Google Inc.
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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.Writer;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationIntegrateTest {

  private String url;

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private Glob destinationFiles;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));
    options.setForce(true);

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options, GitModule.class);
  }

  @Test
  public void testNoIntegration() throws ValidationException, IOException, RepoException {
    GitDestination destination = skylark.eval("g", "g ="
        + "git.destination(\n"
        + "    url = '" + url + "',\n"
        + ")");
    Writer<GitRevision> writer = destination.newWriter(destinationFiles,
        /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null);

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    TransformResult result = TransformResults.of(workdir, new DummyRevision("test"))
        .withSummary("Test change\n"
            + "\n"
            + GitModule.DEFAULT_INTEGRATE_LABEL + "=http://should_not_be_used\n");

    writer.write(result, console);

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "master");
    assertThat(showResult).contains("some content");

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testBadLabel() throws ValidationException, IOException, RepoException {
    try {
      runBadLabel(/*ignoreErrors=*/false);
    } catch (RepoException e) {
      assertThat(e.getMessage()
      ).contains("Error executing 'git fetch file:///non_existent_repository");
    }
  }

  @Test
  public void testBadLabel_ignoreErrors() throws ValidationException, IOException, RepoException {
    runBadLabel(/*ignoreErrors=*/true);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  private void runBadLabel(boolean ignoreErrors)
      throws ValidationException, IOException, RepoException {
    GitDestination destination = skylark.eval("g", "g ="
        + "git.destination(\n"
        + "    url = '" + url + "',\n"
        + "    integrates = [git.integrate( "
        + "        ignore_errors = " + (ignoreErrors ? "True" : "False")
        + "    ),],\n"
        + ")");
    Writer<GitRevision> writer = destination.newWriter(destinationFiles,
        /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null);

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    TransformResult result = TransformResults.of(workdir, new DummyRevision("test"))
        .withSummary("Test change\n"
            + "\n"
            + GitModule.DEFAULT_INTEGRATE_LABEL + "=file:///non_existent_repository\n");
    writer.write(result, console);
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(path, getGitEnv(),  /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }
}
