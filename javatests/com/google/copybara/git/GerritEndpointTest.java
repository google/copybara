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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.Core;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritEndpointTest {

  private static final ImmutableSet<Class<?>> MODULES =
      ImmutableSet.of(Core.class, TestingModule.class, GitModule.class);

  private SkylarkTestExecutor skylarkTestExecutor;
  private TestingConsole console;
  private OptionsBuilder options;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    console = new TestingConsole();
    options = new OptionsBuilder();
    options.setConsole(console)
        .setOutputRootToTmpDir();

    skylarkTestExecutor =
        new SkylarkTestExecutor(options, MODULES.toArray(new Class<?>[MODULES.size()]));
  }

  @Test
  public void testParsing() throws Exception {
    GerritEndpoint gerritEndpoint =
        skylarkTestExecutor.eval(
            "e",
            "e = git.gerrit_api(url = 'https://test.googlesource.com/example'))");
    assertThat(gerritEndpoint.describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylarkTestExecutor.evalFails("git.gerrit_api(url = '')))", "Invalid empty field 'url'");
  }
}
