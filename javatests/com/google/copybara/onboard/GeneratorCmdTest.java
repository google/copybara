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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.CommandEnv;
import com.google.copybara.Workflow;
import com.google.copybara.config.Config;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.onboard.core.AskInputProvider.Mode;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratorCmdTest {

  private TestingConsole console;
  private Path workdir;
  private OptionsBuilder optionsBuilder;
  private Path destination;
  private SkylarkTestExecutor skylark;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    workdir = Files.createTempDirectory("temp");
    destination = Files.createTempDirectory("destination");
    optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console).setOutputRootToTmpDir();
    optionsBuilder.setForce(true);
    File buildifier = Paths.get(System.getenv("TEST_SRCDIR"))
        .resolve(System.getenv("TEST_WORKSPACE"))
        .resolve("javatests/com/google/copybara/onboard")
        .resolve("buildifier")
        .toFile();
    optionsBuilder.buildifier.buildifierBin = buildifier.getAbsolutePath();

    Path userHomeForTest = Files.createTempDirectory("home");
    optionsBuilder.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    optionsBuilder.setHomeDir(userHomeForTest.toString());
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testSimple() throws Exception {
    // TODO(malcon): Refactor TestingConsole.respondWithString to have an optional predicate so
    // that we can match the question with the answer and not rely in the order of resolve
    // calls.
    console.respondWithString(destination.toAbsolutePath().toString());
    console.respondWithString("git_to_git");
    console.respondWithString("https://example.com/origin");
    console.respondWithString("https://example.com/destination");
    console.respondWithString("foo <foo@example.com>");
    console.respondWithString("my_name");

    checkRun();
  }

  @Test
  public void testWithFlags() throws Exception {
    optionsBuilder.generator.askMode = Mode.FAIL;

    console.respondWithString(destination.toAbsolutePath().toString());
    optionsBuilder.generator.template = "git_to_git";
    // Keep keys as string to force the inputs not been registered in Input.registeredInputs()
    optionsBuilder.generator.inputs = ImmutableMap.of(
        "git_origin_url", "https://example.com/origin",
        "git_destination_url", "https://example.com/destination",
        "default_author", "foo <foo@example.com>",
        "migration_name", "my_name",
        "generator_folder", destination.toAbsolutePath().toString()
    );

    checkRun();
  }

  private void checkRun() throws ValidationException, IOException, RepoException {
    ExitCode exitCode = runCommand();
    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    String config = Files.readString(destination.resolve("copy.bara.sky"));

    Config asObject = skylark.loadConfig(config);
    Workflow<?, ?> migration = (Workflow<?, ?>) asObject.getMigration("my_name");
    assertThat(migration).isNotNull();
    assertThat(migration.getOrigin().describe(Glob.ALL_FILES))
        .containsAtLeast("url", "https://example.com/origin");
    assertThat(migration.getDestination().describe(Glob.ALL_FILES))
        .containsAtLeast("url", "https://example.com/destination");
    assertThat(migration.getAuthoring().getDefaultAuthor().toString())
        .isEqualTo("foo <foo@example.com>");
  }

  private ExitCode runCommand(String... params)
      throws ValidationException, IOException, RepoException {
    GeneratorCmd generatorCmd = new GeneratorCmd(skylark.createModuleSet());
    return generatorCmd.run(new CommandEnv(workdir,
        optionsBuilder.build(),
        ImmutableList.copyOf(params)));
  }
}
