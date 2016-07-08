package com.google.copybara.util.console;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class AnsiConsoleTest {

  @Test
  public void promptReturnsTrue() throws Exception {
    checkExpectedPrompt("y", true);
    checkExpectedPrompt("Y", true);
    checkExpectedPrompt("yes", true);
    checkExpectedPrompt("YES", true);
    checkExpectedPrompt(" yes ", true);
  }

  @Test
  public void promptReturnsFalse() throws Exception {
    checkExpectedPrompt("n", false);
    checkExpectedPrompt("N", false);
    checkExpectedPrompt("No", false);
    checkExpectedPrompt("NO", false);
    checkExpectedPrompt(" no ", false);
  }

  @Test
  public void promptRetriesOnInvalidAnswer() throws Exception {
    checkExpectedPrompt("bar\ny", true);
    checkExpectedPrompt("foo\nn", false);
  }

  private void checkExpectedPrompt(String inputText, boolean expectedPrompt) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    AnsiConsole console = new AnsiConsole(in, new PrintStream(out));
    boolean prompt = console.promptConfirmation("Do you want to proceed?");
    String[] output = out.toString(StandardCharsets.UTF_8.name()).split("\n");
    // AnsiConsole prints a message in the first line
    assertThat(output[1]).endsWith("Do you want to proceed? [y/n] ");
    if (expectedPrompt) {
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
