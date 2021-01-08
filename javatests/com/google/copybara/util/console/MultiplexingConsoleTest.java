/*
 * Copyright (C) 2021 Google Inc.
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

import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultiplexingConsoleTest {

  @Test
  public void testConsole() {
    TestingConsole delegate1 = new TestingConsole();
    TestingConsole delegate2 = new TestingConsole();

    MultiplexingConsole underTest = new MultiplexingConsole(delegate1, delegate2);

    underTest.startupMessage("v1");
    underTest.info("This is info");
    underTest.warn("This is warning");
    underTest.error("This is error");
    underTest.verbose("This is verbose");
    underTest.progress("This is progress");


    delegate1
        .assertThat()
        .matchesNext(MessageType.INFO, "Copybara source mover [(]Version: v1[)]")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.ERROR, "This is error")
        .matchesNext(MessageType.VERBOSE, "This is verbose")
        .matchesNext(MessageType.PROGRESS, "This is progress");
    delegate2
        .assertThat()
        .matchesNext(MessageType.INFO, "Copybara source mover [(]Version: v1[)]")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.ERROR, "This is error")
        .matchesNext(MessageType.VERBOSE, "This is verbose")
        .matchesNext(MessageType.PROGRESS, "This is progress");
  }
}


