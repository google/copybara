/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.util.console;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class DelegateConsoleTest {

  @Test
  public void testConsole() {
    TestingConsole delegate = new TestingConsole();
    List<Message> messages = new ArrayList<>();
    DelegateConsole delegating =
        new DelegateConsole(delegate) {
          @Override
          protected void handleMessage(MessageType type, String message) {
            messages.add(new Message(type, message));
          }
        };

    delegating.startupMessage("v1");
    delegating.info("This is info");
    delegating.warn("This is warning");
    delegating.error("This is error");
    delegating.verbose("This is verbose");
    delegating.progress("This is progress");

    assertThat(messages)
        .containsExactly(
            new Message(MessageType.INFO, "Copybara source mover (Version: v1)"),
            new Message(MessageType.INFO, "This is info"),
            new Message(MessageType.WARNING, "This is warning"),
            new Message(MessageType.ERROR, "This is error"),
            new Message(MessageType.VERBOSE, "This is verbose"),
            new Message(MessageType.PROGRESS, "This is progress"));

    delegate
        .assertThat()
        .matchesNext(MessageType.INFO, "Copybara source mover [(]Version: v1[)]")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.ERROR, "This is error")
        .matchesNext(MessageType.VERBOSE, "This is verbose")
        .matchesNext(MessageType.PROGRESS, "This is progress");
  }

  @Test
  public void testDelegateAsk() throws IOException {
    Console delegate = Mockito.mock(Console.class);

    when(delegate.ask(Mockito.eq("fail"), anyString(), any()))
        .thenThrow(new RuntimeException("should fail"));

    when(delegate.ask(Mockito.eq("work"), anyString(), any())).thenReturn("good");

    DelegateConsole console = new DelegateConsole(delegate) {
      @Override
      protected void handleMessage(MessageType info, String message) {
      }
    };

    RuntimeException e =
        assertThrows(RuntimeException.class, () -> console.ask("fail", "aaa", s -> true));
    assertThat(e).hasMessageThat().contains("should fail");

    assertThat(console.ask("work", "aaa", s-> true)).isEqualTo("good");
  }
}
