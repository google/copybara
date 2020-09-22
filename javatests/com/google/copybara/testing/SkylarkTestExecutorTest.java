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

package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SkylarkTestExecutorTest {

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options)
        .withStaticModules(ImmutableSet.of(DummyModule.class));
  }

  @Test
  public void addExtraConfigFile() throws Exception {
    skylark.addConfigFile("foo_extra", "foo content 42");
    String content = skylark.eval("c", "c = dummy.read_foo_extra()");
    assertThat(content).isEqualTo("foo content 42");
  }

  @SuppressWarnings("unused")
  @StarlarkBuiltin(
      name = "dummy",
      doc = "For testing.",
      documented = false)
  public static final class DummyModule implements LabelsAwareModule, StarlarkValue {
    private ConfigFile configFile;

    @Override
    public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
      this.configFile = currentConfigFile;
    }

    @StarlarkMethod(name = "read_foo_extra", doc = "Read foo_extra", documented = false)
    public String readFooExtra() {
      try {
        return configFile.resolve("foo_extra").readContent();
      } catch (CannotResolveLabel | IOException inconceivable) {
        throw new AssertionError(inconceivable);
      }
    }
  }
}
