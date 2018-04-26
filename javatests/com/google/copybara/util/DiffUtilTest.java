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
import static junit.framework.TestCase.fail;

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
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DiffUtilTest {

  private static final boolean VERBOSE = true;

  // Command requires the working dir as a File, and Jimfs does not support Path.toFile()
  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule public final ExpectedException thrown = ExpectedException.none();

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
    try {
      DiffUtil.diff(left, foo, VERBOSE, /*environment=*/ null);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains(
          "Paths 'one' and 'other' must be sibling directories");
    }
  }

  @Test
  public void pathsAreNotSiblings_diffFiles() throws Exception {
    Path foo = createDir(left, "foo");
    try {
      DiffUtil.diffFiles(left, foo, VERBOSE, /*environment=*/ null);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains(
          "Paths 'one' and 'other' must be sibling directories");
    }
  }

  @Test
  public void emptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "foo");
    writeFile(right, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right, VERBOSE, /*environment=*/ null);

    assertThat(diffContents).isEmpty();

    assertThat(DiffUtil.diffFiles(left, right, VERBOSE, /*environment=*/ null)).isEmpty();
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

    ImmutableList<DiffFile> result = DiffUtil.diffFiles(left, right, VERBOSE,
        /*environment=*/ null);
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

    assertThat(DiffUtil.diff(left, right, VERBOSE, /*environment=*/ null)).isEmpty();
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
