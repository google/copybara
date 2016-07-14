// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * A console for testing purposes that does not perform any I/O operations.
 *
 * <p>Allows programming the user input, and asserting on the output messages.
 */
public final class TestingConsole implements Console {
  private static final class Message {
    private final MessageType type;
    private final String text;

    @Override
    public String toString() {
      return type + ": " + text;
    }

    Message(MessageType type, String text) {
      this.type = type;
      this.text = text;

    }
  }

  private enum PromptResponse {
    YES, NO,
  }

  public enum MessageType {
    ERROR, WARNING, INFO, PROGRESS;
  }

  private final ArrayDeque<PromptResponse> programmedResponses = new ArrayDeque<>();
  private final ArrayDeque<Message> messages = new ArrayDeque<>();

  public TestingConsole respondYes() {
    this.programmedResponses.addLast(PromptResponse.YES);
    return this;
  }

  public TestingConsole respondNo() {
    this.programmedResponses.addLast(PromptResponse.NO);
    return this;
  }

  /**
   * Asserts the next message matches some regex and is of the given type.
   *
   * @param type the type of the message, or {@code null} to allow any type
   * @param regex a regex which must fully match the next message.
   */
  public TestingConsole assertNextMatches(@Nullable MessageType type, String regex) {
    return assertRegex(type, regex);
  }

  /**
   * Asserts the next message is equals to a string literal.
   *
   * <p>This is a convenience method to avoid having to escape special characters in the regex.
   */
  public TestingConsole assertNextEquals(@Nullable MessageType type, String text) {
    return assertRegex(type, Pattern.quote(text));
  }

  private TestingConsole assertRegex(@Nullable MessageType type, String regex) {
    assertWithMessage("no more console messages")
        .that(messages)
        .isNotEmpty();
    Message next = messages.removeFirst();
    assertThat(next.text)
        .matches(regex);
    if (type != null) {
      assertWithMessage("type of message with text: " + next.text)
          .that(next.type)
          .isEqualTo(type);
    }
    return this;
  }

  public int countTimesInLog(MessageType type, String regex) {
    int count = 0;
    for (Message message : messages) {
      if (message.type.equals(type) && message.text.matches(regex)) {
        count++;
      }
    }
    return count;
  }

  public TestingConsole assertNoMore() {
    assertThat(messages).isEmpty();
    return this;
  }

  @Override
  public void startupMessage() {}

  @Override
  public void error(String message) {
    messages.addLast(new Message(MessageType.ERROR, message));
  }

  @Override
  public void warn(String message) {
    messages.addLast(new Message(MessageType.WARNING, message));
  }

  @Override
  public void info(String message) {
    messages.addLast(new Message(MessageType.INFO, message));
  }

  @Override
  public void progress(String progress) {
    messages.addLast(new Message(MessageType.PROGRESS, progress));
  }

  @Override
  public boolean promptConfirmation(String message) {
    Preconditions.checkState(!programmedResponses.isEmpty(), "No more programmed responses.");
    warn(message);
    return programmedResponses.removeFirst() == PromptResponse.YES;
  }
}
