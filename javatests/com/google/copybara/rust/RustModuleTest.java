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

package com.google.copybara.rust;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RustModuleTest {
  SkylarkTestExecutor starlark;

  @Before
  public void setup() {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testGetVersionRequirement() throws Exception {
    boolean result =
        starlark.eval(
            "result",
            "result = rust.create_version_requirement(requirement ="
                + " \"1.8.0\").fulfills('1.8.0')");
    assertThat(result).isTrue();
  }
}
