/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.jcommander;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.beust.jcommander.ParameterException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapConverterTest {

  private final MapConverter converter = new MapConverter();

  @Test
  public void unquotedValues() {
    assertThat(converter.convert("foo:bar,baz:qux"))
        .containsExactly("foo", "bar", "baz", "qux")
        .inOrder();
  }

  @Test
  public void emptyString() {
    assertThat(converter.convert("")).isEmpty();
  }

  @Test
  public void trimsAndSkipsEmptyEntries() {
    assertThat(converter.convert(" foo : bar ,, baz:qux, "))
        .containsExactly("foo", "bar", "baz", "qux");
  }

  @Test
  public void emptyValue() {
    assertThat(converter.convert("foo:,bar:\"\"")).containsExactly("foo", "", "bar", "");
  }

  @Test
  public void unquotedValueCanContainColon() {
    assertThat(converter.convert("url:https://github.com/google/foo,branch:main"))
        .containsExactly("url", "https://github.com/google/foo", "branch", "main");
  }

  @Test
  public void quotedValueCanContainCommaAndColon() {
    assertThat(
            converter.convert(
                "transformations:\"move to root, and drop BUILD files\","
                    + "url:\"https://github.com/google/foo\""))
        .containsExactly(
            "transformations", "move to root, and drop BUILD files",
            "url", "https://github.com/google/foo");
  }

  @Test
  public void quotedValuePreservesInnerWhitespace() {
    assertThat(converter.convert("foo: \"  a, b  \" ,bar:c"))
        .containsExactly("foo", "  a, b  ", "bar", "c");
  }

  @Test
  public void escapesInsideQuotes() {
    assertThat(converter.convert("foo:\"say \\\"hi\\\", C:\\\\dir\""))
        .containsExactly("foo", "say \"hi\", C:\\dir");
  }

  @Test
  public void quotesAndBackslashesOutsideLeadingQuoteAreLiteral() {
    assertThat(converter.convert("foo:it\"s,bar:C:\\dir"))
        .containsExactly("foo", "it\"s", "bar", "C:\\dir");
  }

  @Test
  public void missingColon() {
    assertError("foo:bar,baz", "entry 'baz' is missing ':'");
  }

  @Test
  public void emptyKey() {
    assertError(":bar", "empty key");
  }

  @Test
  public void unterminatedQuote() {
    assertError("foo:\"a, b", "value for 'foo' has an unterminated quote");
  }

  @Test
  public void trailingBackslashInsideQuote() {
    assertError("foo:\"a\\", "value for 'foo' has an unterminated quote");
  }

  @Test
  public void textAfterClosingQuote() {
    assertError(
        "foo:\"a\"b,bar:c", "unexpected text after the closing quote of the value for 'foo'");
  }

  @Test
  public void duplicateKey() {
    assertError("foo:a,foo:b", "duplicate key 'foo'");
  }

  @Test
  public void errorMessageExplainsQuoting() {
    ParameterException e = assertThrows(ParameterException.class, () -> converter.convert("foo"));
    assertThat(e).hasMessageThat().contains("Wrap a value in double quotes if it contains ','");
  }

  private void assertError(String input, String expected) {
    ParameterException e = assertThrows(ParameterException.class, () -> converter.convert(input));
    assertThat(e).hasMessageThat().contains(expected);
  }
}
