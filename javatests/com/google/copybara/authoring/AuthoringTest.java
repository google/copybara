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

package com.google.copybara.authoring;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthoringTest {

  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  @Before
  public void setUp() throws Exception {
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void overwriteTest() throws Exception {
    Authoring authoring = skylark.eval("result",
        "result = authoring.overwrite('foo bar <baz@bar.com>')");
    assertThat(authoring)
        .isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.OVERWRITE, ImmutableSet.of()));
  }

  @Test
  public void passThruTest() throws Exception {
    Authoring authoring = skylark.eval("result",
        "result = authoring.pass_thru('foo bar <baz@bar.com>')");
    assertThat(authoring)
        .isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.PASS_THRU, ImmutableSet.of()));
  }

  @Test
  public void whitelistedTest() throws Exception {
    Authoring authoring = skylark.eval("result", ""
        + "result = authoring.whitelisted(\n"
        + "    default = 'foo bar <baz@bar.com>',\n"
        + "    whitelist = ['foo', 'bar'])");
    assertThat(authoring)
        .isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.WHITELISTED, ImmutableSet.of("foo", "bar")));
  }

  @Test
  public void testWhitelistMappingDuplicates() throws Exception {
    skylark.evalFails(""
            + "authoring.whitelisted(\n"
            + "  default = 'Copybara <no-reply@google.com>',\n"
            + "  whitelist = ['foo', 'foo']\n"
            + ")\n",
        "Duplicated whitelist entry 'foo'");
  }

  @Test
  public void testDefaultAuthorNotEmpty() throws Exception {
    skylark.evalFails("authoring.overwrite()\n",
        "parameter 'default' has no default value");
  }


  @Test
  public void testInvalidDefaultAuthor() throws Exception {
    skylark.evalFails(""
            + "authoring.overwrite(\n"
            + "    default = 'invalid')\n",
        "Author 'invalid' doesn't match the expected format 'name <mail@example.com>");
  }

  @Test
  public void testWhitelistNotEmpty() throws Exception {
    skylark.evalFails(""
            + "authoring.whitelisted(\n"
            + "  default = 'Copybara <no-reply@google.com>',\n"
            + "  whitelist = []\n"
            + ")\n",
        "'whitelisted' function requires a non-empty 'whitelist' field. "
            + "For default mapping, use 'overwrite\\(...\\)' mode instead.");
  }

  @Test
  public void testResolve_use_default() throws Exception {
    Authoring authoring = new Authoring(DEFAULT_AUTHOR, AuthoringMappingMode.OVERWRITE,
        ImmutableSet.of());
    assertThat(authoring.useAuthor("baz@bar.com")).isFalse();
  }

  @Test
  public void testResolve_pass_thru() throws Exception {
    Authoring authoring = new Authoring(DEFAULT_AUTHOR, AuthoringMappingMode.PASS_THRU,
        ImmutableSet.of());
    assertThat(authoring.useAuthor("baz@bar.com")).isTrue();
  }

  @Test
  public void testResolve_whitelist() throws Exception {
    Authoring authoring = new Authoring(
        DEFAULT_AUTHOR, AuthoringMappingMode.WHITELISTED, ImmutableSet.of("baz@bar.com"));
    assertThat(authoring.useAuthor("baz@bar.com")).isTrue();
    assertThat(authoring.useAuthor("john@someemail.com")).isFalse();
  }
}
