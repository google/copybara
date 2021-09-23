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
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CoreTest {

  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  @Before
  public void setup() {
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testGetConfig() throws Exception {
    assertThat(
            skylark.<String>evalWithConfigFilePath(
                "p", "p = core.main_config_path", "some/random/path/copy.bara.sky"))
        .isEqualTo("some/random/path/copy.bara.sky");
  }

  @Test
  public void testFormat() throws Exception {
    assertThat(skylark.<String>eval("f", "f = core.format('%-10s %d', ['foo', 1234])"))
        .isEqualTo("foo        1234");
  }

  @Test
  public void testInvalidFormat() {
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () -> skylark.eval("f", "f = core.format('%-10s %d', ['foo', '1234'])"));
    assertThat(expected)
        .hasMessageThat()
        .contains("Invalid format: %-10s %d: d != java.lang.String");
  }

  @Test
  public void testConsole() throws Exception {
     skylark.<String>eval("f", "f = core.console.info('Hello World')");
     console.assertThat().logContains(MessageType.INFO, "Hello World");
  }
}
