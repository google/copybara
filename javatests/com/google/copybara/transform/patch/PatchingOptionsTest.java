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

package com.google.copybara.transform.patch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.InsideGitDirException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PatchingOptionsTest {

  private static final int STRIP_SLASHES = 2;
  private static final boolean VERBOSE = true;
  private static final ImmutableList<String> NO_EXCLUDED = ImmutableList.of();
  private final OptionsBuilder options = new OptionsBuilder();

  // Command requires the working dir as a File, and Jimfs does not support Path.toFile()
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  private Path left;
  private Path right;
  private Path destination;

  @Before
  public void setUp() throws Exception {
    // Check if default patch in path is >= 2.7 otherwise default to the
    // one in /usr/bin/patch. This is used internally.
    if (options.patch.getPatchVersion(options.patch.patchBin).isTooOld()) {
      options.patch.patchBin = "/usr/bin/patch";
    }
    Path rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
    destination = createDir(rootPath, "destination");
  }

  @Test
  public void testPatchWithGitApply() throws Exception {
    useGitApply();
    checkPatch();
  }

  @Test
  public void testPatch() throws Exception {
    setDefaultPatchImplementation();
    checkPatch();
  }

  @Test
  public void testPatchAlreadyApplied_gitApply() throws Exception {
    useGitApply();
    checkPatchAlreadyApplied("patch does not apply", "patch does not apply");
  }

  @Test
  public void testPatchAlreadyApplied_gnuPatch() throws Exception {
    setDefaultPatchImplementation();
    checkPatchAlreadyApplied("[Rr]eversed .* patch detected", "Unreversed patch detected");
  }

  private void checkPatchAlreadyApplied(String forwardError, String reverseError)
      throws IOException, InsideGitDirException, ValidationException {
    String before = ""
        + "foo\n"
        + "more foo\n"
        + "for foo == foo\n"
        + "   fooooo\n";

    String after = ""
        + "foo\n"
        + "some edit\n"
        + "more foo\n"
        + "if foo == foo\n"
        + "   fooooo\n";

    writeFile(left, "file.txt", before);
    writeFile(right, "file.txt", after);
    writeFile(destination, "file.txt", before);

    byte[] patch = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, patch, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(destination).containsFile("file.txt", after);

    try {
      // Re-applying the patch should fail, not revert...
      runPatch(destination, patch, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);
      fail();
    } catch (IOException e) {
      assertThat(e).hasMessageThat().containsMatch(forwardError);
    }
    // Patch was really not applied
    assertThatPath(destination).containsFile("file.txt", after);

    runPatch(destination, patch, /*reverse=*/ true, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(destination).containsFile("file.txt", before);

    try {
      runPatch(destination, patch, /*reverse=*/ true, STRIP_SLASHES, NO_EXCLUDED);
      fail();
    } catch (IOException e) {
      assertThat(e).hasMessageThat().containsMatch(reverseError);
    }
    assertThatPath(destination).containsFile("file.txt", before);
  }

  @Test
  public void testPatchSkipVersionCheck() throws Exception {
    forceUseGnuPatch();
    Map<String, String> env = new HashMap<>(options.general.getEnvironment());
    env.put("GIT_EXEC_PATH", "you shouldn't call git!");
    options.setEnvironment(env);
    writeFile(left, "file1.txt", "old text\n");
    writeFile(right, "file1.txt", "new text\n");
    writeFile(destination, "file1.txt", "old text\n");

    // A very simple patch that we know it works even for old patch versions
    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(destination)
        .containsFile("file1.txt", "new text\n")
        .containsNoMoreFiles();
  }

  private void checkPatch() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "new foo");
    writeFile(right, "c/file3.txt", "bar");
    writeFile(destination, "file1.txt", "foo");
    writeFile(destination, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(left)
        .containsFile("file1.txt", "foo")
        .containsFile("b/file2.txt", "bar")
        .containsNoMoreFiles();
    assertThatPath(right)
        .containsFile("file1.txt", "new foo")
        .containsFile("c/file3.txt", "bar")
        .containsNoMoreFiles();
    assertThatPath(destination)
        .containsFile("file1.txt", "new foo")
        .containsFile("c/file3.txt", "bar")
        .containsNoMoreFiles();

    runPatch(destination, diffContents, /*reverse=*/ true, STRIP_SLASHES, NO_EXCLUDED);
    assertThatPath(destination)
        .containsFile("file1.txt", "foo")
        .containsFile("b/file2.txt", "bar")
        .containsNoMoreFiles();
  }

  private void runPatch(Path root, byte[] diffContents, boolean reverse, int stripSlashes,
      ImmutableList<String> excluded)
      throws InsideGitDirException, IOException, ValidationException {
    options.build().get(PatchingOptions.class).patch(root, diffContents, excluded, stripSlashes,
        reverse, /*gitDir=*/null);
  }

  @Test
  public void applyExcluded() throws Exception {
    // This is the default: It will use 'git apply' since excludes are not supported by GNU Patch.
    setDefaultPatchImplementation();
    checkApplyExcluded();
  }

  @Test
  public void applyExcludedWithForcePatch() throws Exception {
    forceUseGnuPatch();
    ValidationException e = assertThrows(ValidationException.class, () -> checkApplyExcluded());
    assertThat(e)
        .hasMessageThat()
        .contains(
            "--patch-skip-version-check is incompatible with patch transformations that uses"
                + " excluded paths");
  }

  @Test
  public void applyExcludedWithGitApply() throws Exception {
    useGitApply();
    checkApplyExcluded();
  }

  private void checkApplyExcluded() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "excluded/file2.txt", "bar");
    writeFile(left, "other_excluded/file3.txt", "bar");
    writeFile(right, "file1.txt", "new foo");
    writeFile(right, "excluded/file2.txt", "new bar");
    writeFile(right, "other_excluded/file3.txt", "new bar");
    writeFile(destination, "file1.txt", "foo");
    writeFile(destination, "excluded/file2.txt", "bar");
    writeFile(destination, "other_excluded/file3.txt", "bar");
    ImmutableList<String> excludedPaths = ImmutableList.of("excluded/*", "other_excluded/*");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, excludedPaths);

    assertThatPath(left)
        .containsFile("file1.txt", "foo")
        .containsFile("excluded/file2.txt", "bar")
        .containsFile("other_excluded/file3.txt", "bar")
        .containsNoMoreFiles();
    assertThatPath(right)
        .containsFile("file1.txt", "new foo")
        .containsFile("excluded/file2.txt", "new bar")
        .containsFile("other_excluded/file3.txt", "new bar")
        .containsNoMoreFiles();
    assertThatPath(destination)
        .containsFile("file1.txt", "new foo")
        .containsFile("excluded/file2.txt", "bar")
        .containsFile("other_excluded/file3.txt", "bar")
        .containsNoMoreFiles();

    runPatch(destination, diffContents, /*reverse=*/ true, STRIP_SLASHES, excludedPaths);

    assertThatPath(destination)
        .containsFile("file1.txt", "foo")
        .containsFile("excluded/file2.txt", "bar")
        .containsFile("other_excluded/file3.txt", "bar")
        .containsNoMoreFiles();
  }

  /**
   * Tests the situation where the destination is ahead of the baseline, and the diff between the
   * baseline and the destination can be applied without conflicts to the destination.
   */
  @Test
  public void applyDifferentBaseline() throws Exception {
    setDefaultPatchImplementation();
    checkApplyDifferentBaseline();
  }

  @Test
  public void applyDifferentBaseline_gitApply() throws Exception {
    useGitApply();
    checkApplyDifferentBaseline();
  }

  private void checkApplyDifferentBaseline() throws Exception {
    writeFile(left, "file1.txt", "foo\n"
        + "more foo\n");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(left, "file5.txt", "mmm\n"
        + "zzzzzzzzz\n"
        + "zzzzzzzzzzzzzz\n"
        + "zzzzzzzzzzzzzzzzzzzz\n"
        + "bar\n"
        + "foo\n"
        + "bar");
    writeFile(right, "file1.txt", "new foo\n"
        + "more foo\n");
    writeFile(right, "c/file3.txt", "bar");
    writeFile(right, "file5.txt", "mmm\n"
        + "zzzzzzzzz\n"
        + "zzzzzzzzzzzzzz\n"
        + "zzzzzzzzzzzzzzzzzzzz\n"
        + "bar\n"
        + "xxx\n"
        + "bar");
    writeFile(destination, "file1.txt", "foo\n"
        + "more foo\n"
        + "added foo\n");
    writeFile(destination, "b/file2.txt", "bar");
    writeFile(destination, "c/file4.txt", "bar");
    writeFile(destination, "file5.txt", "vvv\n"
        + "zzzzzzzzz\n"
        + "zzzzzzzzzzzzzz\n"
        + "zzzzzzzzzzzzzzzzzzzz\n"
        + "bar\n"
        + "foo\n"
        + "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(destination)
        .containsFile("file1.txt", "new foo\n"
            + "more foo\n"
            + "added foo\n")
        .containsFile("c/file3.txt", "bar")
        .containsFile("c/file4.txt", "bar")
        .containsFile("file5.txt", "vvv\n"
            + "zzzzzzzzz\n"
            + "zzzzzzzzzzzzzz\n"
            + "zzzzzzzzzzzzzzzzzzzz\n"
            + "bar\n"
            + "xxx\n"
            + "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void applyEmptyDiff() throws Exception {
    setDefaultPatchImplementation();
    checkApplyEmptyDiff();
  }

  @Test
  public void applyEmptyDiffWithGitApply() throws Exception {
    useGitApply();
    checkApplyEmptyDiff();
  }

  @Test
  public void applyEmptyDiffForceGnuPatch() throws Exception {
    forceUseGnuPatch();
    checkApplyEmptyDiff();
  }

  private void checkApplyEmptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    runPatch(left, /*diffContents=*/ new byte[]{}, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(left)
        .containsFile("file1.txt", "foo")
        .containsFile("b/file2.txt", "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void patchFail() throws Exception {
    setDefaultPatchImplementation();
    checkFail("hunk FAILED");
  }

  @Test
  public void patchFail_gitApply() throws Exception {
    useGitApply();
    checkFail("error: patch failed: file1.txt:1\n"
        + "error: file1.txt: patch does not apply");
  }

  private void checkFail(String errorMsg) throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(right, "file1.txt", "new foo\n");
    writeFile(destination, "file1.txt", "foo\nmore foo\n");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    try {
      runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);
      fail();
    } catch (IOException e) {
      assertThat(e).hasMessageThat().contains(errorMsg);
    }
  }

  // Regression test for http://b/112639930
  @Test
  public void testApplyFileDoesntMatch() throws Exception {
    setDefaultPatchImplementation();

    writeFile(
        left,
        "file.txt",
        "mmm\n"
            + "zzzzzzzzz\n"
            + "zzzzzzzzzzzzzz\n"
            + "zzzzzzzzzzzzzzzzzzzz\n"
            + "bar\n"
            + "foo\n"
            + "bar");
    writeFile(
        right,
        "file.txt",
        "mmm\n"
            + "zzzzzzzzz\n"
            + "zzzzzzzzzzzzzz\n"
            + "zzzzzzzzzzzzzzzzzzzz\n"
            + "bar\n"
            + "xxx\n"
            + "bar");
    writeFile(
        destination,
        "file.txt",
        "" // Does not have the first line as left and right. Patch won't match the file entirely.
            + "zzzzzzzzz\n"
            + "zzzzzzzzzzzzzz\n"
            + "zzzzzzzzzzzzzzzzzzzz\n"
            + "bar\n"
            + "foo\n"
            + "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    runPatch(destination, diffContents, /*reverse=*/ false, STRIP_SLASHES, NO_EXCLUDED);

    assertThatPath(destination)
        .containsFile(
            "file.txt",
            "zzzzzzzzz\n"
                + "zzzzzzzzzzzzzz\n"
                + "zzzzzzzzzzzzzzzzzzzz\n"
                + "bar\n"
                + "xxx\n"
                + "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void negativeSlashesFail() throws Exception {
    try {
      runPatch(destination, new byte[1], /*reverse=*/ false, /*stripSlashes=*/ -1, NO_EXCLUDED);
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("stripSlashes must be >= 0");
    }
  }

  private Path createDir(Path parent, String name) throws IOException {
    Path path = parent.resolve(name);
    Files.createDirectories(path);
    return path;
  }

  private void writeFile(Path parent, String fileName, String fileContents) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.write(parent.resolve(filePath), fileContents.getBytes(StandardCharsets.UTF_8));
  }

  private void useGitApply() {
    options.patch.useGitApply = true;
    options.patch.skipVersionCheck = false;
  }

  private void setDefaultPatchImplementation() throws Exception {
    maybeSkipForOSX();
    options.patch.useGitApply = false;
    options.patch.skipVersionCheck = false;
  }

  private void forceUseGnuPatch() {
    options.patch.useGitApply = false;
    options.patch.skipVersionCheck = true;
  }

  /**
   * OSX comes with a very old version of GNU Patch. Skip the test in OSX if the default old
   * version is found.
   */
  private void maybeSkipForOSX() throws Exception {
    Assume.assumeFalse("Mac OS X".equals(StandardSystemProperty.OS_NAME.value())
        && options.patch.getPatchVersion(options.patch.patchBin).isTooOld());
  }
}
