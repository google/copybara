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

package com.google.copybara.xml;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XmlTest {

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void stringMatch() throws ValidationException, IOException {
    assertThat(skylark.<String>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>the value</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'STRING')"))
        .isEqualTo("the value");
  }

  @Test
  public void booleanMatch() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>True</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'BOOLEAN')"))
        .isTrue();
  }

  @Test
  public void doubleMatch() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "x = xml.xpath(content = '"
        + "<test>"
        + "  <some>"
        + "    <value>42</value>"
        + "  </some>"
        + "</test>', expression = '/test/some/value', type = 'NUMBER') == 42"))
        .isTrue();
  }
}
