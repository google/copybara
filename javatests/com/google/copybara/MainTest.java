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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test to verify that framework methods are called as expected.
 */
@RunWith(JUnit4.class)
public class MainTest {

  private SkylarkTestExecutor skylark;
  private final String[] args = {"copy.bara.sky"};
  private boolean called = false;

  private OptionsBuilder options;

  @Before
  public void setUp() throws Exception {
    called = false;
    Path userHomeDir = Files.createTempDirectory("MainTest");
    System.setProperty("user.home", userHomeDir.toString());
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
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
          protected void configureLog(FileSystem fs, String[] args) {
            called = true;
          }
        };
    assertThat(main.run(args)).isEqualTo(ExitCode.COMMAND_LINE_ERROR);
    assertThat(called).isTrue();
  }

  @Test
  public void testTemporaryFeatures() throws IOException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());

    Main main = new Main(envWithHome) {
      @Override
      protected ModuleSet newModuleSet(ImmutableMap<String, String> environment, FileSystem fs,
          Console console) {
        return skylark.createModuleSet();
      }
    };

    main.run(new String[]{"--temporary-features", "foo:true,bar:false"});
    assertThat(options.general.isTemporaryFeature("foo", true)).isTrue();
    assertThat(options.general.isTemporaryFeature("foo", false)).isTrue();
    assertThat(options.general.isTemporaryFeature("bar", true)).isFalse();
    assertThat(options.general.isTemporaryFeature("bar", false)).isFalse();
    assertThat(options.general.isTemporaryFeature("baz", true)).isTrue();
    assertThat(options.general.isTemporaryFeature("baz", false)).isFalse();
  }

  @Test
  public void testRelativePath() throws IOException, ValidationException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    Main main = new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {
            called = true;
          }

      @Override
      protected ModuleSet newModuleSet(ImmutableMap<String, String> environment, FileSystem fs,
          Console console) {
        return skylark.createModuleSet();
      }
    };

    Files.write(
        options.general.getFileSystem().getPath("/work").resolve("copy.bara.sky"),
        "".getBytes(UTF_8));
    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());

    ConfigLoader loader = clp.newLoader("./copy.bara.sky", "test");
    assertThat(loader.location()).isEqualTo("/work/copy.bara.sky");
  }

  @Test
  public void testInitEnvironmentCalled() throws IOException {
    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());

    Main main =
        new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {}

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
          protected void configureLog(FileSystem fs, String[] args) {}

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
