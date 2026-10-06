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

package com.google.copybara.jj;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.jj.JjRepository.CommitId;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class JjRepositoryTest {

  private Path repoDir;
  private Path sourceDir;
  private Path targetDir;
  private JjRepository repo;

  @Before
  public void setUp() throws Exception {
    repoDir = Files.createTempDirectory("jj_repo");
    sourceDir = Files.createTempDirectory("source");
    targetDir = Files.createTempDirectory("target_root").resolve("target");
    repo =
        new JjRepository(
            repoDir,
            setUpJjBin(),
            getJjEnv(),
            /* verbose= */ true,
            DEFAULT_TIMEOUT);
  }

  static ImmutableMap<String, String> getJjEnv() throws IOException {
    Map<String, String> values = new HashMap<>(System.getenv());
    values.put("HOME", Files.createTempDirectory("jj_home").toString());
    values.put("JJ_USER", "Copybara");
    values.put("JJ_EMAIL", "copybara@google.com");
    return ImmutableMap.copyOf(values);
  }

  static String setUpJjBin() {
    Path runtime =
        Paths.get(System.getenv("TEST_SRCDIR"))
            .resolve(System.getenv("TEST_WORKSPACE"))
            .resolve("devtools/jj/client/jj");
    if (Files.isExecutable(runtime)) {
      return runtime.toAbsolutePath().toString();
    }
    try {
      new Command(new String[] {"jj", "--version"}).execute();
    } catch (CommandException e) {
      Assume.assumeTrue("jj binary not found on PATH", false);
    }
    return "jj";
  }

  @Test
  public void commitId_validId_succeeds() {
    CommitId id = new CommitId("abc123def456");

    assertThat(id.id()).isEqualTo("abc123def456");
  }

  @Test
  public void commitId_nullId_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new CommitId(null));
  }

  @Test
  public void commitId_blankId_throwsIllegalArgumentException(
      @TestParameter({"", " ", "   ", "\t\n"}) String blankId) {
    assertThrows(IllegalArgumentException.class, () -> new CommitId(blankId));
  }

  @Test
  public void init_createsNonColocatedRepository() throws Exception {
    repo.init();

    assertThat(Files.isDirectory(repoDir.resolve(".jj/repo"))).isTrue();
    assertThat(Files.exists(repoDir.resolve(".git"))).isFalse();
  }

  @Test
  public void initGitColocated_createsColocatedRepository() throws Exception {
    repo.initGitColocated();

    assertThat(Files.isDirectory(repoDir.resolve(".jj/repo"))).isTrue();
    assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
  }
  @Test
  public void simpleCommand_setsAndReadsRepoConfig() throws Exception {
    repo.init();
    repo.simpleCommand("config", "set", "--repo", "ui.default-command", "log");

    String value =
        repo.simpleCommand(
                "--no-pager", "--color=never", "config", "get", "ui.default-command")
            .getStdout()
            .trim();

    assertThat(value).isEqualTo("log");
  }

  @Test
  public void commitTreeAndCheckout_nonExistentTargetDir_materializesFilesWithoutMetadata()
      throws Exception {
    repo.init();
    writeFile(sourceDir, "a.txt", "v1\n");
    writeFile(sourceDir, "sub/b.txt", "sub-v1\n");
    CommitId commitId = repo.commitTree(sourceDir, "commit 1");

    repo.checkout(commitId, targetDir);

    assertThatPath(targetDir)
        .containsFile("a.txt", "v1\n")
        .containsFile("sub/b.txt", "sub-v1\n")
        .containsNoMoreFiles();
  }

  @Test
  public void commitTreeAndCheckout_gitColocatedRepo_preservesAndExcludesGitMetadata()
      throws Exception {
    repo.initGitColocated();
    writeFile(sourceDir, "a.txt", "v1\n");
    CommitId c1 = repo.commitTree(sourceDir, "commit 1");
    writeFile(sourceDir, "a.txt", "v2\n");
    CommitId c2 = repo.commitTree(sourceDir, "commit 2");

    repo.checkout(c1, targetDir);
    repo.checkout(c2, targetDir);

    assertThatPath(targetDir).containsFile("a.txt", "v2\n").containsNoMoreFiles();
    assertThat(Files.isDirectory(repoDir.resolve(".jj/repo"))).isTrue();
    assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
  }

  @Test
  public void commitTreeAndCheckout_overExistingTree_prunesStaleFilesAndReplacesDirectories()
      throws Exception {
    repo.init();
    writeFile(sourceDir, "a.txt", "v1\n");
    writeFile(sourceDir, "sub/b.txt", "sub-v1\n");
    CommitId c1 = repo.commitTree(sourceDir, "commit 1");

    // Replace "sub" directory with a regular file "sub" and add "c.txt".
    Files.delete(sourceDir.resolve("sub/b.txt"));
    Files.delete(sourceDir.resolve("sub"));
    writeFile(sourceDir, "a.txt", "v2\n");
    writeFile(sourceDir, "sub", "sub-as-file\n");
    writeFile(sourceDir, "c.txt", "extra\n");
    CommitId c2 = repo.commitTree(sourceDir, "commit 2");
    repo.checkout(c2, targetDir);

    repo.checkout(c1, targetDir);

    assertThatPath(targetDir)
        .containsFile("a.txt", "v1\n")
        .containsFile("sub/b.txt", "sub-v1\n")
        .containsNoMoreFiles();
  }

  @Test
  public void commitTree_withParent_branchesFromGivenParent() throws Exception {
    repo.init();
    writeFile(sourceDir, "a.txt", "v1\n");
    CommitId c1 = repo.commitTree(sourceDir, "commit 1");
    writeFile(sourceDir, "a.txt", "v2\n");
    repo.commitTree(sourceDir, "commit 2");
    writeFile(sourceDir, "a.txt", "v3\n");

    CommitId c3 = repo.commitTree(sourceDir, "commit 3", /* parent= */ c1);

    String c3Parent =
        repo.simpleCommand("log", "--no-graph", "-r", c3.id() + "-", "-T", "commit_id")
            .getStdout()
            .trim();
    assertThat(c3Parent).isEqualTo(c1.id());
    repo.checkout(c3, targetDir);
    assertThatPath(targetDir).containsFile("a.txt", "v3\n").containsNoMoreFiles();
  }

  @Test
  public void checkout_unknownCommitId_throwsRepoException() throws Exception {
    repo.init();

    var thrown =
        assertThrows(RepoException.class, () -> repo.checkout(new CommitId("deadbeef"), targetDir));

    assertThat(thrown).hasMessageThat().contains("deadbeef");
  }

  private static void writeFile(Path dir, String relativePath, String content) throws IOException {
    Path file = dir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
