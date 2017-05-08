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
import com.google.copybara.Destination;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubmodulesInDestinationTest {

  private String url;
  private String fetch;
  private String push;

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private Glob destinationFiles;
  private SkylarkTestExecutor skylark;
  private GitRepository submodule;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("SubmodulesInDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options, GitModule.class);

    submodule = GitRepository.initScratchRepo(
        /*verbose=*/true, getGitEnv(), options.general.getTmpDirectoryFactory());

    Files.write(submodule.getWorkTree().resolve("foo"), new byte[] {1});
    submodule.add().files("foo").run();
    submodule.simpleCommand("commit", "-m", "dummy commit");
  }

  private GitRepository repo() {
    return new GitRepository(repoGitDir, /*workTree=*/null, /*verbose=*/true, getGitEnv());
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GitDestination destination() throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + ")", url, fetch, push));
  }

  private void writeWithSubmoduleInDestination() throws Exception {
    fetch = "master";
    push = "master";

    Path scratchTree = Files.createTempDirectory("SubmodulesInDestinationTest-scratchTree");
    GitRepository scratchRepo = repo().withWorkTree(scratchTree);
    scratchRepo.simpleCommand("submodule", "add", "file://" + submodule.getWorkTree(), "submodule");
    scratchRepo.simpleCommand("commit", "-m", "commit submodule");

    Files.write(workdir.resolve("test42"), new byte[] {42});
    Destination.Writer writer = destination().newWriter(destinationFiles, /*dryRun=*/false);
    WriterResult result = writer.write(
        TransformResults.of(workdir, new DummyRevision("ref1")),
        console);
    assertThat(result).isEqualTo(WriterResult.OK);
  }

  @Test
  public void submoduleInDestination_negativeDestinationFilesGlob() throws Exception {
    destinationFiles =
        Glob.createGlob(ImmutableList.of("**"), ImmutableList.of(".gitmodules", "submodule"));
    writeWithSubmoduleInDestination();
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFiles(".gitmodules", "submodule", "test42");
  }

  @Test
  public void submoduleInDestination_positiveDestinationFilesGlob() throws Exception {
    destinationFiles = Glob.createGlob(ImmutableList.of("test42"));
    writeWithSubmoduleInDestination();
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFiles(".gitmodules", "submodule", "test42");
  }

  @Test
  public void submoduleInDestination_deletesIfMatchesDestinationFilesGlob() throws Exception {
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));
    writeWithSubmoduleInDestination();
    GitTesting.assertThatCheckout(repo(), "master")
        .containsFiles("test42")
        .containsNoFiles("submodule");
  }

  @Test
  public void submoduleInSubdirectoryWithSiblingFiles() throws Exception {
    destinationFiles = Glob.createGlob(ImmutableList.of("foo/a", "foo/c"));
    fetch = "master";
    push = "master";

    Path scratchTree = Files.createTempDirectory("SubmodulesInDestinationTest-scratchTree");
    GitRepository scratchRepo = repo().withWorkTree(scratchTree);

    Files.createDirectories(scratchTree.resolve("foo"));
    Files.write(scratchTree.resolve("foo/a"), new byte[] {1});
    scratchRepo.add().files("foo/a").run();
    scratchRepo.simpleCommand("submodule", "add", "file://" + submodule.getWorkTree(), "foo/b");
    scratchRepo.simpleCommand("commit", "-m", "commit submodule and foo/a");

    // Create a commit that removes foo/a and adds foo/c
    Files.createDirectories(workdir.resolve("foo"));
    Files.write(workdir.resolve("foo/c"), new byte[] {1});

    Destination.Writer writer =
        destination().newWriter(destinationFiles, /*dryRun=*/false);
    WriterResult result = writer.write(
        TransformResults.of(workdir, new DummyRevision("ref1")),
        console);
    assertThat(result).isEqualTo(WriterResult.OK);

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFiles("foo/c", "foo/b")
        .containsNoFiles("foo/a");
  }
}
