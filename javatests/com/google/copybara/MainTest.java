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
import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.ExitCode;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test to verify that framework methods are called as expected.
 */
@RunWith(JUnit4.class)
public class MainTest {

  private String[] args = {"copy.bara.sky"};
  private boolean called = false;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    called = false;
    Path userHomeDir = Files.createTempDirectory("MainTest");
    System.setProperty("user.home", userHomeDir.toString());
  }

  @Test
  public void testNoArguments() throws IOException {
    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    assertThat(new Main(envWithHome).run(/*no arguments*/ new String[]{}))
        .isEqualTo(ExitCode.COMMAND_LINE_ERROR);
  }

  @Test
  public void testConfigureLogCalled() throws IOException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    Main main = new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs) {
            called = true;
          }
        };
    assertThat(main.run(args)).isEqualTo(ExitCode.COMMAND_LINE_ERROR);
    assertThat(called).isTrue();
  }

  @Test
  public void testInitEnvironmentCalled() throws IOException {
    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());

    Main main =
        new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs) {}

          @Override
          protected void initEnvironment(Options options, CopybaraCmd copybaraCmd,
              ImmutableList<String> rawArgs) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }

  @Test
  public void testShutdownCalled() {
    Main main =
        new Main() {
          @Override
          protected void configureLog(FileSystem fs) {}

          @Override
          protected void shutdown(CommandResult result) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }

  @Test
  public void testInvalidForcedAuthor() throws IOException {
    ImmutableMap<String, String> envWithHome =
        ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString());
    assertThat(new Main(envWithHome).run(/*no arguments*/ new String[] {"--force-author='fdsfds'"}))
        .isEqualTo(ExitCode.COMMAND_LINE_ERROR);
  }
}
