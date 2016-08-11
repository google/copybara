package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.LogSubjects.LogSubject;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestingConsoleTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private TestingConsole console;

  @Before
  public void setup() {
    console = new TestingConsole();
  }

  private void expectAssertion(String errorRegex, Runnable failingOperation) {
    AssertionError thrown = null;
    try {
      failingOperation.run();
    } catch (AssertionError e) {
      thrown = e;
    }
    assertThat(thrown.getMessage()).matches(errorRegex);
  }

  @Test
  public void assertFailsForPartialMatch() {
    console.error("jjj_asdf_mmm");
    final LogSubject logSubject = console.assertThat();
    expectAssertion(".*jjj_asdf_mmm.*",
        new Runnable() {
          @Override
          public void run() {
            logSubject.matchesNext(MessageType.ERROR, "asdf");
          }
        });
  }

  @Test
  public void assertFailsWhenNoMoreMessagesRemain() {
    console.error("foo");
    final LogSubject logSubject = console.assertThat()
        .matchesNext(MessageType.ERROR, "foo");
    expectAssertion("no more log messages.*",
        new Runnable() {
          @Override
          public void run() {
            logSubject.matchesNext(MessageType.ERROR, "foo");
          }
        });
  }

  @Test
  public void allMethodsAddMessages() {
    console.error("error method 1234");
    console.warn("warn method 1234");
    console.info("info method 1234");
    console.progress("progress method 1234");
    console.assertThat()
        .matchesNext(MessageType.ERROR, "error method \\d{4}")
        .matchesNext(MessageType.WARNING, "warn method \\d{4}")
        .matchesNext(MessageType.INFO, "info method \\d{4}")
        .matchesNext(MessageType.PROGRESS, "progress method \\d{4}")
        .containsNoMoreMessages();
  }

  @Test
  public void assertNextEquals() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console.assertThat()
        .equalsNext(MessageType.ERROR, "error method")
        .equalsNext(MessageType.WARNING, "warn method")
        .equalsNext(MessageType.INFO, "info method")
        .equalsNext(MessageType.PROGRESS, "progress method")
        .containsNoMoreMessages();
  }

  @Test
  public void assertMessageTypeCorrect() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console.assertThat()
        .matchesNext(MessageType.ERROR, "error method")
        .matchesNext(MessageType.WARNING, "warn method")
        .matchesNext(MessageType.INFO, "info method")
        .matchesNext(MessageType.PROGRESS, "progress method")
        .containsNoMoreMessages();
  }

  @Test
  public void assertMessageTypeWrong1() {
    console.error("foo");
    expectAssertion(".*foo.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertThat()
                .matchesNext(MessageType.WARNING, "foo");
          }
        });
  }

  @Test
  public void assertMessageTypeWrong2() {
    console.info("bar");
    final LogSubject logSubject = console.assertThat();
    expectAssertion(".*bar.*",
        new Runnable() {
          @Override
          public void run() {
            logSubject.matchesNext(MessageType.PROGRESS, "bar");
          }
        });
  }

  @Test
  public void programmedResponses() throws Exception {
    Console console = new TestingConsole()
        .respondYes()
        .respondNo();
    assertThat(console.promptConfirmation("Proceed?")).isTrue();
    assertThat(console.promptConfirmation("Proceed?")).isFalse();
  }

  @Test
  public void throwsExceptionIfNoMoreResponses() throws Exception {
    Console console = new TestingConsole()
        .respondNo();
    console.promptConfirmation("Proceed?");
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("No more programmed responses");
    console.promptConfirmation("Proceed?");
  }
}
