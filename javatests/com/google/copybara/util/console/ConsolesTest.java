// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console;

import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConsolesTest {

  private TestingConsole console;

  @Before
  public void setup() {
    this.console = new TestingConsole();
  }

  @Test
  public void logLines_empty() {
    Consoles.logLines(console, "prefix", /*text*/ "");
    console.assertThat()
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneLine() {
    Consoles.logLines(console, "fooprefix-", ""
        + "hello\n"
        + "goodbye\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-hello")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-goodbye")
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneEmptyLine() {
    Consoles.logLines(console, "fooprefix-", "\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-")
        .containsNoMoreMessages();
  }

  @Test
  public void logLines_oneEmptyLineSurroundedByNonEmpty() {
    Consoles.logLines(console, "fooprefix-", ""
        + "x\n"
        + "\n"
        + "y\n");
    console.assertThat()
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-x")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-")
        .equalsNext(TestingConsole.MessageType.INFO, "fooprefix-y")
        .containsNoMoreMessages();
  }
}
