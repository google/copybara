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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConsoleTest {
  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void ansiConsolePromptReturnsTrue() throws Exception {
    checkAnsiConsoleExpectedPrompt("y", true);
    checkAnsiConsoleExpectedPrompt("Y", true);
    checkAnsiConsoleExpectedPrompt("yes", true);
    checkAnsiConsoleExpectedPrompt("YES", true);
    checkAnsiConsoleExpectedPrompt(" yes ", true);
  }

  @Test
  public void ansiConsolePromptReturnsFalse() throws Exception {
    checkAnsiConsoleExpectedPrompt("n", false);
    checkAnsiConsoleExpectedPrompt("N", false);
    checkAnsiConsoleExpectedPrompt("No", false);
    checkAnsiConsoleExpectedPrompt("NO", false);
    checkAnsiConsoleExpectedPrompt(" no ", false);
  }

  @Test
  public void ansiConsolePromptRetriesOnInvalidAnswer() throws Exception {
    checkAnsiConsoleExpectedPrompt("bar\ny", true);
    checkAnsiConsoleExpectedPrompt("foo\nn", false);
  }

  @Test
  public void logConsolePromptReturnsTrue() throws Exception {
    checkLogConsoleExpectedPrompt("y", true);
    checkLogConsoleExpectedPrompt("Y", true);
    checkLogConsoleExpectedPrompt("yes", true);
    checkLogConsoleExpectedPrompt("YES", true);
    checkLogConsoleExpectedPrompt(" yes ", true);
  }

  @Test
  public void logConsolePromptReturnsFalse() throws Exception {
    checkLogConsoleExpectedPrompt("n", false);
    checkLogConsoleExpectedPrompt("N", false);
    checkLogConsoleExpectedPrompt("No", false);
    checkLogConsoleExpectedPrompt("NO", false);
    checkLogConsoleExpectedPrompt(" no ", false);
  }

  @Test
  public void logConsolePromptRetriesOnInvalidAnswer() throws Exception {
    checkLogConsoleExpectedPrompt("bar\ny", true);
    checkLogConsoleExpectedPrompt("foo\nn", false);
  }

  @Test
  public void testEOFReturnsFalse() throws Exception {
    Console console = LogConsole.readWriteConsole(
        new ByteArrayInputStream(
            new byte[]{}),new PrintStream(new ByteArrayOutputStream()), /*verbose=*/ false);

    assertThat(console.promptConfirmation("Proceed?")).isFalse();
  }

  @Test
  public void logConsolePromtFailsOnMissingSystemConsole() throws Exception {
    Console console = LogConsole.writeOnlyConsole(
        new PrintStream(new ByteArrayOutputStream()), /*verbose=*/ false);
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("LogConsole cannot read user input if system console is not present");
    console.promptConfirmation("Do you want to proceed?");
  }

  @Test
  public void progressPrefix() throws Exception {
    TestingConsole delegate = new TestingConsole();
    Console console = new ProgressPrefixConsole("FOO ", delegate);
    console.progress("bar");

    delegate.assertThat()
        .matchesNext(MessageType.PROGRESS, "FOO bar")
        .containsNoMoreMessages();
  }

  @Test
  public void fmtMethodsWork() throws Exception {
    TestingConsole delegate = new TestingConsole()
        .respondYes();
    CapturingConsole console = CapturingConsole.captureAllConsole(delegate);

    console.errorFmt("This is %s", "error!");
    console.warnFmt("This is %s", "warning");
    console.infoFmt("This is %s", "info");
    console.progressFmt("This is %s", "progress");
    console.promptConfirmationFmt("Do you want to %s?", "continue");

    assertThat(console.getMessages()).containsExactly(
        new Message(MessageType.ERROR, "This is error!"),
        new Message(MessageType.WARNING, "This is warning"),
        new Message(MessageType.INFO, "This is info"),
        new Message(MessageType.PROGRESS, "This is progress"),
        new Message(MessageType.PROMPT, "Do you want to continue?"));
  }

  @Test
  public void testVerbose() throws Exception {
    TestingConsole verboseDelegate = new TestingConsole();
    CapturingConsole verboseConsole= CapturingConsole.captureAllConsole(verboseDelegate);

    verboseConsole.verboseFmt("This is %s", "verbose!");
    verboseConsole.verboseFmt("This is also verbose");
    verboseConsole.info("This is info");

    assertThat(verboseConsole.getMessages()).containsExactly(
        new Message(MessageType.VERBOSE, "This is verbose!"),
        new Message(MessageType.VERBOSE, "This is also verbose"),
        new Message(MessageType.INFO, "This is info"));
  }

  @Test
  public void captureAllConsole() throws Exception {
    TestingConsole delegate = new TestingConsole()
        .respondYes();
    CapturingConsole console = CapturingConsole.captureAllConsole(delegate);

    console.error("This is error!");
    console.warn("This is warning");
    console.info("This is info");
    console.progress("This is progress");
    console.promptConfirmation("Do you want to continue?");

    assertThat(console.getMessages()).containsExactly(
        new Message(MessageType.ERROR, "This is error!"),
        new Message(MessageType.WARNING, "This is warning"),
        new Message(MessageType.INFO, "This is info"),
        new Message(MessageType.PROGRESS, "This is progress"),
        new Message(MessageType.PROMPT, "Do you want to continue?"));

    delegate.assertThat()
        .matchesNext(MessageType.ERROR, "This is error!")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.PROGRESS, "This is progress")
        // TestingConsole registers prompt as a warning
        .matchesNext(MessageType.WARNING, "Do you want to continue[?]")
        .containsNoMoreMessages();
  }

  @Test
  public void captureOnlyConsole() throws Exception {
    TestingConsole delegate = new TestingConsole()
        .respondYes();
    CapturingConsole console =
        CapturingConsole.captureOnlyConsole(delegate, MessageType.ERROR, MessageType.WARNING);

    console.error("This is error!");
    console.warn("This is warning");
    console.info("This is info");
    console.progress("This is progress");
    console.promptConfirmation("Do you want to continue?");

    assertThat(console.getMessages()).containsExactly(
        new Message(MessageType.ERROR, "This is error!"),
        new Message(MessageType.WARNING, "This is warning"));

    delegate.assertThat()
        .matchesNext(MessageType.ERROR, "This is error!")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.PROGRESS, "This is progress")
        // TestingConsole registers prompt as a warning
        .matchesNext(MessageType.WARNING, "Do you want to continue[?]")
        .containsNoMoreMessages();
  }

  private void checkAnsiConsoleExpectedPrompt(String inputText, boolean expectedResponse)
      throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Console console = new AnsiConsole(in, new PrintStream(out), /*verbose=*/ false);

    checkExpectedPrompt(console, out, inputText, expectedResponse);
  }

  private void checkLogConsoleExpectedPrompt(String inputText, boolean expectedResponse)
      throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Console console = LogConsole.readWriteConsole(in, new PrintStream(out), /*verbose=*/ false);

    checkExpectedPrompt(console, out, inputText, expectedResponse);
  }

  private void checkExpectedPrompt(
      Console console, ByteArrayOutputStream out, String inputText, boolean expectedResponse)
      throws IOException {
    boolean prompt = console.promptConfirmation("Do you want to proceed?");
    assertThat(out.toString(StandardCharsets.UTF_8.name()))
        .endsWith("Do you want to proceed? [y/n] ");
    if (expectedResponse) {
      assertWithMessage("Input text '" + inputText + "' should return true.")
          .that(prompt)
          .isTrue();
    } else {
      assertWithMessage("Input text '" + inputText + "' should return false.")
          .that(prompt)
          .isFalse();
    }
  }
}
