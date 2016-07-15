package com.google.copybara.util.console;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.copybara.testing.LogSubjects;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class ConsoleTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
  public void testEOFDetected() throws Exception {
    Console console = LogConsole.readWriteConsole(
        new ByteArrayInputStream(new byte[]{}),new PrintStream(new ByteArrayOutputStream()));

    thrown.expect(RuntimeException.class);
    console.promptConfirmation("Proceed?");
  }

  @Test
  public void logConsolePromtFailsOnMissingSystemConsole() throws Exception {
    Console console = LogConsole.writeOnlyConsole(new PrintStream(new ByteArrayOutputStream()));
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("LogConsole cannot read user input if system console is not present");
    console.promptConfirmation("Do you want to proceed?");
  }

  @Test
  public void progressPrefix() throws Exception {
    TestingConsole delegate = new TestingConsole();
    Console console = new ProgressPrefixConsole("FOO ", delegate);
    console.progress("bar");

    assertAbout(LogSubjects.console())
        .that(delegate)
        .matchesNext(MessageType.PROGRESS, "FOO bar")
        .containsNoMoreMessages();
  }

  private void checkAnsiConsoleExpectedPrompt(String inputText, boolean expectedResponse)
      throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Console console = new AnsiConsole(in, new PrintStream(out));

    checkExpectedPrompt(console, out, inputText, expectedResponse);
  }

  private void checkLogConsoleExpectedPrompt(String inputText, boolean expectedResponse)
      throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Console console = LogConsole.readWriteConsole(in, new PrintStream(out));

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
