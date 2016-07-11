package com.google.copybara.util.console.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.AssertingConsole.PromptResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AssertingConsoleTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private AssertingConsole console;

  @Before
  public void setup() {
    console = new AssertingConsole();
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
            console.assertNextMatches("asdf");
          }
        });
  }

  @Test
  public void assertFailsWhenNoMoreMessagesRemain() {
    console.error("foo");
    console.assertNextMatches("foo");
    expectAssertion("no more console messages.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches("foo");
          }
        });
  }

  @Test
  public void allMethodsAddMessages() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console
        .assertNextMatches("error method")
        .assertNextMatches("warn method")
        .assertNextMatches("info method")
        .assertNextMatches("progress method")
        .assertNoMore();
  }

  @Test
  public void assertMessageTypeCorrect() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console
        .assertNextMatches(AssertingConsole.MessageType.ERROR, "error method")
        .assertNextMatches(AssertingConsole.MessageType.WARNING, "warn method")
        .assertNextMatches(AssertingConsole.MessageType.INFO, "info method")
        .assertNextMatches(AssertingConsole.MessageType.PROGRESS, "progress method")
        .assertNoMore();
  }

  @Test
  public void assertMessageTypeWrong1() {
    console.error("foo");
    expectAssertion(".*foo.*",
        new Runnable() {
          @Override
          public void run() {
            console.assertNextMatches(AssertingConsole.MessageType.WARNING, "foo");
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
            console.assertNextMatches(AssertingConsole.MessageType.PROGRESS, "bar");
          }
        });
  }

  @Test
  public void programmedResponses() throws Exception {
    Console console = new AssertingConsole(PromptResponse.YES, PromptResponse.NO);
    assertThat(console.promptConfirmation("Proceed?")).isTrue();
    assertThat(console.promptConfirmation("Proceed?")).isFalse();
  }

  @Test
  public void throwsExceptionIfNoMoreResponses() throws Exception {
    Console console = new AssertingConsole(PromptResponse.NO);
    console.promptConfirmation("Proceed?");
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("No more programmed responses");
    console.promptConfirmation("Proceed?");
  }
}
