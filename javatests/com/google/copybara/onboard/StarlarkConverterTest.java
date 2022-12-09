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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StarlarkConverterTest {
  private TestingConsole console;
  private StarlarkConverter starlarkConverter;

  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> T resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };

  @Before
  public void setup() {
    console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    SkylarkTestExecutor starlark = new SkylarkTestExecutor(options);
    starlarkConverter = new StarlarkConverter(starlark.createModuleSet(), console);
  }

  @Test
  public void convertGlob() throws Exception {
    Glob result = (Glob) starlarkConverter
        .convert("glob(include = [\"foo\"], exclude = [\"bar\"])", RESOLVER);

    Path root = Paths.get("/");
    assertThat(result.relativeTo(root).matches(root.resolve("foo"))).isTrue();
    assertThat(result.relativeTo(root).matches(root.resolve("bar"))).isFalse();
  }
}
