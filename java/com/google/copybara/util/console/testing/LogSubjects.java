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
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
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

  static final SubjectFactory<LogSubject, TestingConsole> CONSOLE_SUBJECT_FACTORY =
      new SubjectFactory<LogSubject, TestingConsole>() {
        @Override
        public LogSubject getSubject(FailureStrategy failureStrategy, TestingConsole target) {
          return new LogSubject(failureStrategy, target);
        }
      };

  private LogSubjects() {}

  public static class LogSubject extends Subject<LogSubject, TestingConsole> {

    private final ArrayDeque<Message> messages;

    LogSubject(FailureStrategy failureStrategy, TestingConsole target) {
      super(failureStrategy, target);
      this.messages = new ArrayDeque<>(target.getMessages());
    }

    /**
     * Asserts the next message matches some regex and is of the given type.
     *
     * @param type the type of the message, or {@code null} to allow any type
     * @param regex a regex which must fully match the next message.
     */
    public LogSubject matchesNext(@Nullable MessageType type, String regex) {
      return assertRegex(type, regex);
    }

    /**
     * Asserts the next message is equals to a string literal.
     *
     * <p>This is a convenience method to avoid having to escape special characters in the regex.
     */
    public LogSubject equalsNext(@Nullable MessageType type, String text) {
      return assertRegex(type, Pattern.quote(text));
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
      List<Message> matches = new ArrayList<>();
      for (Message message : messages) {
        if (message.getType().equals(type) && message.getText().matches(regex)) {
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

    private LogSubject assertRegex(@Nullable MessageType type, String regex) {
      assertWithMessage("no more log messages")
          .that(messages)
          .isNotEmpty();
      Message next = messages.removeFirst();
      assertWithMessage(regex + " does not match " + next.getText()
          + ". Do you have [] or other unescaped chars in the regex?")
          .that(next.getText()).matches(regex);
      if (type != null) {
        assertWithMessage("type of message with text: " + next.getText())
            .that(next.getType())
            .isEqualTo(type);
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
