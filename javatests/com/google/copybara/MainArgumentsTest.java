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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.CommandLineException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.ExitCode;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MainArgumentsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private MainArguments mainArguments;
  private FileSystem fs;
  private OptionsBuilder options;

  @Before
  public void setup() {
    mainArguments = new MainArguments(new String[] {"one", "two"});
    fs = Jimfs.newFileSystem();
    options = new OptionsBuilder();
  }

  @Test
  public void getWorkdirNormalized() throws Exception {
    mainArguments.baseWorkdir = "/some/../path/..";
    Path baseWorkdir = mainArguments.getBaseWorkdir(options.general, fs);
    assertThat(baseWorkdir.toString()).isEqualTo("/");
  }

  @Test
  public void getWorkdirIsNotDirectory() throws Exception {
    Files.write(fs.getPath("file"), "hello".getBytes(UTF_8));

    mainArguments.baseWorkdir = "file";
    thrown.expect(IOException.class);
    thrown.expectMessage("'file' exists and is not a directory");
    mainArguments.getBaseWorkdir(options.general, fs);
  }

  @Test
  public void testEmptyArguments() throws Exception {
    mainArguments.unnamed = ImmutableList.of();
    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Expected at least a configuration file");
    parseUnnamedArgs();
  }

  @Test
  public void testWrongCommand() throws Exception {
    mainArguments.unnamed = ImmutableList.of("foooo");
    try {
      parseUnnamedArgs();
      fail();
    } catch (CommandLineException e) {
      assertThat(e).hasMessageThat()
          .containsMatch("Available commands: \\[info, migrate, validate\\]");
    }
  }

  @Test
  public void testTooManyArguments() throws Exception {
    mainArguments.unnamed = ImmutableList.of("1", "2", "3", "4", "5");
    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Expected at most four arguments");
    parseUnnamedArgs();
  }

  @Test
  public void testGetOriginalArgsForLogging() throws Exception {
    assertThat(mainArguments.getOriginalArgsForLogging()).containsExactly("one", "two");
  }


  /**
   * Subcommand 'migrate' allows all the parameters.
   */
  @Test
  public void testArgumentParsingMigrate() throws Exception {
    checkParsing(
        ImmutableList.of("copy.bara.sky"),
        "migrate",
        "copy.bara.sky",
        "default",
        /* expectedSourceRef= */ null);
    checkParsing(
        ImmutableList.of("migrate", "copy.bara.sky"),
        "migrate",
        "copy.bara.sky",
        "default",
        /* expectedSourceRef= */ null);
    checkParsing(
        ImmutableList.of("copy.bara.sky", "import_wf"),
        "migrate",
        "copy.bara.sky",
        "import_wf",
        /* expectedSourceRef= */ null);
    checkParsing(
        ImmutableList.of("copy.bara.sky", "import_wf", "some_ref"),
        "migrate",
        "copy.bara.sky",
        "import_wf",
        "some_ref");
    checkParsing(
        ImmutableList.of("migrate", "copy.bara.sky", "import_wf", "some_ref"),
        "migrate",
        "copy.bara.sky",
        "import_wf",
        "some_ref");
  }

  /**
   * Subcommand 'validate' only allows one parameter (no workflow name or sourceRef).
   */
  @Test
  public void testArgumentParsingValidate() throws Exception {
    checkParsing(
        ImmutableList.of("validate", "copy.bara.sky"),
        "validate",
        "copy.bara.sky",
        "default",
        /* expectedSourceRef= */ null);

    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Too many arguments for subcommand 'validate'");
    checkParsing(ImmutableList.of("validate", "copy.bara.sky", "import_wf", "some_ref"));
  }

  /**
   * Subcommand 'info' does not allow sourceRef.
   */
  @Test
  public void testArgumentParsingInfo() throws Exception {
    checkParsing(
        ImmutableList.of("info", "copy.bara.sky"),
        "info",
        "copy.bara.sky",
        "default",
        /* expectedSourceRef= */ null);
    checkParsing(
        ImmutableList.of("info", "copy.bara.sky", "import_wf"),
        "info",
        "copy.bara.sky",
        "import_wf",
        /* expectedSourceRef= */ null);

    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Too many arguments for subcommand 'info'");
    checkParsing(ImmutableList.of("info", "copy.bara.sky", "import_wf", "some_ref"));
  }

  private void checkParsing(
      List<String> args,
      String expectedSubcommand,
      String expectedConfigPath,
      String expectedWorkflowName,
      @Nullable String expectedSourceRef)
      throws CommandLineException {
    checkParsing(args);
    assertThat(mainArguments.getSubcommand().name()).isEqualTo(expectedSubcommand);
    assertThat(mainArguments.getConfigPath()).isEqualTo(expectedConfigPath);
    assertThat(mainArguments.getWorkflowName()).isEqualTo(expectedWorkflowName);
    assertThat(mainArguments.getSourceRef()).isEqualTo(expectedSourceRef);
  }

  private void checkParsing(List<String> args) throws CommandLineException {
    mainArguments = new MainArguments(new String[] {"one", "two"});
    mainArguments.unnamed = args;
    parseUnnamedArgs();
  }

  private void parseUnnamedArgs() throws CommandLineException {
    mainArguments.parseUnnamedArgs(
        ImmutableMap.of(
            "migrate", new MockCommand("migrate"),
            "validate", new MockCommand("validate"),
            "info", new MockCommand("info")),
        new MockCommand("migrate"));
  }

  private static class MockCommand implements CopybaraCmd {
    private String name;

    public MockCommand(String name) {
      this.name = Preconditions.checkNotNull(name);
    }

    @Override
    public ExitCode run(
        MainArguments mainArgs, Options options, ConfigLoader<?> configLoader, Copybara copybara)
        throws ValidationException, IOException, RepoException {
      throw new IllegalStateException();
    }

    @Override
    public String name() {
      return name;
    }
  }
}
