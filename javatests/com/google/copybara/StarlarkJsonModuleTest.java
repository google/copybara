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

import com.google.common.collect.ImmutableMap;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for using bazel's JSON parser in Copybara's Starlark
 */
@RunWith(JUnit4.class)
public class StarlarkJsonModuleTest {
  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private OptionsBuilder options;
  private Gson gson;

  @Before
  public void setup() throws IOException {
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
    console = new TestingConsole();
    options.setConsole(console);
    gson = new Gson();
  }

  @Test
  public void testDecodeTrue() throws Exception {
    boolean result = skylark.eval("x", "x=json.decode('true')");
    assertThat(result).isTrue();
  }

  @Test
  public void testDecodeFalse() throws Exception {
    boolean result = skylark.eval("x", "x=json.decode('false')");
    assertThat(result).isFalse();
  }

  @Test
  public void testDecodeNull() throws Exception {
    NoneType result = skylark.eval("x", "x=json.decode('null')");
    assertThat(result).isInstanceOf(NoneType.class);
  }

  @Test
  public void testDecodeInteger() throws Exception {
    StarlarkInt result = skylark.eval("x", "x=json.decode('22')");
    assertThat(result).isEqualTo(StarlarkInt.of(22));
  }

  @Test
  public void testDecodeFloat() throws Exception {
    StarlarkFloat result = skylark.eval("x", "x=json.decode('21.7')");
    assertThat(result).isEqualTo(StarlarkFloat.of(21.7));
  }

  @Test
  public void testDecodeSimpleJsonObject() throws Exception {
    Dict<String, String> result = skylark.eval("x", "x=json.decode('{\"foo\": \"bar\"}')");
    assertThat(result.containsKey("foo")).isTrue();
    assertThat(result.get("foo")).isEqualTo("bar");
  }

  @Test
  public void testDecodeSimpleJsonArray() throws Exception {
    String json = "x=json.decode('"
        + "[{\"foo\": \"bar\"}, {\"baz\": \"foo\"}]')";

    List<Dict<String, String>> result = skylark.eval("x", json);
    assertThat(result).containsExactly(
        Dict.immutableCopyOf(ImmutableMap.of("foo", "bar")),
        Dict.immutableCopyOf(ImmutableMap.of("baz", "foo")));
  }

  @Test
  public void testDecodeJsonArrayWithObjectValues() throws Exception {
    String json = "x=json.decode('"
        + "[{\"foo\": [22, 23, 24]}, {\"baz\": {\"bar\": true}}]')";

    Dict<String, StarlarkList<StarlarkInt>> dict1 = Dict.immutableCopyOf(
        ImmutableMap.of("foo",
            StarlarkList.immutableOf(
                StarlarkInt.of(22), StarlarkInt.of(23), StarlarkInt.of(24))));

    Dict<String, Dict<String, Boolean>> dict2 = Dict.immutableCopyOf(
        ImmutableMap.of("baz",
            Dict.immutableCopyOf(
                ImmutableMap.of("bar", true))));

    List<?> result = skylark.eval("x", json);
    assertThat(result).containsExactly(dict1, dict2);
  }

  @Test
  public void testEncode() throws Exception {
    String result = skylark.eval("x", "x=json.encode([22, 23, 24])");

    List<Integer> parsed = Arrays.asList(gson.fromJson(result, Integer[].class));
    assertThat(parsed).containsExactly(22, 23, 24);
  }

  @Test
  public void testEncodeDict() throws Exception {
    String result = skylark.eval("x", "x=json.encode({\"foo\": \"bar\", \"bar\": \"baz\"})");

    // Parse the serialized JSON using Gson, so we can check map values directly
    TypeToken<Map<String, String>> mapType = new TypeToken<>() {};
    Map<String, String> parsed = gson.fromJson(result, mapType.getType());

    assertThat(parsed).containsExactlyEntriesIn(
        ImmutableMap.of("foo", "bar", "bar", "baz"));
  }
}
