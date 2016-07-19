package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.Message;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Truth subjects for assertions on logs.
 */
public class LogSubjects {

  private static final SubjectFactory<LogSubject, TestingConsole> CONSOLE_SUBJECT_FACTORY =
      new SubjectFactory<LogSubject, TestingConsole>() {
        @Override
        public LogSubject getSubject(FailureStrategy failureStrategy, TestingConsole target) {
          return new LogSubject(failureStrategy, target);
        }
      };

  public static SubjectFactory<LogSubject, TestingConsole> console() {
    return CONSOLE_SUBJECT_FACTORY;
  }

  private LogSubjects() {}

  /**
   * Truth subject that provides fluent methods for assertions on {@link TestingConsole}s.
   *
   * <p>For example:
   *
   *     assertAbout(LogSubjects.console()))
   *       .that(testConsole)
   *       .matchesNext(...)
   *       .equalsNext(...)
   *       .containsNoMoreMessages();
   */
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
     * * {@code type}.
     */
    public LogSubject onceInLog(MessageType type, String regex) {
      List<Message> matches = new ArrayList<>();
      for (Message message : messages) {
        if (message.getType().equals(type) && message.getText().matches(regex)) {
          matches.add(message);
        }
      }
      assertWithMessage(regex + " matches " + matches.size() + " times. Expected 1")
          .that(matches).hasSize(1);
      return this;
    }

    private LogSubject assertRegex(@Nullable MessageType type, String regex) {
      assertWithMessage("no more log messages")
          .that(messages)
          .isNotEmpty();
      Message next = messages.removeFirst();
      assertThat(next.getText())
          .matches(regex);
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
