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

import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that we can execute commands with Bazel shell library.
 */
@RunWith(JUnit4.class)
public class CommandRunnerTest {

  @Test
  public void testCommand() throws CommandException {
    Command command = new Command(new String[]{"echo", "hello", "world"});
    CommandOutputWithStatus result = new CommandRunner(command).execute();
    assertThat(result.getTerminationStatus().success()).isTrue();
    assertThat(result.getStdout()).isEqualTo("hello world\n");
  }
}
