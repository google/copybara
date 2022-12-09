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

import com.google.common.collect.ImmutableMap;
import com.google.copybara.onboard.core.CannotConvertException;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.template.ConfigGenerator;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InputsTest {

  private StarlarkConverter starlarkConverter;

  @Before
  public void setUp() throws Exception {
    TestingConsole console = new TestingConsole();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console).setOutputRootToTmpDir();
    optionsBuilder.setForce(true);
    SkylarkTestExecutor starlark = new SkylarkTestExecutor(optionsBuilder);
    starlarkConverter = new StarlarkConverter(starlark.createModuleSet(), console);
  }

  @Test
  public void originGlobTest() throws CannotConvertException {
    InputProviderResolver resolver =
        new InputProviderResolver() {
          @Override
          public <T> T resolve(Input<T> input)
              throws CannotProvideException {
            throw new CannotProvideException("Don't call me");
          }

          @Override
          public <T> Optional<T> resolveOptional(Input<T> input) throws InterruptedException {
            return InputProviderResolver.super.resolveOptional(input);
          }

          @Override
          public ImmutableMap<String, ConfigGenerator> getGenerators() {
            return InputProviderResolver.super.getGenerators();
          }

          @Override
          public <T> T parseStarlark(String starlark, Class<T> type) throws CannotConvertException {
            return (T) starlarkConverter.convert(starlark, this);
          }
        };
    Glob value = Inputs.ORIGIN_GLOB.convert("glob([\"foo\"])", resolver);
    Path root = Paths.get("/");
    assertThat(value.relativeTo(root).matches(root.resolve("foo"))).isTrue();
    assertThat(value.relativeTo(root).matches(root.resolve("bar"))).isFalse();
  }
}
