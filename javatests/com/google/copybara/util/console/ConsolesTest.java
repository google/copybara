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

package com.google.copybara.util.console;

import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConsolesTest {

  private TestingConsole console;

  @Before
  public void setup() {
    this.console = new TestingConsole();
  }

  @Test
  public void logLines_empty() {
    Consoles.logLines(console, "prefix", /*text*/ "");
    console.assertThat()
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneLine() {
    Consoles.logLines(console, "fooprefix-", ""
        + "hello\n"
        + "goodbye\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-hello")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-goodbye")
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneEmptyLine() {
    Consoles.logLines(console, "fooprefix-", "\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-")
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneEmptyLineSurroundedByNonEmpty() {
    Consoles.logLines(console, "fooprefix-", ""
        + "x\n"
        + "\n"
        + "y\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-x")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-y")
        .containsNoMoreMessages();
  }
}
