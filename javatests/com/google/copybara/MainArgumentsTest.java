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

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;

import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class MainArgumentsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private MainArguments mainArguments;
  private FileSystem fs;

  @Before
  public void setup() {
    mainArguments = new MainArguments();
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void getWorkdirNormalized() throws Exception {
    mainArguments.baseWorkdir = "/some/../path/..";
    Path baseWorkdir = mainArguments.getBaseWorkdir(fs);
    assertThat(baseWorkdir.toString()).isEqualTo("/");
  }

  @Test
  public void getWorkdirIsNotDirectory() throws Exception {
    Files.write(fs.getPath("file"), "hello".getBytes());

    mainArguments.baseWorkdir = "file";
    thrown.expect(IOException.class);
    thrown.expectMessage("'file' exists and is not a directory");
    mainArguments.getBaseWorkdir(fs);
  }

  @Test
  public void testEmptyArguments() throws Exception {
    mainArguments.unnamed = ImmutableList.of();
    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Expected at least a configuration file");
    mainArguments.parseUnnamedArgs();
  }

  @Test
  public void testTooManyArguments() throws Exception {
    mainArguments.unnamed = ImmutableList.of("1", "2", "3", "4", "5");
    thrown.expect(CommandLineException.class);
    thrown.expectMessage("Expected at most four arguments");
    mainArguments.parseUnnamedArgs();
  }

  @Test
  public void testArgumentParsing() throws Exception {
    checkParsing(ImmutableList.of("copy.bara.sky"),
        Subcommand.MIGRATE, "copy.bara.sky", "default", /*sourceRef=*/ null);
    checkParsing(ImmutableList.of("migrate", "copy.bara.sky"),
        Subcommand.MIGRATE, "copy.bara.sky", "default", /*sourceRef=*/ null);
    checkParsing(ImmutableList.of("validate", "copy.bara.sky"),
        Subcommand.VALIDATE, "copy.bara.sky", "default", /*sourceRef=*/ null);
    checkParsing(ImmutableList.of("copy.bara.sky", "import_wf"),
        Subcommand.MIGRATE, "copy.bara.sky", "import_wf", /*sourceRef=*/ null);
    checkParsing(ImmutableList.of("copy.bara.sky", "import_wf", "some_ref"),
        Subcommand.MIGRATE, "copy.bara.sky", "import_wf", "some_ref");
    checkParsing(ImmutableList.of("migrate", "copy.bara.sky", "import_wf", "some_ref"),
        Subcommand.MIGRATE, "copy.bara.sky", "import_wf", "some_ref");
  }

  private void checkParsing(List<String> args, Subcommand expectedSubcommand, String expectedConfigPath,
      String expectedWorkflowName, @Nullable String expectedSourceRef) throws CommandLineException {
    mainArguments = new MainArguments();
    mainArguments.unnamed = args;
    mainArguments.parseUnnamedArgs();
    assertThat(mainArguments.getSubcommand()).isEqualTo(expectedSubcommand);
    assertThat(mainArguments.getConfigPath()).isEqualTo(expectedConfigPath);
    assertThat(mainArguments.getWorkflowName()).isEqualTo(expectedWorkflowName);
    assertThat(mainArguments.getSourceRef()).isEqualTo(expectedSourceRef);
  }
}
