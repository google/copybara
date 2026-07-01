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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.CommandLineException;
import com.google.copybara.testing.OptionsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandEnvTest {

  private Path workdir;
  private Options options;

  @Before
  public void setUp() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder().build();
  }

  @Test
  public void testGetters() {
    CommandEnv env = new CommandEnv(workdir, options, ImmutableList.of("foo"));

    assertThat(env.getWorkdir().toString()).isEqualTo(workdir.toString());
    assertThat(env.getArgs()).containsExactly("foo");
    assertThat(env.getOptions()).isEqualTo(options);
    assertThat(env.getConfigFileArgs()).isNull();
  }

  @Test
  public void testConfigParsingNoWorkflow() throws CommandLineException {
    CommandEnv env = new CommandEnv(workdir, options, ImmutableList.of("foo/copy.bara.sky"));
    env.parseConfigFileArgs(mock(CopybaraCmd.class), /*usesSourceRef=*/true);
    assertThat(env.getConfigFileArgs().getConfigPath()).isEqualTo("foo/copy.bara.sky");
    assertThat(env.getConfigFileArgs().getWorkflowName()).isEqualTo("default");
    assertThat(env.getConfigFileArgs().getSourceRefs()).isEmpty();
  }

  @Test
  public void testConfigParsingNoSourceRef() throws CommandLineException {
    CommandEnv env = new CommandEnv(workdir, options, ImmutableList.of("foo/copy.bara.sky", "wf"));
    env.parseConfigFileArgs(mock(CopybaraCmd.class), /*usesSourceRef=*/true);
    assertThat(env.getConfigFileArgs().getConfigPath()).isEqualTo("foo/copy.bara.sky");
    assertThat(env.getConfigFileArgs().getWorkflowName()).isEqualTo("wf");
    assertThat(env.getConfigFileArgs().getSourceRefs()).isEmpty();
  }

  @Test
  public void testConfigParsingNoSourceRefButPassed() {
    CommandEnv env = new CommandEnv(workdir, options,
        ImmutableList.of("foo/copy.bara.sky", "wf", "foo"));
    CommandLineException e =
        assertThrows(
            CommandLineException.class,
            () -> env.parseConfigFileArgs(mock(CopybaraCmd.class), /*usesSourceRef=*/ false));
    assertThat(e).hasMessageThat().contains("Too many arguments for subcommand");
  }

  @Test
  public void testConfigParsing() throws CommandLineException {
    CommandEnv env = new CommandEnv(workdir, options,
        ImmutableList.of("foo/copy.bara.sky", "wf", "123"));
    env.parseConfigFileArgs(mock(CopybaraCmd.class), /*usesSourceRef=*/true);
    assertThat(env.getConfigFileArgs().getConfigPath()).isEqualTo("foo/copy.bara.sky");
    assertThat(env.getConfigFileArgs().getWorkflowName()).isEqualTo("wf");
    assertThat(env.getConfigFileArgs().getSourceRefs()).containsExactly("123");
  }

  @Test
  public void testConfigParsingMultipleSourceRefs() throws CommandLineException {
    CommandEnv env = new CommandEnv(workdir, options,
        ImmutableList.of("foo/copy.bara.sky", "wf", "123", "456"));
    env.parseConfigFileArgs(mock(CopybaraCmd.class), /*usesSourceRef=*/true);
    assertThat(env.getConfigFileArgs().getConfigPath()).isEqualTo("foo/copy.bara.sky");
    assertThat(env.getConfigFileArgs().getWorkflowName()).isEqualTo("wf");
    assertThat(env.getConfigFileArgs().getSourceRefs()).containsExactly("123", "456");
  }
}
