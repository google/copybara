/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.onboard.core.AskInputProvider.Mode;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AskInputProviderTest {

  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> T resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };
  private static final Input<String> INPUT = Input.create("AskInputProviderTest",
      "just for test", null, String.class, (s, resolver) -> s);

  private static final Input<Integer> INT_INPUT = Input.create("AskInputProviderTestInt",
      "just for test", null, Integer.class, (s, resolver) -> Integer.valueOf(s));

  private static final Input<String> INPUT_WITH_DEFAULT = Input.create(
      "AskInputProviderTestWithDefault", "just for test", "aaaa",
      String.class, (s, resolver) -> s);

  private static final Input<Integer> CANNOT_CONVERT = Input.create(
      "AskInputProviderTestCannotConvert",
      "just for test", null, Integer.class,
      (value, resolver) -> {
        try {
          return Integer.valueOf(value);
        } catch (NumberFormatException e) {
          throw new CannotConvertException("Cannot convert");
        }
      });

  private TestingConsole console;

  @Before
  public void setup() {
    console = new TestingConsole();
  }

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, "hello"), Mode.FAIL, console);

    assertThat(provider.provides()).containsExactly(INPUT, InputProvider.DEFAULT_PRIORITY);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void testNoDefault() {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, null), Mode.FAIL, console);

    assertThat(assertThrows(CannotProvideException.class,
        () -> provider.resolve(INPUT, RESOLVER))).hasMessageThat()
        .contains("Couldn't infer a value for AskInputProviderTest");
  }

  @Test
  public void testInputWithDefault() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT_WITH_DEFAULT, null), Mode.FAIL, console);

    assertThat(provider.resolve(INPUT_WITH_DEFAULT, RESOLVER)).isEqualTo(Optional.of("aaaa"));
  }

  @Test
  public void testAuto() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, "hello"), Mode.AUTO, console);

    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void testAutoAsksUser() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, null), Mode.AUTO, console);
    console.respondWithString("hello");
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("hello"));
    console
        .assertThat()
        .onceInLog(
            MessageType.INFO, "just for test\\(AskInputProviderTest\\)\\? \\[default: none\\]");
  }

  @Test
  public void testAutoAsksUserWrongValue() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INT_INPUT, null), Mode.AUTO, console);
    console.respondWithString("hello");
    console.respondWithString("42");
    assertThat(provider.resolve(INT_INPUT, RESOLVER)).isEqualTo(Optional.of(42));
    console
        .assertThat()
        .timesInLog(
            2,
            MessageType.INFO,
            "just for test\\(AskInputProviderTestInt\\)\\? \\[default: none\\]");
  }

  @Test
  public void testAutoAsksUserWrongValueCannotConvertException()
      throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(CANNOT_CONVERT, null), Mode.AUTO, console);
    console.respondWithString("hello");
    console.respondWithString("42");
    assertThat(provider.resolve(CANNOT_CONVERT, RESOLVER)).isEqualTo(Optional.of(42));
    console.assertThat().timesInLog(2, MessageType.INFO, "just for test.*");
  }

  @Test
  public void testConfirm() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, "hello"), Mode.CONFIRM, console);
    console.respondWithString("bye");
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("bye"));
    console
        .assertThat()
        .onceInLog(
            MessageType.INFO, "just for test\\(AskInputProviderTest\\)\\? \\[default: 'hello'\\]");
  }

  @Test
  public void testConfirmDefault() throws CannotProvideException, InterruptedException {
    AskInputProvider provider = new AskInputProvider(
        new ConstantProvider<>(INPUT, "hello"), Mode.CONFIRM, console);
    console.respondWithString("");
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("hello"));
    console
        .assertThat()
        .onceInLog(
            MessageType.INFO, "just for test\\(AskInputProviderTest\\)\\? \\[default: 'hello'\\]");
  }
}
