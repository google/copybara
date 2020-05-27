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
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.DiffUtil.DiffFile.Operation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private Path left;
  private Path right;

  @Before
  public void setUp() throws Exception {
    Path rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
  }

  @Test
  public void pathsAreNotSiblings_diff() throws Exception {
    Path foo = createDir(left, "foo");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> DiffUtil.diff(left, foo, VERBOSE, System.getenv()));
    assertThat(e).hasMessageThat().contains("Paths 'one' and 'other' must be sibling directories");
  }

  @Test
  public void pathsAreNotSiblings_diffFiles() throws Exception {
    Path foo = createDir(left, "foo");
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> DiffUtil.diffFiles(left, foo, VERBOSE, System.getenv()));
    assertThat(e).hasMessageThat().contains("Paths 'one' and 'other' must be sibling directories");
  }

  @Test
  public void emptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "foo");
    writeFile(right, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, System.getenv());

    assertThat(diffContents).isEmpty();

    assertThat(DiffUtil.diffFiles(left, right, VERBOSE, System.getenv())).isEmpty();
  }

  @Test
  public void testFilterDiff() throws Exception {
    writeFile(left, "file1.txt", "foo-left");
    writeFile(left, "file2.txt", "bar-left");
    writeFile(right, "file1.txt", "foo-right");
    writeFile(right, "file2.txt", "bar-right");

    assertThat(DiffUtil.filterDiff(DiffUtil.diff(left, right, VERBOSE, System.getenv()),
        f -> false)).isEmpty();

    String all = DiffUtil.filterDiff(DiffUtil.diff(left, right, VERBOSE, System.getenv()),
        f -> true);
    assertThat(all).contains("diff --git a/left/file1.txt b/right/file1.txt");
    assertThat(all).contains("diff --git a/left/file2.txt b/right/file2.txt");

    String one = DiffUtil.filterDiff(DiffUtil.diff(left, right, VERBOSE, System.getenv()),
        f -> f.equals("left/file1.txt"));
    assertThat(one).contains("diff --git a/left/file1.txt b/right/file1.txt");
    assertThat(one).contains("-foo-left\n"
        + "\\ No newline at end of file\n"
        + "+foo-right\n"
        + "\\ No newline at end of file");
    assertThat(one).doesNotContain("diff --git a/left/file2.txt b/right/file2.txt");
  }

  @Test
  public void testDiffFiles() throws Exception {
    writeFile(left, "deleted.txt", "");
    writeFile(left, "modified.txt", "");
    writeFile(left, "unchanged.txt", "");
    writeFile(left, "copied.txt", Strings.repeat("a", 100));
    writeFile(left, "moved_old_name.txt", Strings.repeat("b", 100));
    writeFile(right, "copied.txt", Strings.repeat("a", 100));
    writeFile(right, "unchanged.txt", "");
    writeFile(right, "copied2.txt", Strings.repeat("a", 100));
    writeFile(right, "moved_new_name.txt", Strings.repeat("b", 100));
    writeFile(right, "modified.txt", "foo");
    writeFile(right, "added.txt", "");

    ImmutableList<DiffFile> result = DiffUtil.diffFiles(left, right, VERBOSE, System.getenv());
    ImmutableMap<String, DiffFile> byName = Maps.uniqueIndex(result, DiffFile::getName);

    assertThat(byName.get("deleted.txt").getOperation()).isEqualTo(Operation.DELETE);
    assertThat(byName.get("modified.txt").getOperation()).isEqualTo(Operation.MODIFIED);
    assertThat(byName.get("unchanged.txt")).isNull();
    assertThat(byName.get("copied.txt")).isNull();
    assertThat(byName.get("copied2.txt").getOperation()).isEqualTo(Operation.ADD);
    assertThat(byName.get("moved_old_name.txt").getOperation()).isEqualTo(Operation.DELETE);
    assertThat(byName.get("moved_new_name.txt").getOperation()).isEqualTo(Operation.ADD);
    assertThat(byName.get("added.txt").getOperation()).isEqualTo(Operation.ADD);
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

    assertThat(DiffUtil.diff(left, right, VERBOSE, System.getenv())).isEmpty();
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
}
