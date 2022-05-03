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

package com.google.copybara.re2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Re2Test {

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void matches() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", "x = re2.compile('a.*b').matches('axxxxxb')"))
        .isTrue();
  }
  @Test
  public void matcherMatches() throws ValidationException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "re = re2.compile('a.*b')\n"
        + "x = re.matcher('axxxxxb').matches()\n")).isTrue();
  }
  @Test
  public void find() throws ValidationException, IOException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "re = re2.compile('a.*b')\n"
        + "x = re.matcher('axxxxxb').find()\n")).isTrue();
  }
  @Test
  public void findCount() throws ValidationException {
    assertThat(skylark.<Boolean>eval("x", ""
        + "re = re2.compile('a.*b')\n"
        + "x = re.matcher('axxxxxb').find(1)\n")).isFalse();
  }

  @Test
  public void group() throws ValidationException, IOException {
    assertThat(skylark.<String>eval("x", ""
        + "re = re2.compile('a(.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.group(1)")).isEqualTo("xxxxx");
  }

  @Test
  public void group_name() throws ValidationException {
    assertThat(skylark.<String>eval("x", ""
        + "re = re2.compile('a(?P<name>.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.group('name')")).isEqualTo("xxxxx");
  }

  @Test
  public void groupCount() throws ValidationException, EvalException {
    assertThat(skylark.<StarlarkInt>eval("x", ""
        + "re = re2.compile('a(x*)(y*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.group_count()").toInt("x")).isEqualTo(2);
  }

  @Test
  public void groupError() {
    assertThat(assertThrows(ValidationException.class, () ->
        skylark.<Boolean>eval("x", ""
            + "re = re2.compile('a(.*)b')\n"
            + "m = re.matcher('axxxxxb')\n"
            + "x = m.group(1)"))).hasMessageThat().contains("Call to group() is not allowed");
  }

  @Test
  public void end() throws ValidationException, IOException, EvalException {
    assertThat(skylark.<StarlarkInt>eval("x", ""
        + "re = re2.compile('a(.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.end(1)").toInt("x")).isEqualTo(6);
  }

  @Test
  public void end_name() throws ValidationException, EvalException {
    assertThat(skylark.<StarlarkInt>eval("x", ""
        + "re = re2.compile('a(?P<name>.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.end('name')").toInt("x")).isEqualTo(6);
  }

  @Test
  public void endError() {
    assertThat(assertThrows(ValidationException.class, () ->
        skylark.<Boolean>eval("x", ""
            + "re = re2.compile('a(.*)b')\n"
            + "m = re.matcher('axxxxxb')\n"
            + "x = m.end(1)"))).hasMessageThat().contains("Call to end() is not allowed");
  }

  @Test
  public void start() throws ValidationException, IOException, EvalException {
    assertThat(skylark.<StarlarkInt>eval("x", ""
        + "re = re2.compile('a(.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.start(1)").toInt("x")).isEqualTo(1);
  }

  @Test
  public void start_name() throws ValidationException, EvalException {
    assertThat(skylark.<StarlarkInt>eval("x", ""
        + "re = re2.compile('a(?P<name>.*)b')\n"
        + "m = re.matcher('axxxxxb')\n"
        + "m.matches()\n"
        + "x = m.start('name')").toInt("x")).isEqualTo(1);
  }

  @Test
  public void startError() {
    assertThat(assertThrows(ValidationException.class, () ->
        skylark.<Boolean>eval("x", ""
            + "re = re2.compile('a(.*)b')\n"
            + "m = re.matcher('axxxxxb')\n"
            + "x = m.start(1)"))).hasMessageThat().contains("Call to start() is not allowed");
  }

  @Test
  public void replaceAll() throws ValidationException, IOException {
    assertThat(skylark.<String>eval("x", ""
        + "re = re2.compile('a(b*)c')\n"
        + "m = re.matcher('abbcabbbc')\n"
        + "m.matches()\n"
        + "x = m.replace_all('HELLO')")).isEqualTo("HELLOHELLO");
  }

  @Test
  public void replaceFirst() throws ValidationException {
    assertThat(skylark.<String>eval("x", ""
        + "re = re2.compile('a(b*)c')\n"
        + "m = re.matcher('abbcabbbc')\n"
        + "m.matches()\n"
        + "x = m.replace_first('HELLO')")).isEqualTo("HELLOabbbc");
  }

  @Test
  public void replaceAllParam() throws ValidationException {
    assertThat(skylark.<String>eval("x", ""
        + "re = re2.compile('a(b*)c')\n"
        + "m = re.matcher('abbcabbbc')\n"
        + "m.matches()\n"
        + "x = m.replace_all('HELLO$1')")).isEqualTo("HELLObbHELLObbb");
  }
}
