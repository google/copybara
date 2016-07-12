package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.util.console.Console;
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
  public ExpectedException expectedException = ExpectedException.none();

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
    expectAssertion(".*jjj_asdf_mmm.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches(MessageType.ERROR, "asdf");
          }
        });
  }

  @Test
  public void assertFailsWhenNoMoreMessagesRemain() {
    console.error("foo");
    console.assertNextMatches(MessageType.ERROR, "foo");
    expectAssertion("no more console messages.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches(MessageType.ERROR, "foo");
          }
        });
  }

  @Test
  public void allMethodsAddMessages() {
    console.error("error method 1234");
    console.warn("warn method 1234");
    console.info("info method 1234");
    console.progress("progress method 1234");
    console
        .assertNextMatches(MessageType.ERROR, "error method \\d{4}")
        .assertNextMatches(MessageType.WARNING, "warn method \\d{4}")
        .assertNextMatches(MessageType.INFO, "info method \\d{4}")
        .assertNextMatches(MessageType.PROGRESS, "progress method \\d{4}")
        .assertNoMore();
  }

  @Test
  public void assertNextEquals() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console
        .assertNextEquals(MessageType.ERROR, "error method")
        .assertNextEquals(MessageType.WARNING, "warn method")
        .assertNextEquals(MessageType.INFO, "info method")
        .assertNextEquals(MessageType.PROGRESS, "progress method")
        .assertNoMore();
  }

  @Test
  public void assertMessageTypeCorrect() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console
        .assertNextMatches(MessageType.ERROR, "error method")
        .assertNextMatches(MessageType.WARNING, "warn method")
        .assertNextMatches(MessageType.INFO, "info method")
        .assertNextMatches(MessageType.PROGRESS, "progress method")
        .assertNoMore();
  }

  @Test
  public void assertMessageTypeWrong1() {
    console.error("foo");
    expectAssertion(".*foo.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches(MessageType.WARNING, "foo");
          }
        });
  }

  @Test
  public void assertMessageTypeWrong2() {
    console.info("bar");
    expectAssertion(".*bar.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches(MessageType.PROGRESS, "bar");
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
