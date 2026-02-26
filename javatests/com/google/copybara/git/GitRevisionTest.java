/*
 * Copyright (C) 2026 Google LLC
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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Strings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRevisionTest {

  private GitRepository repository;
  private Path workdir;
  private Path gitDir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    gitDir = Files.createTempDirectory("gitdir");
    repository =
        GitRepository.newBareRepo(
                gitDir, getGitEnv(), /* verbose= */ true, DEFAULT_TIMEOUT, /* noVerify= */ false)
            .withWorkTree(workdir);
    repository.init();
  }

  @Test
  public void testGitRevisionLabels() throws Exception {
    Files.write(workdir.resolve("foo.txt"), new byte[] {});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
    GitRevision revision = repository.getHeadRef();
    String hash = revision.getHash();
    assertThat(revision.associatedLabels()).containsEntry("GIT_SHA1", hash);
    assertThat(revision.associatedLabels()).containsEntry("GIT_SHORT_SHA1", hash.substring(0, 7));
    assertThat(revision.associatedLabel("GIT_SHA1")).containsExactly(hash);
    assertThat(revision.associatedLabel("GIT_SHORT_SHA1")).containsExactly(hash.substring(0, 7));
  }

  @Test
  public void testGitRevisionLabelsSha256() {
    String sha256 = Strings.repeat("a", 64);
    GitRevision unused = new GitRevision(repository, sha256);
  }

  @Test
  public void testGitRevisionInvalidHashLength() {
    assertThrows(
        IllegalArgumentException.class, () -> new GitRevision(repository, Strings.repeat("a", 39)));
    assertThrows(
        IllegalArgumentException.class, () -> new GitRevision(repository, Strings.repeat("a", 65)));
  }
}
