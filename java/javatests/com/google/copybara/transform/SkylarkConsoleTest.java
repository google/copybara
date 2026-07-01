/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SkylarkConsoleTest {

  @Test
  public void testColorize() {
    Console delegate = mock(Console.class);
    when(delegate.colorize(any(), any())).thenReturn("colorized!");
    assertThat(new SkylarkConsole(delegate).colorize(AnsiColor.BLUE, "aaaa"))
        .isEqualTo("colorized!");
  }

  @Test
  public void testAsk() throws Exception {
    Console delegate = mock(Console.class);
    when(delegate.ask(any(), any(), any())).thenReturn("answer!");
    assertThat(new SkylarkConsole(delegate).ask("aaaa", "bbb", s -> true)).isEqualTo("answer!");
  }
}
