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
import static org.junit.Assert.assertThrows;

import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.LogSubjects.LogSubject;
import java.io.IOException;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestingConsoleTest {

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
    assertThat(thrown.getMessage()).matches("(?s)" + errorRegex);
  }

  @Test
  public void testFailsForPartialMatch() {
    console.error("jjj_asdf_mmm");
    LogSubject logSubject = console.assertThat();
    expectAssertion(".*jjj_asdf_mmm.*",
        () -> logSubject.matchesNext(MessageType.ERROR, "asdf"));
  }

  @Test
  public void testFailsWhenNoMoreMessagesRemain() {
    console.error("foo");
    LogSubject logSubject = console.assertThat().matchesNext(MessageType.ERROR, "foo");
    expectAssertion("no more log messages.*",
        () -> logSubject.matchesNext(MessageType.ERROR, "foo"));
  }

  @Test
  public void testMatchesNext() {
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
  public void testMatchesNextSkipAhead() {
    console.error("error method 1234");
    console.warn("warn method 1234");
    console.info("info method 1234");
    console.progress("progress method 1234");
    console.assertThat()
        .matchesNextSkipAhead(MessageType.WARNING, "warn method \\d{4}")
        .matchesNextSkipAhead(MessageType.PROGRESS, "progress method \\d{4}")
        .containsNoMoreMessages();
  }

  @Test
  public void testEqualsNext() {
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
  public void testEqualsNextSkipAhead() {
    console.error("error method");
    console.warn("warn method");
    console.info("info method");
    console.progress("progress method");
    console.assertThat()
        .equalsNextSkipAhead(MessageType.WARNING, "warn method")
        .equalsNextSkipAhead(MessageType.PROGRESS, "progress method")
        .containsNoMoreMessages();
  }

  @Test
  public void testMessageTypeCorrect() {
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
  public void testMessageTypeWrong1() {
    console.error("foo");
    expectAssertion(".*foo.*",
        () -> console.assertThat()
            .matchesNext(MessageType.WARNING, "foo"));
  }

  @Test
  public void testMessageTypeWrong2() {
    console.info("bar");
    LogSubject logSubject = console.assertThat();
    expectAssertion(".*bar.*",
        () -> logSubject.matchesNext(MessageType.PROGRESS, "bar"));
  }

  @Test
  public void testProgrammedResponses() throws Exception {
    Console console = new TestingConsole()
        .respondYes()
        .respondNo();
    assertThat(console.promptConfirmation("Proceed?")).isTrue();
    assertThat(console.promptConfirmation("Proceed?")).isFalse();
  }

  @Test
  public void testProgrammedStringResponses() throws Exception {
    Console console = new TestingConsole().respondWithString("Hello world!");
    assertThat(console.ask("Have anything to say?", "foo", Objects::nonNull))
        .isEqualTo("Hello world!");
  }

  @Test
  public void testThrowsExceptionIfNoMoreResponses() throws Exception {
    Console console = new TestingConsole()
        .respondNo();
    assertThat(console.promptConfirmation("Proceed?")).isFalse();
    IllegalStateException expected = assertThrows(
        IllegalStateException.class,
        () -> console.promptConfirmation("Proceed?"));
    assertThat(expected).hasMessageThat().contains("No more programmed responses");
  }

  @Test
  public void testPredicateCausesAssertionError() throws Exception {
    Console console = new TestingConsole().respondWithString("Lorem Ipsum!");
    assertThrows(
        IOException.class,
        () -> console.ask("Have anything to say?", "foo", x -> x.equals("Hello world!")));
  }
}
