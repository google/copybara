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
public class GitHubTriggerTest {

  private SkylarkTestExecutor skylarkTestExecutor;

  @Before
  public void setup() throws Exception {
    TestingConsole console = new TestingConsole();
    OptionsBuilder options = new OptionsBuilder();
    options.setConsole(console).setOutputRootToTmpDir();
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("badword"));
    skylarkTestExecutor = new SkylarkTestExecutor(options);
  }

  @Test
  public void testParsing() throws Exception {
    GitHubTrigger gitHubTrigger =
        skylarkTestExecutor.eval(
            "e", "e = git.github_trigger("
                + "url = 'https://github.com/google/example',"
                + "events = ['STATUS', 'ISSUES'])");
    assertThat(gitHubTrigger.describe()).containsExactly(
        "type", "github_trigger",
        "url", "https://github.com/google/example",
        "events", "STATUS",
        "events", "ISSUES").inOrder();

    assertThat(gitHubTrigger.getEndpoint().describe())
        .containsExactly("type", "github_api", "url", "https://github.com/google/example");
  }

  @Test
  public void testInvalidEvent() {
    skylarkTestExecutor.evalFails(
        "git.github_trigger("
            + "url = 'https://github.com/google/example',"
            + "events = ['LABEL'])",
        "LABEL is not a valid value. Values: \\[ISSUES, ISSUE_COMMENT, PULL_REQUEST,"
            + " PULL_REQUEST_REVIEW_COMMENT, PUSH, STATUS, CHECK_RUN\\]");
  }

  @Test
  public void testEmptyEvents() {
    skylarkTestExecutor.evalFails(
        "git.github_trigger("
            + "url = 'https://github.com/google/example',"
            + "events = [])",
        "events cannot be empty");
  }

  @Test
  public void testParsingWithChecker() throws Exception {
    GitHubTrigger gitHubTrigger =
        skylarkTestExecutor.eval(
            "e",
            "e = git.github_trigger(\n"
                + "  url = 'https://github.com/google/example', \n"
                + "  checker = testing.dummy_checker(),\n"
                + "  events = ['STATUS', 'ISSUES'],\n"
                + ")\n");

    assertThat(gitHubTrigger.describe()).containsExactly(
        "type", "github_trigger",
        "url", "https://github.com/google/example",
        "events", "STATUS",
        "events", "ISSUES").inOrder();

    assertThat(gitHubTrigger.getEndpoint().describe())
        .containsExactly("type", "github_api", "url", "https://github.com/google/example");
  }

  @Test
  public void testParsingEmptyUrl() {
    skylarkTestExecutor.evalFails("git.github_trigger(url = '')", "Invalid empty field 'url'");
  }
}
