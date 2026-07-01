/*
 * Copyright (C) 2018 Google Inc.
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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.ModuleSupplier;
import com.google.copybara.Options;
import com.google.copybara.git.GitHubOptions;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SemicolonListConverterTest {
  private Options options;
  private JCommander jCommander;

  @Before
  public void setUp() throws Exception {
    ImmutableMap<String, String> envWithHome =
        ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString());
    ModuleSupplier moduleSupplier =
        new ModuleSupplier(envWithHome, FileSystems.getDefault(), new TestingConsole());

    options = moduleSupplier.create().getOptions();
    jCommander = new JCommander(options.getAll());
  }

  @Test
  public void testJCommander_integerList() throws Exception {
    jCommander.parse("--allstar-app-ids", "100;101");
    assertThat(options.get(GitHubOptions.class).allStarAppIds)
        .isEqualTo(ImmutableList.of(100, 101));
  }

  @Test
  public void testJCommander_integerListWithNonZeroValidator() throws Exception {
    ParameterException e =
        assertThrows(
            ParameterException.class, () -> jCommander.parse("--allstar-app-ids", "100;-101"));
    assertThat(e.getMessage())
        .isEqualTo("Parameter --allstar-app-ids elements should be greater than zero (found -101)");
  }
}
