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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitEnvironmentTest {

  private static final Map<String, String> environment =
      ImmutableMap.<String, String>builder().put("FOO", "123").put("BAR", "456").build();

  @Test
  public void testEnvironmentReturned() {
    assertThat(new GitEnvironment(environment, /*noGitPrompt*/ false).getEnvironment())
        .containsExactlyEntriesIn(environment);
  }

  @Test
  public void testNoGitPrompt() {
    assertThat(new GitEnvironment(environment, /*noGitPrompt*/ true).getEnvironment())
        .containsEntry("GIT_TERMINAL_PROMPT", "0");
  }

  @Test
  public void testGitBinaryResolution() throws Exception {
    assertThat(new GitEnvironment(ImmutableMap.of()).resolveGitBinary()).isEqualTo("git");
    assertThat(
            new GitEnvironment(ImmutableMap.of("GIT_EXEC_PATH", "/some/path")).resolveGitBinary())
        .isEqualTo("/some/path/git");
  }
}
