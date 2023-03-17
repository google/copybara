/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.toml;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.StarlarkDateTimeModule.StarlarkDateTime;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.time.OffsetDateTime;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TomlTest {
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testTomlString() throws ValidationException {
    String result = parseToml(String.class, "'key = \"value\"'", "key");
    assertThat(result).isEqualTo("value");
  }

  @Test
  public void testTomlInteger() throws ValidationException {
    StarlarkInt result = parseToml(StarlarkInt.class, "'key = 42'", "key");
    assertThat(result).isEqualTo(StarlarkInt.of(42));
  }

  @Test
  public void testTomlFloat() throws ValidationException {
    StarlarkFloat result = parseToml(StarlarkFloat.class, "'key = 3.14'", "key");
    assertThat(result).isEqualTo(StarlarkFloat.of(3.14));
  }

  @Test
  public void testTomlBoolean() throws ValidationException {
    Boolean result1 = parseToml(Boolean.class, "'key = true'", "key");
    assertThat(result1).isTrue();

    Boolean result2 = parseToml(Boolean.class, "'key = false'", "key");
    assertThat(result2).isFalse();
  }

  @Test
  public void testTomlDateTime() throws ValidationException {
    String time = "2023-01-11T17:32:18-08:00";
    StarlarkDateTime result =
        parseToml(StarlarkDateTime.class, String.format("'key = %s'", time), "key");
    assertThat(result).isEqualTo(
        new StarlarkDateTime(OffsetDateTime.parse(time).toEpochSecond(), "-08:00"));
  }

  @Test
  public void testTomlArray() throws ValidationException {
    String content = "'key = [ 1, 2, 3] '";
    Iterable<?> result =
        parseToml(Iterable.class, content, "key");
    assertThat(result).containsExactly(
        StarlarkInt.of(1), StarlarkInt.of(2), StarlarkInt.of(3));
  }

  @Test
  public void testTomlInlineTableKey() throws ValidationException {
    String content = "'key = { subkey = \"value\" }'";
    String result = parseToml(String.class, String.format("%s", content), "key.subkey");
    assertThat(result).isEqualTo("value");
  }

  @Test
  public void testTomlRegularTableKey() throws ValidationException {
    String content = "\"\"\""
        + "[table]\n"
        + "key = \"value\"\n"
        + "other_key = [ 1, [\"foo\"] ]\n"
        + "\"\"\"\n";

    String result1 = parseToml(String.class, String.format("%s", content), "table.key");
    assertThat(result1).isEqualTo("value");
    Iterable<?> result2 =
        parseToml(Iterable.class, content, "table.other_key");
    assertThat(result2).containsExactly(StarlarkInt.of(1), StarlarkList.immutableOf("foo"));
  }

  @Test
  public void testTomlTableReturnValue() throws ValidationException {
    String content = "\"\"\""
        + "[table]\n"
        + "key = \"value\"\n"
        + "other_key = [ 1, [\"foo\"] ]\n"
        + "\"\"\"\n";

    Iterable<?> result = parseToml(Iterable.class, String.format("%s", content), "table");
    assertThat(result).containsExactlyElementsIn(
        Dict.immutableCopyOf(
          ImmutableMap.of(
            "other_key", StarlarkList.<StarlarkValue>immutableOf(
                  StarlarkInt.of(1), StarlarkList.immutableOf("foo")),
            "key", "value")));
  }

  @Test
  public void testKeyDoesNotExist() throws ValidationException {
    NoneType result = parseToml(NoneType.class, "'foo = 42'", "bar");
    assertThat(result).isInstanceOf(NoneType.class);
  }
  @Test
  public void testBadTomlError() {
    // Below string definition is missing closing quote.
    ValidationException e = assertThrows(
        ValidationException.class,
        () -> parseToml(String.class, "'key = \"value'", "key"));
    assertThat(e).hasMessageThat()
        .containsMatch("Unexpected end of input, expected \" or a character");
  }


  public <T> T parseToml(Class<T> clazz, String content, String key) throws ValidationException {
    return clazz.cast(skylark.eval(
        "x",
        String.format("toml_content = %s\n"
            + "toml_result = toml.parse(content = toml_content)\n"
            + "x = toml_result.get(key ='%s')", content, key))
    );
  }

}
