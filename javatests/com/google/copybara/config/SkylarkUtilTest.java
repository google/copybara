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

package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SkylarkUtilTest {

  private SkylarkTestExecutor starlark;

  @Before
  public void doBeforeEachTest() {
    OptionsBuilder options = new OptionsBuilder();
    starlark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testDictNestedInDictSuccess() throws Exception {
    Object o = starlark.eval("v", "v = {'outer_key': {'inner_key': 'inner_value'}}");
    Dict<String, Dict<String, String>> parsed =
        SkylarkUtil.castOfDictNestedInDict(o, String.class, String.class, String.class, "test");
    assertThat(parsed.get("outer_key").get("inner_key")).isEqualTo("inner_value");
  }

  @Test
  public void testDictNestedInDictIncorrectType() throws Exception {
    Object o = starlark.eval("v", "v = {1: {'inner_key': ['value_1', 'value_2']}}");
    EvalException e =
        assertThrows(
            EvalException.class,
            () ->
                SkylarkUtil.castOfDictNestedInDict(
                    o, String.class, String.class, String.class, "test"));
    assertThat(e).hasMessageThat().contains("Key not assignable. Wanted string, got int");
  }
}
