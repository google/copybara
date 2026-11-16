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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.jj.JjRepository.CommitId;
import com.google.copybara.jj.JjRepository.RebaseResult;
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
  public void rebaseResult_validInputs_succeeds(@TestParameter boolean hasConflicts) {
    CommitId tip = new CommitId("abc123def456");

    RebaseResult result = new RebaseResult(hasConflicts, tip);

    assertThat(result.hasConflicts()).isEqualTo(hasConflicts);
    assertThat(result.tipCommitId()).isEqualTo(tip);
  }

  @Test
  public void rebaseResult_nullTipCommitId_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new RebaseResult(false, null));
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
  public void rebase_cleanThreeWayMerge_mergesChangesWithoutConflict() throws Exception {
    repo.init();
    writeFile(sourceDir, "file.txt", "line1\nline2\nline3\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");

    writeFile(sourceDir, "file.txt", "line1\nline2\npatched3\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);

    writeFile(sourceDir, "file.txt", "imported1\nline2\nline3\n");
    CommitId imported = repo.commitTree(sourceDir, "imported", baseline);

    RebaseResult result = repo.rebase(patch1, patch1, imported);
    repo.checkout(result.tipCommitId(), targetDir);

    assertThat(result.hasConflicts()).isFalse();
    assertThatPath(targetDir)
        .containsFile("file.txt", "imported1\nline2\npatched3\n")
        .containsNoMoreFiles();
  }

  @Test
  public void rebase_multiCommitPatchStack_returnsNewestTipCommitId() throws Exception {
    // Upstream and each patch change a different file, so every change merges cleanly and the
    // tip must contain all of them.
    repo.init();
    writeFile(sourceDir, "upstream.txt", "base\n");
    writeFile(sourceDir, "patch1.txt", "base\n");
    writeFile(sourceDir, "patch2.txt", "base\n");
    writeFile(sourceDir, "patch3.txt", "base\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");

    writeFile(sourceDir, "patch1.txt", "patched\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);

    writeFile(sourceDir, "patch2.txt", "patched\n");
    repo.commitTree(sourceDir, "patch 2");

    writeFile(sourceDir, "patch3.txt", "patched\n");
    CommitId patch3 = repo.commitTree(sourceDir, "patch 3");

    writeFile(sourceDir, "upstream.txt", "imported\n");
    writeFile(sourceDir, "patch1.txt", "base\n");
    writeFile(sourceDir, "patch2.txt", "base\n");
    writeFile(sourceDir, "patch3.txt", "base\n");
    CommitId imported = repo.commitTree(sourceDir, "imported", baseline);

    RebaseResult result = repo.rebase(patch1, patch3, imported);
    repo.checkout(result.tipCommitId(), targetDir);

    assertThat(result.hasConflicts()).isFalse();
    assertThatPath(targetDir)
        .containsFile("upstream.txt", "imported\n")
        .containsFile("patch1.txt", "patched\n")
        .containsFile("patch2.txt", "patched\n")
        .containsFile("patch3.txt", "patched\n")
        .containsNoMoreFiles();
  }

  @Test
  public void rebase_conflictInIntermediateCommit_setsHasConflictsTrue() throws Exception {
    repo.init();
    writeFile(sourceDir, "foo.txt", "base\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");

    // Patch 1 conflicts with imported ("patch1" vs "imported"), while Patch 2 overwrites foo.txt
    // with "imported" so the tip commit is conflict-free while Patch 1 remains conflicted.
    writeFile(sourceDir, "foo.txt", "patch1\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);

    writeFile(sourceDir, "foo.txt", "imported\n");
    CommitId patch2 = repo.commitTree(sourceDir, "patch 2");

    writeFile(sourceDir, "foo.txt", "imported\n");
    CommitId imported = repo.commitTree(sourceDir, "imported", baseline);

    RebaseResult result = repo.rebase(patch1, patch2, imported);

    assertThat(result.hasConflicts()).isTrue();
  }

  @Test
  public void rebaseOnto_cleanMerge_writesMergedTipIntoDir() throws Exception {
    repo.init();
    writeFile(sourceDir, "file.txt", "line1\nline2\nline3\n");
    writeFile(sourceDir, "removed_upstream.txt", "old\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "file.txt", "line1\nline2\npatched3\n");
    writeFile(sourceDir, "added_by_patch.txt", "patch\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "file.txt", "imported1\nline2\nline3\n");

    RebaseResult result = repo.rebaseOnto(upstreamDir, "imported", patch1, patch1);

    assertThat(result.hasConflicts()).isFalse();
    assertThatPath(upstreamDir)
        .containsFile("file.txt", "imported1\nline2\npatched3\n")
        .containsFile("added_by_patch.txt", "patch\n")
        .containsNoMoreFiles();
  }

  @Test
  public void rebaseOnto_parentsNewBaseOnStackRootParent() throws Exception {
    repo.init();
    writeFile(sourceDir, "file.txt", "base\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "patch.txt", "patch\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "file.txt", "imported\n");

    RebaseResult result = repo.rebaseOnto(upstreamDir, "imported", patch1, patch1);

    // With a 1-commit stack, the rebased tip's grandparent is the new base's parent.
    String newBaseParent =
        repo.simpleCommand(
                "log", "--no-graph", "-r", result.tipCommitId().id() + "--", "-T", "commit_id")
            .getStdout()
            .trim();
    assertThat(newBaseParent).isEqualTo(baseline.id());
  }

  @Test
  public void rebaseOnto_withConflict_setsHasConflictsAndLeavesDirUnchanged() throws Exception {
    repo.init();
    writeFile(sourceDir, "foo.txt", "base line\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "foo.txt", "patch line\n");
    writeFile(sourceDir, "added_by_patch.txt", "patch\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "foo.txt", "imported line\n");

    RebaseResult result = repo.rebaseOnto(upstreamDir, "imported", patch1, patch1);

    assertThat(result.hasConflicts()).isTrue();
    assertThatPath(upstreamDir).containsFile("foo.txt", "imported line\n").containsNoMoreFiles();
    // The conflicted tip is still in the repository.
    assertThat(logTemplate(result.tipCommitId().id(), "conflict")).isEqualTo("true");
  }

  @Test
  public void rebaseOnto_patchAlreadyUpstream_keepsEmptyTipWithoutConflict() throws Exception {
    repo.init();
    writeFile(sourceDir, "file.txt", "line1\nline2\nline3\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "file.txt", "line1\nline2\npatched3\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "file.txt", "line1\nline2\npatched3\n");

    RebaseResult result = repo.rebaseOnto(upstreamDir, "imported", patch1, patch1);

    assertThat(result.hasConflicts()).isFalse();
    assertThatPath(upstreamDir)
        .containsFile("file.txt", "line1\nline2\npatched3\n")
        .containsNoMoreFiles();
    // jj keeps commits that become empty, so the tip is still the rebased patch.
    assertThat(logTemplate(result.tipCommitId().id(), "description")).isEqualTo("patch 1");
    assertThat(logTemplate(result.tipCommitId().id(), "empty")).isEqualTo("true");
  }

  @Test
  public void rebaseOnto_firstPatchAlreadyUpstream_appliesRemainingPatches() throws Exception {
    repo.init();
    writeFile(sourceDir, "patch1.txt", "base\n");
    writeFile(sourceDir, "patch2.txt", "base\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "patch1.txt", "patched\n");
    CommitId patch1 = repo.commitTree(sourceDir, "patch 1", baseline);
    writeFile(sourceDir, "patch2.txt", "patched\n");
    CommitId patch2 = repo.commitTree(sourceDir, "patch 2");
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "patch1.txt", "patched\n");
    writeFile(upstreamDir, "patch2.txt", "base\n");

    RebaseResult result = repo.rebaseOnto(upstreamDir, "imported", patch1, patch2);

    assertThat(result.hasConflicts()).isFalse();
    assertThatPath(upstreamDir)
        .containsFile("patch1.txt", "patched\n")
        .containsFile("patch2.txt", "patched\n")
        .containsNoMoreFiles();
    String rebasedPatch1 = result.tipCommitId().id() + "-";
    assertThat(logTemplate(rebasedPatch1, "description")).isEqualTo("patch 1");
    assertThat(logTemplate(rebasedPatch1, "empty")).isEqualTo("true");
    assertThat(logTemplate(result.tipCommitId().id(), "empty")).isEqualTo("false");
  }

  @Test
  public void rebaseOnto_stackWithMergeCommit_throwsRepoException() throws Exception {
    repo.init();
    writeFile(sourceDir, "a.txt", "a\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "b.txt", "b\n");
    CommitId left = repo.commitTree(sourceDir, "left", baseline);
    writeFile(sourceDir, "c.txt", "c\n");
    CommitId right = repo.commitTree(sourceDir, "right", baseline);
    repo.simpleCommand("new", left.id(), right.id(), "-m", "merge");
    repo.simpleCommand("new");
    CommitId merge =
        new CommitId(
            repo.simpleCommand("log", "--no-graph", "-r", "@-", "-T", "commit_id")
                .getStdout()
                .trim());
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "a.txt", "imported\n");

    var thrown =
        assertThrows(
            RepoException.class, () -> repo.rebaseOnto(upstreamDir, "imported", left, merge));

    assertThat(thrown).hasMessageThat().contains("merge commits");
  }

  @Test
  public void rebaseOnto_rootNotAncestorOfTip_throwsRepoException() throws Exception {
    repo.init();
    writeFile(sourceDir, "a.txt", "a\n");
    CommitId baseline = repo.commitTree(sourceDir, "baseline");
    writeFile(sourceDir, "b.txt", "b\n");
    CommitId left = repo.commitTree(sourceDir, "left", baseline);
    writeFile(sourceDir, "c.txt", "c\n");
    CommitId right = repo.commitTree(sourceDir, "right", baseline);
    Path upstreamDir = Files.createTempDirectory("upstream");
    writeFile(upstreamDir, "a.txt", "imported\n");

    var thrown =
        assertThrows(
            RepoException.class, () -> repo.rebaseOnto(upstreamDir, "imported", left, right));

    assertThat(thrown).hasMessageThat().contains("not an ancestor");
  }

  @Test
  public void commitTree_gitignoredFile_isCommitted() throws Exception {
    repo.init();
    writeFile(sourceDir, ".gitignore", "gen/\n");
    writeFile(sourceDir, "gen/x.txt", "x\n");

    CommitId commit = repo.commitTree(sourceDir, "commit");

    assertThat(listFiles(commit)).containsExactly(".gitignore", "gen/x.txt");
  }

  @Test
  public void commitTree_fileOverOneMebibyte_isCommitted() throws Exception {
    repo.init();
    writeFile(sourceDir, "big.txt", "x".repeat(1024 * 1024 + 1));

    CommitId commit = repo.commitTree(sourceDir, "commit");

    assertThat(listFiles(commit)).containsExactly("big.txt");
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

  /** Returns the paths in {@code commit}'s tree, as recorded by jj. */
  private ImmutableList<String> listFiles(CommitId commit) throws RepoException {
    return ImmutableList.copyOf(
        Splitter.on('\n')
            .omitEmptyStrings()
            .split(repo.simpleCommand("file", "list", "-r", commit.id()).getStdout()));
  }

  /** Returns the trimmed output of {@code jj log -r revset -T template}. */
  private String logTemplate(String revset, String template) throws RepoException {
    return repo.simpleCommand("log", "--no-graph", "-r", revset, "-T", template).getStdout().trim();
  }

  private static void writeFile(Path dir, String relativePath, String content) throws IOException {
    Path file = dir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
