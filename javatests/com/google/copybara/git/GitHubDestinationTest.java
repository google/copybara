/*
 * Copyright (C) 2018 Google Inc.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
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
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

//TODO(HUANHUANCHEN): Move those private functions to a util class if we need more functions from
// gitDestinationTest in next cl.
@RunWith(JUnit4.class)
public class GitHubDestinationTest {

  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private String url;
  private String fetch;
  private String push;
  private boolean force;

  private Path repoGitDir;
  private Glob destinationFiles;
  private Path workdir;
  private boolean skipPush;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    options =
        new OptionsBuilder()
            .setConsole(console)
            .setOutputRootToTmpDir();
    skylark = new SkylarkTestExecutor(options);
    repoGitDir = Files.createTempDirectory("GitHubDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");

    git("init", "--bare", repoGitDir.toString());
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));

    url = "file://" + repoGitDir;
    skylark = new SkylarkTestExecutor(options);
    force = false;
    skipPush = false;
  }

  @Test
  public void testDryRun() throws Exception {
    fetch = "master";
    push = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    Path scratchTree = Files.createTempDirectory("GitHubDestinationTest-testLocalRepo");
    Files.write(scratchTree.resolve("foo"), "foo\n".getBytes(UTF_8));
    repo().withWorkTree(scratchTree).add().force().files("foo").run();
    repo().withWorkTree(scratchTree).simpleCommand("commit", "-a", "-m", "change");
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ true,
            new DummyRevision("origin_ref1"));
    Writer<GitRevision> writer = destination().newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("foo", "foo\n")
        .containsNoMoreFiles();

    // Run again without dry run
    writer = newWriter();
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(repo(), "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testHttpUrl() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.github_destination("
        + "    url = 'http://github.com/foo', \n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("url")).contains("https://github.com/foo");
  }

  private void process(Writer<GitRevision> writer, DummyRevision ref)
      throws ValidationException, RepoException, IOException {
    process(writer, destinationFiles, ref);
  }

  private Writer<GitRevision> newWriter() throws ValidationException {
    return destination().newWriter(
        new WriterContext(
            "piper_to_github",
            /*workflowIdentityUser=*/ "TEST",
            false,
            new DummyRevision("test")));
  }

  private GitDestination evalDestination()
      throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.github_destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    skip_push = %s,\n"
            + ")", url, fetch, push, skipPush ? "True" : "False"));
  }

  private GitDestination destination() throws ValidationException {
    options.setForce(force);
    return evalDestination();
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(path, getEnv(), /*verbose=*/true);
  }

  private Map<String, String> getEnv() {
    Map<String, String> env = Maps.newHashMap(options.general.getEnvironment());
    env.putAll(getGitEnv());
    return env;
  }

  private void process(Writer<GitRevision> writer, Glob destinationFiles, DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    processWithBaseline(writer, destinationFiles, originRef, /*baseline=*/ null);
  }

  private void processWithBaseline(Writer<GitRevision> writer, Glob destinationFiles,
      DummyRevision originRef, String baseline)
      throws RepoException, ValidationException, IOException {
    processWithBaselineAndConfirmation(writer, destinationFiles, originRef, baseline,
        /*askForConfirmation*/false);
  }

  private void processWithBaselineAndConfirmation(Writer<GitRevision> writer,
      Glob destinationFiles, DummyRevision originRef, String baseline,
      boolean askForConfirmation)
      throws ValidationException, RepoException, IOException {
    TransformResult result = TransformResults.of(workdir, originRef);
    if (baseline != null) {
      result = result.withBaseline(baseline);
    }

    if (askForConfirmation) {
      result = result.withAskForConfirmation(true);
    }
    ImmutableList<DestinationEffect> destinationResult =
        writer.write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

}