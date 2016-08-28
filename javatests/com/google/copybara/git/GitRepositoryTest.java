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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRepositoryTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private GitRepository repository;

  @Before
  public void setup() throws Exception {
    this.repository = GitRepository.initScratchRepo(/*verbose=*/true, System.getenv());
  }

  @Test
  public void testCheckoutLocalBranch() throws Exception {
    thrown.expect(CannotFindReferenceException.class);
    thrown.expectMessage("Cannot find reference 'foo'");
    repository.simpleCommand("checkout", "foo");
  }

  @Test
  public void testGitBinaryResolution() throws Exception {
    assertThat(GitRepository.resolveGitBinary(ImmutableMap.<String, String>of()))
        .isEqualTo("git");
    assertThat(GitRepository.resolveGitBinary(ImmutableMap.of("GIT_EXEC_PATH", "/some/path")))
        .isEqualTo("/some/path/git");
  }
}
