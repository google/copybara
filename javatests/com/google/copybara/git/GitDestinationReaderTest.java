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


package com.google.copybara.git;

import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Workflow;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationReaderTest {


  private SkylarkTestExecutor skylark;

  private OptionsBuilder options;
  private TestingConsole console;
  private Path destinationPath;
  private Path gitDir;
  private Path workDir;

  private DummyOrigin origin;
  private GitRepository repo;
  private String primaryBranch;

  @Before
  public void setup() throws Exception {
    destinationPath = Files.createTempDirectory("destination");
    gitDir = Files.createTempDirectory("gitDir");

    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);
    options.setHomeDir(Files.createTempDirectory("home").toString());
    origin = new DummyOrigin();
    options.testingOptions.origin = origin;
    repo = GitRepository.newBareRepo(destinationPath, getGitEnv(),
        /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false).withWorkTree(gitDir).init();
    primaryBranch = repo.getPrimaryBranch();
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    workDir = Files.createTempDirectory("workdir");
    options.setWorkdirToRealTempDir(workDir.toString());
    skylark = new SkylarkTestExecutor(options);
    origin.singleFileChange(1580341755, "first", "foo/origin.txt", "AAAA")
        .singleFileChange(1580341795, "second", "foo/origin.txt", "BBBB");
  }

  @Test
  public void testReadFileFromDestination() throws Exception {
    Files.createDirectories(gitDir.resolve("foo"));
    Files.write(gitDir.resolve("foo/foo.txt"), "foo".getBytes(UTF_8));
    repo.add().files("foo/foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repo.commit("= Foo = <bar@bara.com>", date,
        String.format("adding foo  \n\n%s: %s", DummyOrigin.LABEL_NAME, "0"));
    runWorkflow(ImmutableList.of(
        "res = ctx.destination_reader().read_file(path = 'foo/foo.txt')",
        "if res != 'foo':",
        "  fail('expected foo, got ' + res)"));
  }

  @Test
  public void testGlobFileFromDestination() throws Exception {
    Files.createDirectories(gitDir.resolve("foo"));
    Files.write(gitDir.resolve("foo/destination.txt"), "foo".getBytes(UTF_8));
    repo.add().files("foo/destination.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repo.commit("= Foo = <bar@bara.com>", date,
        String.format("adding foo  \n\n%s: %s", DummyOrigin.LABEL_NAME, "0"));
    FileSubjects.assertThatPath(workDir).containsNoMoreFiles();
    runWorkflow(ImmutableList.of(
        "ctx.destination_reader().copy_destination_files(glob = glob(include = ['foo/**']))"));
    FileSubjects.assertThatPath(workDir).containsFile("checkout/foo/destination.txt", "foo");
  }

  @Test
  public void testGlobFileFromDestination_otherRoot() throws Exception {
    Files.createDirectories(gitDir.resolve("foo"));
    Files.write(gitDir.resolve("foo/destination.txt"), "foo".getBytes(UTF_8));
    repo.add().files("foo/destination.txt").run();
    Files.createDirectories(gitDir.resolve("bar"));
    Files.write(gitDir.resolve("bar/destination.txt"), "foo".getBytes(UTF_8));
    repo.add().files("bar/destination.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repo.commit("= Foo = <bar@bara.com>", date,
        String.format("adding foo  \n\n%s: %s", DummyOrigin.LABEL_NAME, "0"));
    FileSubjects.assertThatPath(workDir).containsNoMoreFiles();
    runWorkflow(ImmutableList.of(
        "ctx.destination_reader().copy_destination_files(glob = glob(include = ['bar/**']))",
        "ctx.run(core.transform([core.remove(glob(include = ['bar/**']))], reversal=[]))"));
  }

  @Test
  public void testFileExists() throws Exception {
    Files.createDirectories(gitDir.resolve("foo"));
    Files.write(gitDir.resolve("foo/foo.txt"), "foo".getBytes(UTF_8));
    repo.add().files("foo/foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repo.commit("= Foo = <bar@bara.com>", date,
        String.format("adding foo  \n\n%s: %s", DummyOrigin.LABEL_NAME, "0"));
    runWorkflow(ImmutableList.of(
        "res = ctx.destination_reader().file_exists(path = 'foo/foo.txt')",
        "if not res:",
        "  fail('expected foo.txt to exist')"));
  }

  @Test
  public void testFileNotExists() throws Exception {
    Files.createDirectories(gitDir.resolve("foo"));
    Files.write(gitDir.resolve("foo/foo.txt"), "foo".getBytes(UTF_8));
    repo.add().files("foo/foo.txt").run();
    ZonedDateTime date = ZonedDateTime.now(ZoneId.of("-07:00"))
        .truncatedTo(ChronoUnit.SECONDS);
    repo.commit("= Foo = <bar@bara.com>", date,
        String.format("adding foo  \n\n%s: %s", DummyOrigin.LABEL_NAME, "0"));
    runWorkflow(ImmutableList.of(
        "res = ctx.destination_reader().file_exists(path = 'foo/bar.txt')",
        "if res:",
        "  fail('expected bar.txt not to exist')"));
  }

  private void runWorkflow(ImmutableList<String> funBody) throws Exception {
    Workflow<?, ?> test = workflow("def _dynamicTransform(ctx):\n"
        + funBody.stream().map(s -> "  " + s).collect(Collectors.joining("\n"))
        + "\n");
    test.run(workDir, ImmutableList.of("1"));
  }

  private Workflow<?, ?> workflow(String dynamicTransform) throws IOException, ValidationException {
    String config =
        dynamicTransform
            + "\n"
            + "core.workflow(\n"
            + "    name = 'default',\n"
            + "    origin = testing.origin(),\n"
            + "    destination = git.destination(\n"
            + "      fetch = '" + primaryBranch + "', \n"
            + "      push = '" + primaryBranch + "', \n"
            + "      url = 'file://" + destinationPath + "', \n"
            + "    ),\n"
            + "    origin_files = glob(['foo/**']),\n"
            + "    destination_files = glob(['foo/**']),\n"
            + "    transformations = [core.dynamic_transform(_dynamicTransform)],\n"
            + "    authoring = authoring.overwrite('test <commiter@email.com>'),\n"

        + ")\n"
            + "\n";
    return (Workflow) skylark.loadConfig(config).getMigration("default");
  }


}
