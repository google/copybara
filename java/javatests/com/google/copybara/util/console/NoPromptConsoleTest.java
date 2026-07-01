/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.util.console;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
@RunWith(JUnit4.class)

public class NoPromptConsoleTest {
  @Test
  public void checkNoPrompt()
      throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Console console = new NoPromptConsole(
        new AnsiConsole(in, new PrintStream(out), /*verbose=*/ false),  true);
    boolean prompt = console.promptConfirmation("Do you want to proceed?");
    assertThat(out.toString(UTF_8.name())).contains("Prompt: Do you want to proceed?");
    assertThat(out.toString(UTF_8.name())).contains("Answering: yes");
    assertThat(prompt).isTrue();
  }
}
