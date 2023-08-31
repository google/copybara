/*
 * Copyright (C) 2023 Google LLC.
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

package com.google.copybara.html;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HtmlTest {

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testSelectOneElements() throws ValidationException, IOException {
    List<HtmlElement> elements =
        skylark.<List<HtmlElement>>eval(
            "e",
            ""
                + "content = '"
                + "<body>"
                + "  <p> 1 </p>"
                + "</body>"
                + "'\n"
                + "e = html.xpath(content, '//p')");
    assertThat(elements).hasSize(1);
  }

  @Test
  public void testSelectMultipleElements() throws ValidationException, IOException {
    List<HtmlElement> elements =
        skylark.<List<HtmlElement>>eval(
            "e",
            ""
                + "content = '"
                + "<body>"
                + "  <p> 1 </p>"
                + "  <p> 2 </p>"
                + "  <random>"
                + "    <p> 3 </p>"
                + "  </random>"
                + "</body>"
                + "'\n"
                + "e = html.xpath(content, '//p')");
    assertThat(elements).hasSize(3);
  }

  @Test
  public void testSelectNonExistingElements() throws ValidationException, IOException {
    List<HtmlElement> elements =
        skylark.<List<HtmlElement>>eval(
            "e",
            ""
                + "content = '"
                + "<body>"
                + "  <p> 1 </p>"
                + "  <p> 2 </p>"
                + "  <random>"
                + "    <p> 3 </p>"
                + "  </random>"
                + "</body>"
                + "'\n"
                + "e = html.xpath(content, '//dev')");
    assertThat(elements).isEmpty();
  }

  @Test
  public void testSelectUnsupportedType() throws ValidationException, IOException {
    List<HtmlElement> elements =
        skylark.<List<HtmlElement>>eval(
            "e",
            ""
                + "content = '"
                + "<body>"
                + "  <p> 1 </p>"
                + "</body>"
                + "'\n"
                + "e = html.xpath(content, '//p/text()')");
    assertThat(elements).isEmpty();
  }

  @Test
  public void testExtractAttributes() throws ValidationException, IOException {
    List<HtmlElement> elements =
        skylark.<List<HtmlElement>>eval(
            "e",
            ""
                + "content = '"
                + "<body>"
                + "  <p id=\"example\" name=\"one\"> 1 </p>"
                + "</body>"
                + "'\n"
                + "e = html.xpath(content, '//p')");
    assertThat(elements).hasSize(1);
    assertThat(elements.get(0).attr("id")).isEqualTo("example");
    assertThat(elements.get(0).attr("name")).isEqualTo("one");
    assertThat(elements.get(0).attr("label")).isEmpty();
  }
}
