/*
 * Copyright (C) 2025 Google LLC.
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
import static org.mockito.Mockito.mock;

import com.google.copybara.testing.OptionsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubOptionsTest {
  private GitHubOptions githubOptions;

  @Before
  public void setUp() throws Exception {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    githubOptions = new GitHubOptions(optionsBuilder.general, optionsBuilder.git);
  }

  @Test
  public void getGitRepositoryHook_returnsGitHubRepositoryHookInstance() throws Exception {
    GitRepositoryHook hook =
        githubOptions.getGitRepositoryHook(
            new GitRepositoryHook.GitRepositoryData("123", "https://github.com/foo/bar"),
            mock(CredentialFileHandler.class));

    assertThat(hook).isInstanceOf(GitHubRepositoryHook.class);
  }
}
