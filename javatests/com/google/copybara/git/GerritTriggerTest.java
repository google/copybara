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

import com.google.common.collect.ImmutableSet;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritTriggerTest {

  private SkylarkTestExecutor skylarkTestExecutor;

  @Before
  public void setup() throws Exception {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console).setOutputRootToTmpDir();
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    skylarkTestExecutor =
        new SkylarkTestExecutor(options);
  }

  @Test
  public void testParsing() throws Exception {
    GerritTrigger gerritTrigger =
        skylarkTestExecutor.eval(
            "e", "e = git.gerrit_trigger(url = 'https://test.googlesource.com/example')");
    assertThat(gerritTrigger.describe())
        .containsExactly("type", "gerrit_trigger", "url", "https://test.googlesource.com/example");
    assertThat(gerritTrigger.getEndpoint().describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    GerritTrigger gerritTrigger =
        skylarkTestExecutor.eval(
            "e",
            "e = git.gerrit_trigger(\n"
                + "url = 'https://test.googlesource.com/example', \n"
                + "checker = testing.dummy_checker(),\n"
                + ")\n");

    assertThat(gerritTrigger.describe())
        .containsExactly("type", "gerrit_trigger", "url", "https://test.googlesource.com/example");
    assertThat(gerritTrigger.getEndpoint().describe())
        .containsExactly("type", "gerrit_api", "url", "https://test.googlesource.com/example");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylarkTestExecutor.evalFails("git.gerrit_trigger(url = '')", "Invalid empty field 'url'");
  }
}
