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

import com.google.common.testing.EqualsTester;
import com.google.devtools.build.lib.syntax.EvalException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthorTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testParse() throws Exception {
    Author author = Author.parse("Foo Bar <foo@bar.com>");
    assertThat(author.getName()).isEqualTo("Foo Bar");
    assertThat(author.getEmail()).isEqualTo("foo@bar.com");
  }

  @Test
  public void testParseEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(
            Author.parse("Foo Bar <foo@bar.com>"),
            Author.parse("'Foo Bar <foo@bar.com>'"),
            Author.parse("\"Foo Bar <foo@bar.com>\""))
        .testEquals();
  }

  @Test
  public void testParse_emptyEmail() throws Exception {
    Author fooBar = Author.parse("Foo Bar <>");
    assertThat(fooBar.getEmail()).isEqualTo("");
    assertThat(fooBar.getName()).isEqualTo("Foo Bar");
  }

  @Test
  public void testWrongEmailFormat() throws Exception {
    thrown.expect(EvalException.class);
    thrown.expectMessage(
        "Author 'foo-bar' doesn't match the expected format 'name <mail@example.com>");
    Author.parse("foo-bar");
  }

  @Test
  public void testToString() throws Exception {
    assertThat(new Author("Foo Bar", "foo@bar.com").toString())
        .isEqualTo("Foo Bar <foo@bar.com>");
    // An empty email is a valid author label
    assertThat(new Author("Foo Bar", "").toString())
        .isEqualTo("Foo Bar <>");
  }

  @Test
  public void testEquals() throws Exception {
    new EqualsTester()
        // Authors with the same non-empty email are the same author
        .addEqualityGroup(
            new Author("Foo Bar", "foo@bar.com"),
            new Author("Foo Bar", "foo@bar.com"),
            new Author("Foo B", "foo@bar.com")
        )
        // Authors with empty email are only equal if they have the same exact name
        .addEqualityGroup(
            new Author("Foo Bar", ""),
            new Author("Foo Bar", "")
        )
        .testEquals();
  }
}
