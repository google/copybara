/*
 * Copyright (C) 2025 Google LLC
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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GitHubRepositoryHookTest {
  private OptionsBuilder builder;
  private GitTestUtil gitTestUtil;
  private TestingConsole console;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    builder =
        new OptionsBuilder()
            .setOutputRootToTmpDir()
            .setWorkdirToRealTempDir()
            .setConsole(console)
            .setEnvironment(GitTestUtil.getGitEnv().getEnvironment());

    gitTestUtil = new GitTestUtil(builder);
    gitTestUtil.mockRemoteGitRepos();
  }

  private GitRepositoryHook getGitRepositoryHookUnderTest(
      GitRepositoryHook.GitRepositoryData gitRepositoryData, GitHubOptions gitHubOptions)
      throws Exception {
    return new GitHubRepositoryHook(
        gitRepositoryData, gitHubOptions, mock(CredentialFileHandler.class));
  }

  @Test
  public void constructor_withNullValues_throwsNullPointerException() throws Exception {
    assertThrows(NullPointerException.class, () -> getGitRepositoryHookUnderTest(null, null));
  }

  @Test
  public void beforeCheckout_withMatchingRepositoryId_withEmptyChangeList() throws Exception {
    GitRepositoryHook underTest =
        getGitRepositoryHookUnderTest(
            new GitRepositoryHook.GitRepositoryData(
                "123456789", "https://github.com/google/copybara"),
            builder.github);
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/copybara"),
        GitTestUtil.mockResponse(
            """
            {
              "default_branch": "test_default_branch",
              "id": 123456789
            }
            """));
    underTest.beforeCheckout();
  }

  @Test
  public void beforeCheckout_withMismatchedRepositoryId_withCheckoutError() throws Exception {
    GitRepositoryHook underTest =
        getGitRepositoryHookUnderTest(
            new GitRepositoryHook.GitRepositoryData(
                "123456789", "https://github.com/google/copybara"),
            builder.github);
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/copybara"),
        GitTestUtil.mockResponse(
            """
            {
              "default_branch": "test_default_branch",
              "id": 1
            }
            """));
    ValidationException e = assertThrows(ValidationException.class, underTest::beforeCheckout);
    assertThat(e).hasMessageThat().contains("Expected repository id 123456789 but got repo id 1");
  }

  @Test
  public void beforeCheckout_withUnsetRepositoryId_withNoError() throws Exception {
    GitRepositoryHook underTest =
        getGitRepositoryHookUnderTest(
            new GitRepositoryHook.GitRepositoryData(
                /* id= */ null, "https://github.com/google/copybara"),
            builder.github);
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/copybara"),
        GitTestUtil.mockResponse(
            """
            {
              "default_branch": "test_default_branch",
              "id": 1
            }
            """));
    underTest.beforeCheckout();
  }
}
