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
package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.StructModule.StructImpl;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import net.starlark.java.eval.Printer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StructModuleTest {
  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private OptionsBuilder options;

  @Before
  public void setup() throws IOException {
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
    console = new TestingConsole();
    options.setConsole(console);
  }

  @Test
  public void testBasicConstructor() throws Exception {
    skylark.eval("x", "x=struct(foo='bar')");
  }

  @Test
  public void testReprParses() throws Exception {
    StructImpl x = skylark.eval("x", "x=struct(foo='bar')");
    StructImpl y = skylark.eval("y", "y=" + new Printer().repr(x));
    assertThat(x).isEqualTo(y);
  }

  @Test
  public void testEquality() throws Exception {
    assertThat((Boolean) skylark.eval("x", "x=struct(foo='bar') == struct(foo='bar')"))
        .isTrue();
    assertThat((Boolean) skylark.eval("x", "x=struct(foo='bar') == struct(bar='bar')"))
        .isFalse();
    assertThat((Boolean) skylark.eval("x", "x=struct(foo='bar') == struct(foo='foo')"))
        .isFalse();
  }

  @Test
  public void testKnownField() throws Exception {
    String str = skylark.eval("x", "test = struct(foo='bar')\nx = test.foo");
    assertThat(str).isEqualTo("bar");
  }

  @Test
  public void testUnknownField() throws Exception {
    skylark.evalFails("struct(foo = 'bar').nope", "Field nope is unknown");
  }
}
