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

import com.beust.jcommander.JCommander;
import com.google.copybara.util.ExitCode;
import java.nio.file.FileSystem;
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
  boolean called = false;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    called = false;
    System.setProperty("user.home", "/tmp/foo/bar");
  }

  @Test
  public void testNoArguments() {
    assertThat(new Main().run(args)).isEqualTo(ExitCode.COMMAND_LINE_ERROR);
  }

  @Test
  public void testConfigureLogCalled() {

    Main main =
        new Main() {
          @Override
          protected void configureLog(FileSystem fs) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }

  @Test
  public void testInitEnvironmentCalled() {
    Main main =
        new Main() {
          @Override
          protected void configureLog(FileSystem fs) {}

          @Override
          protected void initEnvironment(Options o, MainArguments args, JCommander jcommander) {
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
          protected void shutdown(ExitCode exitCode) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }
}
