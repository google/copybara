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

package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * A testing console that allows programming the user input and intercepts all the messages.
 *
 * <p>It also writes the output to a {@link LogConsole} for debug.
 */
public final class TestingConsole implements Console {

  public static final class Message {
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

    public MessageType getType() {
      return type;
    }

    public String getText() {
      return text;
    }
  }

  private enum PromptResponse {
    YES, NO,
  }

  public enum MessageType {
    ERROR, WARNING, INFO, PROGRESS;
  }

  private final Console outputConsole = LogConsole.writeOnlyConsole(System.out);
  private final ArrayDeque<PromptResponse> programmedResponses = new ArrayDeque<>();
  private final ArrayList<Message> messages = new ArrayList<>();

  public TestingConsole respondYes() {
    this.programmedResponses.addLast(PromptResponse.YES);
    return this;
  }

  public TestingConsole respondNo() {
    this.programmedResponses.addLast(PromptResponse.NO);
    return this;
  }

  /**
   * Returns the list of messages in the original order that they were logged.
   */
  public ImmutableList<Message> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  /**
   * Returns a truth subject that provides fluent methods for assertions on this instance.
   *
   * <p>For example:
   *
   *     testConsole.assertThat()
   *       .matchesNext(...)
   *       .equalsNext(...)
   *       .containsNoMoreMessages();
   */
  public LogSubjects.LogSubject assertThat() {
    return assertAbout(LogSubjects.CONSOLE_SUBJECT_FACTORY)
        .that(this);
  }

  @Override
  public void startupMessage() {
    outputConsole.startupMessage();
  }

  @Override
  public void error(String message) {
    messages.add(new Message(MessageType.ERROR, message));
    outputConsole.error(message);
  }

  @Override
  public void warn(String message) {
    messages.add(new Message(MessageType.WARNING, message));
    outputConsole.warn(message);
  }

  @Override
  public void info(String message) {
    messages.add(new Message(MessageType.INFO, message));
    outputConsole.warn(message);
  }

  @Override
  public void progress(String message) {
    messages.add(new Message(MessageType.PROGRESS, message));
    outputConsole.progress(message);
  }

  @Override
  public boolean promptConfirmation(String message) throws IOException {
    Preconditions.checkState(!programmedResponses.isEmpty(), "No more programmed responses.");
    warn(message);
    return programmedResponses.removeFirst() == PromptResponse.YES;
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return outputConsole.colorize(ansiColor, message);
  }
}
