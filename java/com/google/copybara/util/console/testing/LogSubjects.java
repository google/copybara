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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Truth subjects for assertions on logs.
 */
public class LogSubjects {

  static final Subject.Factory<LogSubject, TestingConsole> CONSOLE_SUBJECT_FACTORY =
      LogSubject::new;

  private LogSubjects() {}

  public static class LogSubject extends Subject {

    private final ArrayDeque<Message> messages;

    LogSubject(FailureMetadata failureMetadata, TestingConsole target) {
      super(failureMetadata, target);
      this.messages = new ArrayDeque<>(target.getMessages());
    }

    /**
     * Asserts the next message matches some regex and is of the given type.
     *
     * @param type the type of the message, or {@code null} to allow any type
     * @param regex a regex which must fully match the next message.
     */
    public LogSubject matchesNext(@Nullable MessageType type, String regex) {
      return assertRegex(/*skipAhead*/ false, type, regex);
    }

    /**
     * Same as {@link #matchesNext(MessageType, String)} but skipping any messages that do not
     * match until one that matches is found.
     */
    public LogSubject matchesNextSkipAhead(@Nullable MessageType type, String regex) {
      return assertRegex(/*skipAhead*/ true, type, regex);
    }

    /**
     * Asserts the next message is equals to a string literal.
     *
     * <p>This is a convenience method to avoid having to escape special characters in the regex.
     */
    public LogSubject equalsNext(@Nullable MessageType type, String text) {
      return assertRegex(/*skipAhead*/ false, type, Pattern.quote(text));
    }

    /**
     * Same as {@link #equalsNext(MessageType, String)} but skipping any messages that do not
     * match until one that matches is found.
     */
    public LogSubject equalsNextSkipAhead(@Nullable MessageType type, String text) {
      return assertRegex(/*skipAhead*/ true, type, Pattern.quote(text));
    }

    /**
     * Asserts that at least one log message of the specified {@code type} contains a match for the
     * regular expression {@code regex}.
     */
    public LogSubject logContains(MessageType type, String regex) {
      Pattern rx = Pattern.compile(regex);
      for (Message message : messages) {
        if (message.getType().equals(type) && rx.matcher(message.getText()).find()) {
          return this; // found
        }
      }
      throw new AssertionError(
          "No log message matched the regular expression: "
              + regex
              + "\nExisting messages:"
              + "\n--------\n"
              + Joiner.on("\n").join(messages)
              + "\n--------\n");
    }

    /**
     * Assert that a regex text message appears once in the console output for a certain message
     * {@code type}.
     */
    public LogSubject onceInLog(MessageType type, String regex) {
      return timesInLog(1, type, regex);
    }

    /**
     * Assert that a regex text message appears some number of times in the console output for a
     * certain message {@code type}.
     */
    public LogSubject timesInLog(int times, MessageType type, String regex) {
      Pattern rx = Pattern.compile(regex);
      List<Message> matches = new ArrayList<>();
      for (Message message : messages) {
        if (message.getType().equals(type) && rx.matcher(message.getText()).find()) {
          matches.add(message);
        }
      }
      assertWithMessage(regex + " was not found in log the expected number of times (" + times + ")"
          + " Do you have [] or other unescaped chars in the regex? Existing messages:"
          + "\n--------\n"
          + Joiner.on("\n").join(messages)
          + "\n--------\n")
          .that(matches).hasSize(times);
      return this;
    }

    private LogSubject assertRegex(boolean skipAhead, @Nullable MessageType type, String regex) {
      assertWithMessage("no more log messages")
          .that(messages)
          .isNotEmpty();
      Message next = messages.removeFirst();
      try {
        assertWithMessage(regex + " does not match " + next.getText()
            + ". Do you have [] or other unescaped chars in the regex?")
            .that(next.getText()).matches(regex);
        if (type != null) {
          assertWithMessage("type of message with text: " + next.getText())
              .that(next.getType())
              .isEqualTo(type);
        }
      } catch (AssertionError e) {
        if (!skipAhead || messages.isEmpty()) {
          throw e;
        }
        assertRegex(skipAhead, type, regex);
      }
      return this;
    }

    /**
     * There are no more lines in the logs.
     */
    public LogSubject containsNoMoreMessages() {
      assertThat(messages).isEmpty();
      return this;
    }
  }
}
