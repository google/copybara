// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;

import java.util.ArrayDeque;
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * A console which allows asserting on the messages that were sent to it.
 */
public final class AssertingConsole implements Console {
  private static final class Message {
    private final MessageType type;
    private final String text;

    Message(MessageType type, String text) {
      this.type = type;
      this.text = text;
    }
  }

  public enum PromptResponse {
    YES, NO,
  }

  public enum MessageType {
    ERROR, WARNING, INFO, PROGRESS;
  }

  private final ArrayDeque<PromptResponse> programmedResponses = new ArrayDeque<>();
  private final ArrayDeque<Message> messages = new ArrayDeque<>();

  public AssertingConsole(PromptResponse... programmedResponses) {
    this.programmedResponses.addAll(Arrays.asList(programmedResponses));
  }

  /**
   * Asserts the next message matches some regex.
   */
  public AssertingConsole assertNextMatches(String regex) {
    return assertNextMatches(/*type*/ null, regex);
  }

  /**
   * Asserts the next message matches some regex and is of the given type.
   *
   * @param type the type of the message, or {@code null} to allow any type
   * @param regex a regex which must fully match the next message.
   */
  public AssertingConsole assertNextMatches(@Nullable MessageType type, String regex) {
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

  public AssertingConsole assertNoMore() {
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
