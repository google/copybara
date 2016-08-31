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

package com.google.copybara;

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
    Author author = Author.parse(/*location*/ null, "Foo Bar <foo@bar.com>");
    assertThat(author.getName()).isEqualTo("Foo Bar");
    assertThat(author.getEmail()).isEqualTo("foo@bar.com");
  }

  @Test
  public void testWrongEmailFormat() throws Exception {
    thrown.expect(EvalException.class);
    thrown.expectMessage(
        "Author 'foo-bar' doesn't match the expected format 'name <mail@example.com>");
    Author.parse(/*location*/ null, "foo-bar");
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
        .addEqualityGroup(
            new Author("Foo Bar", "foo@bar.com"), new Author("Foo Bar", "foo@bar.com"))
        .addEqualityGroup(new Author("Copybara", "no-reply@google.com"))
        .testEquals();
  }
}
