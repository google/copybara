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

package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.DiffUtil.DiffFile.Operation;
import com.google.copybara.shell.Command;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DiffUtilTest {

  private static final boolean VERBOSE = true;

  // Command requires the working dir as a File, and Jimfs does not support Path.toFile()
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  Path rootPath;
  private Path left;
  private Path right;
  public Map<String, String> testEnv;

  @Before
  public void setUp() throws Exception {
    rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
    testEnv = System.getenv();
  }

  @Test
  public void pathsAreNotSiblings_diff() throws Exception {
    Path foo = createDir(left, "foo");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> DiffUtil.diff(left, foo, VERBOSE, testEnv));
    assertThat(e).hasMessageThat().contains("Paths 'one' and 'other' must be sibling directories");
  }

  @Test
  public void pathsAreNotSiblings_diffFiles() throws Exception {
    Path foo = createDir(left, "foo");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> DiffUtil.diffFiles(left, foo, VERBOSE, testEnv));
    assertThat(e).hasMessageThat().contains("Paths 'one' and 'other' must be sibling directories");
  }

  @Test
  public void diffWarningDoesNotThrowException() throws Exception {
    //set up environment where git warns of diff containing crlf
    writeFile(left, "file1.txt", "foo\n");
    writeFile(left, "file2.txt", "foo\r\n");
    writeFile(right, "file1.txt", "foo\r\n");
    writeFile(right, "file2.txt", "foo\r");
    Map<String, String> env =
        setDotGitconfigContents(
            """
            [core]
            autocrlf=true
            safecrlf=warn
            """);

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, env);

    assertThat(new String(diffContents, StandardCharsets.UTF_8)).isNotEmpty();
  }

  @Test
  public void runDiffInGitDirectory() throws Exception {
    GitEnvironment gitEnv = new GitEnvironment(testEnv);
    ImmutableList<String> params =
        ImmutableList.of(gitEnv.resolveGitBinary(), "init", rootPath.toString());
    Command cmd =
        new Command(params.toArray(new String[] {}), gitEnv.getEnvironment(), rootPath.toFile());
    new CommandRunner(cmd).withVerbose(true).execute();
    writeFile(left, "file1.txt", "foo");
    writeFile(right, "file1.txt", "foo");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, testEnv);

    assertThat(diffContents).isEmpty();

    assertThat(DiffUtil.diffFiles(left, right, VERBOSE, testEnv)).isEmpty();
    FileUtil.deleteRecursively(rootPath.resolve(".git"));
  }

  @Test
  public void emptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "foo");
    writeFile(right, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, testEnv);

    assertThat(diffContents).isEmpty();

    assertThat(DiffUtil.diffFiles(left, right, VERBOSE, testEnv)).isEmpty();
  }

  @Test
  public void crAtEolDiff() throws Exception {
    writeFile(left, "file1.txt", "foo\r\n");
    writeFile(right, "file1.txt", "foo\n");

    byte[] diffContentsIgnoreCr =
        DiffUtil.diffFileWithIgnoreCrAtEol(left.getParent(), left, right, VERBOSE, testEnv);
    String diffContents =
        new String(DiffUtil.diff(left, right, VERBOSE, testEnv), StandardCharsets.UTF_8);

    assertThat(diffContentsIgnoreCr).isEmpty();
    assertThat(diffContents)
        .isEqualTo(
            """
            diff --git a/left/file1.txt b/right/file1.txt
            index e48b03e..257cc56 100644
            --- a/left/file1.txt
            +++ b/right/file1.txt
            @@ -1 +1 @@
            -foo\r
            +foo
            """);
  }

  @Test
  public void testFilterDiff_excludeAll() throws Exception {
    writeFile(left, "file1.txt", "foo-left");
    writeFile(left, "file2.txt", "bar-left");
    writeFile(right, "file1.txt", "foo-right");
    writeFile(right, "file2.txt", "bar-right");
    byte[] diff = DiffUtil.diff(left, right, VERBOSE, testEnv);

    byte[] filtered = DiffUtil.filterDiff(diff, f -> false);

    assertThat(filtered).isEmpty();
  }

  @Test
  public void testFilterDiff_includeAll() throws Exception {
    writeFile(left, "file1.txt", "foo-left");
    writeFile(left, "file2.txt", "bar-left");
    writeFile(right, "file1.txt", "foo-right");
    writeFile(right, "file2.txt", "bar-right");
    byte[] diff = DiffUtil.diff(left, right, VERBOSE, testEnv);

    byte[] filtered = DiffUtil.filterDiff(diff, f -> true);

    String filteredStr = new String(filtered, StandardCharsets.UTF_8);
    assertThat(filteredStr).contains("diff --git a/left/file1.txt b/right/file1.txt");
    assertThat(filteredStr).contains("diff --git a/left/file2.txt b/right/file2.txt");
  }

  @Test
  public void testFilterDiff_includeOne() throws Exception {
    writeFile(left, "file1.txt", "foo-left");
    writeFile(left, "file2.txt", "bar-left");
    writeFile(right, "file1.txt", "foo-right");
    writeFile(right, "file2.txt", "bar-right");
    byte[] diff = DiffUtil.diff(left, right, VERBOSE, testEnv);

    byte[] filtered = DiffUtil.filterDiff(diff, f -> f.equals("left/file1.txt"));

    String filteredStr = new String(filtered, StandardCharsets.UTF_8);
    assertThat(filteredStr).contains("diff --git a/left/file1.txt b/right/file1.txt");
    assertThat(filteredStr)
        .contains(
            """
            -foo-left
            \\ No newline at end of file
            +foo-right
            \\ No newline at end of file\
            """);
    assertThat(filteredStr).doesNotContain("diff --git a/left/file2.txt b/right/file2.txt");
  }

  @Test
  public void testFilterDiff_preservesTrailingNewlineWhenLastFileExcluded() throws Exception {
    writeFile(left, "file1.txt", "foo-left");
    writeFile(left, "file2.txt", "bar-left");
    writeFile(right, "file1.txt", "foo-right");
    writeFile(right, "file2.txt", "bar-right");
    byte[] diff = DiffUtil.diff(left, right, VERBOSE, testEnv);

    // Filter that includes file1.txt but excludes file2.txt (which is alphabetical last)
    byte[] filtered = DiffUtil.filterDiff(diff, f -> f.equals("left/file1.txt"));

    String filteredStr = new String(filtered, StandardCharsets.UTF_8);
    assertThat(filteredStr).endsWith("\n");
  }

  @Test
  public void testFilterDiff_preservesCrlf() throws Exception {
    String diff =
        """
        diff --git a/file1.txt b/file1.txt\r
        --- a/file1.txt\r
        +++ b/file1.txt\r
        @@ -1 +1 @@\r
        -foo\r
        +bar\r\
        """;

    byte[] filtered = DiffUtil.filterDiff(diff.getBytes(StandardCharsets.UTF_8), f -> true);

    assertThat(new String(filtered, StandardCharsets.UTF_8)).isEqualTo(diff);
  }

  @Test
  public void testFilterDiff_malformedDiffLine_notMatched() throws Exception {
    String diff =
        """
        diff file1.txt
        --- a/file1.txt
        +++ b/file1.txt
        @@ -1 +1 @@
        -foo
        +bar\
        """;

    // Filter that excludes every file found
    byte[] filteredEmpty = DiffUtil.filterDiff(diff.getBytes(StandardCharsets.UTF_8), f -> false);

    assertThat(new String(filteredEmpty, StandardCharsets.UTF_8)).isEqualTo(diff);
  }

  @Test
  public void testNoPrefixSuppressed() throws Exception {
    // set no prefix in git config
    writeFile(left, "file1.txt", "foo-left");
    writeFile(right, "file1.txt", "foo-right");

    Map<String, String> env =
        setDotGitconfigContents(
            """
            [diff]
            noprefix = true
            """);

    // diffutil ignores git prefix setting
    byte[] bytes = DiffUtil.diff(left, right, VERBOSE, env);
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .isEqualTo(
            """
            diff --git a/left/file1.txt b/right/file1.txt
            index 5ca5c10..5fcb760 100644
            --- a/left/file1.txt
            +++ b/right/file1.txt
            @@ -1 +1 @@
            -foo-left
            \\ No newline at end of file
            +foo-right
            \\ No newline at end of file
            """);
  }

  @Test
  public void testDiffFiles() throws Exception {
    writeFile(left, "deleted.txt", "");
    writeFile(left, "modified.txt", "");
    writeFile(left, "unchanged.txt", "");
    writeFile(left, "copied.txt", Strings.repeat("a", 100));
    writeFile(left, "moved_old_name.txt", Strings.repeat("b", 100));
    writeSymlink(left, "symlink_left.txt", "unchanged.txt");
    writeFile(right, "copied.txt", Strings.repeat("a", 100));
    writeFile(right, "unchanged.txt", "");
    writeFile(right, "copied2.txt", Strings.repeat("a", 100));
    writeFile(right, "moved_new_name.txt", Strings.repeat("b", 100));
    writeFile(right, "modified.txt", "foo");
    writeFile(right, "added.txt", "");
    writeFile(right, "added.txt", "");
    writeFile(right, "symlink_left.txt", "Now a file");

    ImmutableList<DiffFile> result = DiffUtil.diffFiles(left, right, VERBOSE, testEnv);
    ImmutableMap<String, DiffFile> byName = Maps.uniqueIndex(result, DiffFile::getName);

    assertThat(byName.get("deleted.txt").getOperation()).isEqualTo(Operation.DELETE);
    assertThat(byName.get("modified.txt").getOperation()).isEqualTo(Operation.MODIFIED);
    assertThat(byName.get("unchanged.txt")).isNull();
    assertThat(byName.get("copied.txt")).isNull();
    assertThat(byName.get("copied2.txt").getOperation()).isEqualTo(Operation.ADD);
    assertThat(byName.get("moved_old_name.txt").getOperation()).isEqualTo(Operation.DELETE);
    assertThat(byName.get("moved_new_name.txt").getOperation()).isEqualTo(Operation.ADD);
    assertThat(byName.get("added.txt").getOperation()).isEqualTo(Operation.ADD);
    assertThat(byName.get("symlink_left.txt").getOperation()).isEqualTo(Operation.TYPE_CHANGE);
  }

  @Test
  public void testReverseApplyPatches() throws Exception {
    writeFile(left, "file1.txt", "a\n");
    writeFile(right, "file1.txt", "b\n");

    String patch =
        """
        diff --git a/left/file1.txt b/right/file1.txt
        index e48b03e..257cc56 100644
        --- a/left/file1.txt
        +++ b/right/file1.txt
        @@ -1 +1 @@
        -a
        +b
        """;

    String patchName = "patch.txt";
    writeFile(rootPath, patchName, patch);

    // before applying the patch,
    String contents = Files.readString(right.resolve("file1.txt"));
    assertThat(contents).isEqualTo("b\n");

    DiffUtil.reverseApplyPatches(null, ImmutableList.of(rootPath.resolve(patchName)),
        right, testEnv);

    contents = Files.readString(right.resolve("file1.txt"));
    assertThat(contents).isEqualTo("a\n");
  }

  /**
   * Don't treat origin/destination folders as flags or other special argument. This means that
   * we run 'git options -- origin dest' instead of 'git options origin dest' that is
   * ambiguous.
   */
  @Test
  public void originDestinationFolderSeparatedArguments() throws Exception {
    // Should not be treated as an illegal flag
    left = createDir(tmpFolder.getRoot().toPath(), "-foo");
    right = createDir(tmpFolder.getRoot().toPath(), "reverse");
    writeFile(left, "file1.txt", "foo");
    writeFile(right, "file1.txt", "foo");

    assertThat(DiffUtil.diff(left, right, VERBOSE, testEnv)).isEmpty();
  }

  @Test
  public void testNormalizeDiff_removesGitDiffHeaders() {
    String diff =
        """
        diff --git a/premerge/foo.txt b/checkout/foo.txt
        index 123456..789101 100644
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1,2 +1,2 @@
         line 1
        -line 2
        +line 2 modified
        """;
    String expected =
        """
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1,2 +1,2 @@
         line 1
        -line 2
        +line 2 modified
        """;

    byte[] stripped = DiffUtil.normalizeDiff(diff.getBytes(StandardCharsets.UTF_8));

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).isEqualTo(expected);
  }

  @Test
  public void testNormalizeDiff_noHeaders_unchanged() {
    String diff =
        """
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1,2 +1,2 @@
         line 1
        """;

    byte[] stripped = DiffUtil.normalizeDiff(diff.getBytes(StandardCharsets.UTF_8));

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).isEqualTo(diff);
  }

  @Test
  public void testNormalizeDiff_metadataAndModeChanges() {
    String diff =
        """
        diff --git a/premerge/foo.txt b/checkout/foo.txt
        old mode 100644
        new mode 100755
        similarity index 100%
        rename from foo.txt
        rename to bar.txt
        index 123456..789101
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1 +1 @@
         line 1
        """;
    String expected =
        """
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1 +1 @@
         line 1
        """;

    byte[] stripped = DiffUtil.normalizeDiff(diff.getBytes(StandardCharsets.UTF_8));

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).isEqualTo(expected);
  }

  @Test
  public void testNormalizeDiff_removesHunkSectionHeader() {
    String diff =
        """
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1,2 +1,2 @@ extra hunk section header
         line 1
        """;
    String expected =
        """
        --- a/premerge/foo.txt
        +++ b/checkout/foo.txt
        @@ -1,2 +1,2 @@
         line 1
        """;

    byte[] stripped = DiffUtil.normalizeDiff(diff.getBytes(StandardCharsets.UTF_8));

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).isEqualTo(expected);
  }

  @Test
  public void testNormalizeDiff_preservesCrlf() {
    String diff =
        """
        diff --git a/premerge/foo.txt b/checkout/foo.txt\r
        index 123456..789101 100644\r
        --- a/premerge/foo.txt\r
        +++ b/checkout/foo.txt\r
        @@ -1,2 +1,2 @@ extra hunk section header\r
         line 1\r
        -line 2\r
        +line 2 modified\r
        """;
    String expected =
        """
        --- a/premerge/foo.txt\r
        +++ b/checkout/foo.txt\r
        @@ -1,2 +1,2 @@\r
         line 1\r
        -line 2\r
        +line 2 modified\r
        """;

    byte[] stripped = DiffUtil.normalizeDiff(diff.getBytes(StandardCharsets.UTF_8));

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).isEqualTo(expected);
  }

  @Test
  public void testStripPathPrefixes() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ ConsistencyFile.PREMERGE_DIR_NAME,
            /* rightPrefix= */ ConsistencyFile.CHECKOUT_DIR_NAME,
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_nullPrefix() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ ConsistencyFile.PREMERGE_DIR_NAME,
            /* rightPrefix= */ ConsistencyFile.CHECKOUT_DIR_NAME,
            /* commonPrefix= */ null);

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/src/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/src/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_emptyPrefix() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ ConsistencyFile.PREMERGE_DIR_NAME,
            /* rightPrefix= */ ConsistencyFile.CHECKOUT_DIR_NAME,
            /* commonPrefix= */ "");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/src/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/src/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_prefixedPathNotFound_noChange() {
    String diff =
        """
        --- a/premerge/other/foo.txt
        +++ b/checkout/other/foo.txt
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ ConsistencyFile.PREMERGE_DIR_NAME,
            /* rightPrefix= */ ConsistencyFile.CHECKOUT_DIR_NAME,
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/premerge/other/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/checkout/other/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_customFolders() {
    String diff =
        """
        --- a/custom_left/src/foo.txt
        +++ b/custom_right/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ "custom_left",
            /* rightPrefix= */ "custom_right",
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_nullLeftDir() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ null,
            /* rightPrefix= */ "checkout",
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/premerge/src/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_nullRightDir() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ "premerge",
            /* rightPrefix= */ null,
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/checkout/src/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_nullLeftRightDirs_noMatch() {
    String diff =
        """
        --- a/premerge/src/foo.txt
        +++ b/checkout/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ null,
            /* rightPrefix= */ null,
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/premerge/src/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/checkout/src/foo.txt\n");
  }

  @Test
  public void testStripPathPrefixes_nullLeftRightDirs_commonMatch() {
    String diff =
        """
        --- a/src/foo.txt
        +++ b/src/foo.txt
         line 1
        """;

    byte[] stripped =
        DiffUtil.stripPathPrefixes(
            diff.getBytes(StandardCharsets.UTF_8),
            /* leftPrefix= */ null,
            /* rightPrefix= */ null,
            /* commonPrefix= */ "src");

    String strippedStr = new String(stripped, StandardCharsets.UTF_8);
    assertThat(strippedStr).contains("--- a/foo.txt\n");
    assertThat(strippedStr).contains("+++ b/foo.txt\n");
  }

  @Test
  public void extractDescription_extractsDescription() {
    String patch =
        """
        Description line 1
        Description line 2
        --- a/file.txt
        +++ b/file.txt
        """;

    String description = DiffUtil.extractDescription(patch);

    assertThat(description)
        .isEqualTo(
            """
            Description line 1
            Description line 2\
            """);
  }

  @Test
  public void extractDescription_extractsDescription_stopsAtGitDiff() {
    String patch =
        """
        Description line 1
        Description line 2
        diff --git a/file.txt b/file.txt
        """;

    String description = DiffUtil.extractDescription(patch);

    assertThat(description)
        .isEqualTo(
            """
            Description line 1
            Description line 2\
            """);
  }

  @Test
  public void extractDescription_noDescription() {
    String patch =
        """
        --- a/file.txt
        +++ b/file.txt
        """;

    String description = DiffUtil.extractDescription(patch);

    assertThat(description).isEmpty();
  }

  @Test
  public void validatePatchDescription_validDescription_doesNotThrow() throws Exception {
    String description =
        """
        This is a valid description
        It can be multiline
        But no diff headers
        """;

    DiffUtil.validatePatchDescription(Optional.of(description));
  }

  @Test
  public void validatePatchDescription_emptyDescription_doesNotThrow() throws Exception {
    DiffUtil.validatePatchDescription(Optional.empty());
  }

  @Test
  public void validatePatchDescription_invalidDescriptionWithLeftHeader_throwsException() {
    String description =
        """
        Invalid description
        --- a/file.txt
        """;

    assertThrows(
        ValidationException.class,
        () -> DiffUtil.validatePatchDescription(Optional.of(description)));
  }

  @Test
  public void validatePatchDescription_invalidDescriptionWithRightHeader_throwsException() {
    String description =
        """
        Invalid description
        +++ b/file.txt
        """;

    assertThrows(
        ValidationException.class,
        () -> DiffUtil.validatePatchDescription(Optional.of(description)));
  }

  @Test
  public void validatePatchDescription_invalidDescriptionWithGitDiffHeader_throwsException() {
    String description =
        """
        Invalid description
        diff --git a/file.txt b/file.txt
        """;

    assertThrows(
        ValidationException.class,
        () -> DiffUtil.validatePatchDescription(Optional.of(description)));
  }

  @Test
  public void validatePatchDescription_invalidDescriptionWithHunkHeader_throwsException() {
    String description =
        """
        Invalid description
        @@ -1,2 +1,2 @@
        """;

    assertThrows(
        ValidationException.class,
        () -> DiffUtil.validatePatchDescription(Optional.of(description)));
  }

  @Test
  public void validatePatchDescription_invalidDescriptionWithIndentedHunkHeader_throwsException() {
    String description =
        """
        Invalid description
          @@ -1,2 +1,2 @@
        """;

    assertThrows(
        ValidationException.class,
        () -> DiffUtil.validatePatchDescription(Optional.of(description)));
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

  private void writeSymlink(Path parent, String fileName, String target) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.createSymbolicLink(parent.resolve(filePath), filePath.resolve(target));
  }

  private Map<String, String> setDotGitconfigContents(String contents) throws IOException {
    Path foo = Files.createTempDirectory("foo");
    Map<String, String> env = new HashMap<>(testEnv);
    env.put("HOME", foo.toAbsolutePath().toString());
    writeFile(foo, ".gitconfig", contents);
    return env;
  }
}
