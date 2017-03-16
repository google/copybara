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
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileUtilTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private Path temp;

  @Before
  public void setup() throws Exception {
    temp = newTempDirectory("temp");
  }

  private Path newTempDirectory(String prefix) throws IOException {
    // TODO(copybara-team): Explore running the tests against different filesystems using:
    // Jimfs.newFileSystem(Configuration.windows());
    // Right now it's not possible because we rely on {@link Path#toFile}, which is not supported
    // by Jimfs.
    return Files.createTempDirectory(prefix);
  }

  @Test
  public void checkRelativism_string_absolute() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative("/foo");
  }

  @Test
  public void checkRelativism_path_absolute() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative(Paths.get("/foo"));
  }

  @Test
  public void checkRelativism_oneDot() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative("foo/./bar");
  }

  @Test
  public void checkRelativism_twoDots() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative("foo/../bar");
  }

  @Test
  public void checkRelativism_path_twoDotsAtStart() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative(Paths.get("../bar"));
  }

  @Test
  public void checkRelativism_twoDotsAtEnd() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative(Paths.get("bar/.."));
  }

  @Test
  public void checkRelativism_oneDotAtStart() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative("./foo");
  }

  @Test
  public void checkRelativism_oneDotAtEnd() {
    thrown.expect(IllegalArgumentException.class);
    FileUtil.checkNormalizedRelative("foo/.");
  }

  @Test
  public void checkRelativism_succeedsForDotInValidComponent() {
    FileUtil.checkNormalizedRelative("foo/.emacs.d");
    FileUtil.checkNormalizedRelative("foo/bar.baz");
  }

  @Test
  public void testCopyMaterializeAbsolutePaths() throws Exception {
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    Path absolute = touch(Files.createDirectory(temp.resolve("absolute")).resolve("absolute"));
    Path absoluteSymlink = Files.createSymbolicLink(
        temp.resolve("absolute").resolve("symlink"), absolute);

    Path absoluteDir = newTempDirectory("absoluteDir");
    Files.createDirectories(absoluteDir.resolve("absoluteDirDir"));
    Files.write(absoluteDir.resolve("absoluteDirElement"), "abc".getBytes(UTF_8));
    Files.write(absoluteDir.resolve("absoluteDirDir/element"), "abc".getBytes(UTF_8));

    FileUtil.addPermissions(touch(one.resolve("foo")),
        ImmutableSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ));
    touch(one.resolve("some/folder/bar"));

    Files.createSymbolicLink(one.resolve("some/folder/baz"),
        one.getFileSystem().getPath("../../foo"));

    // Symlink to the root:
    Files.createSymbolicLink(one.resolve("dot"), one.getFileSystem().getPath("."));
    // Test multiple jumps inside the root: some/multiple -> folder/baz -> ../../foo
    Files.createSymbolicLink(one.resolve("some/multiple"),
        one.resolve("some").relativize(one.resolve("some/folder/baz")));

    Path folder = one.resolve("some/folder");
    Path absoluteTarget = folder.relativize(absolute);
    Files.createSymbolicLink(folder.resolve("absolute"), absoluteTarget);
    Path absoluteSymlinkTarget = folder.relativize(absoluteSymlink);
    Files.createSymbolicLink(folder.resolve("absoluteSymlink"), absoluteSymlinkTarget);
    // Multiple jumps of symlinks that ends out of the root
    Files.createSymbolicLink(folder.resolve("absolute2"),
        folder.relativize(folder.resolve("absolute")));

    // Symlink to a directory outside root
    Files.createSymbolicLink(folder.resolve("absolute3"), absoluteDir);

    FileUtil.copyFilesRecursively(one, two, CopySymlinkStrategy.MATERIALIZE_OUTSIDE_SYMLINKS);

    assertThatPath(two)
        .containsFile("foo", "abc")
        .containsFile("dot/foo", "abc")
        .containsFile("some/folder/bar", "abc")
        .containsFile("some/multiple", "abc")
        .containsFile("some/folder/absolute", "abc")
        .containsFile("some/folder/absoluteSymlink", "abc")
        .containsFile("some/folder/absolute2", "abc")
        .containsFile("some/folder/absolute3/absoluteDirElement", "abc")
        .containsFile("some/folder/absolute3/absoluteDirDir/element", "abc")
        .containsFile("some/folder/baz", "abc")
        .containsNoMoreFiles();

    assertThat(Files.isExecutable(two.resolve("foo"))).isTrue();
    assertThat(Files.isExecutable(two.resolve("foo"))).isTrue();
    assertThat(Files.isExecutable(two.resolve("some/folder/bar"))).isFalse();
    assertThat(Files.readSymbolicLink(two.resolve("some/folder/baz")).toString())
        .isEqualTo(two.getFileSystem().getPath("../../foo").toString());
    // Symlink to a directory inside the root are symlinked
    assertThat(Files.isSymbolicLink(two.resolve("dot"))).isTrue();
    assertThat(Files.isSymbolicLink(two.resolve("some/multiple"))).isTrue();
    // Anything outside of one/... is copied as a regular file
    assertThat(Files.isSymbolicLink(two.resolve("some/folder/absolute"))).isFalse();
    assertThat(Files.isSymbolicLink(two.resolve("some/folder/absoluteSymlink"))).isFalse();
    assertThat(Files.isSymbolicLink(two.resolve("some/folder/absolute2"))).isFalse();
    assertThat(Files.isSymbolicLink(two.resolve("some/folder/absolute3"))).isFalse();
  }

  @Test
  public void testCopyFailAbsoluteSymlinks() throws Exception {
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    Path absolute = touch(Files.createDirectory(temp.resolve("absolute")).resolve("absolute"));

    Path folder = Files.createDirectories(one.resolve("some/folder"));
    Path absoluteTarget = folder.relativize(absolute);
    Files.createSymbolicLink(folder.resolve("absolute"), absoluteTarget);

    thrown.expect(AbsoluteSymlinksNotAllowed.class);
    FileUtil.copyFilesRecursively(one, two, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
  }

  private Path touch(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, "abc".getBytes(UTF_8));
    return path;
  }

  @Test
  public void testMaterializedSymlinksAreWriteable() throws Exception {
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    Path absolute = touch(Files.createDirectory(temp.resolve("absolute")).resolve("absolute"));
    FileUtil.addPermissions(absolute, ImmutableSet.of(PosixFilePermission.OWNER_READ));

    Path absoluteTarget = one.relativize(absolute);
    Files.createSymbolicLink(one.resolve("absolute"), absoluteTarget);

    FileUtil.copyFilesRecursively(one, two, CopySymlinkStrategy.MATERIALIZE_OUTSIDE_SYMLINKS);
    assertThat(Files.isSymbolicLink(two.resolve("absolute"))).isFalse();
    assertThat(Files.isWritable(two.resolve("absolute"))).isTrue();
  }

  @Test
  public void testCopyWithGlob() throws Exception {
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    Files.createDirectories(one.resolve("foo"));
    Files.createDirectories(one.resolve("bar"));
    touch(one.resolve("foo/include.txt"));
    touch(one.resolve("foo/exclude.txt"));
    touch(one.resolve("bar/nonono.txt"));

    FileUtil.copyFilesRecursively(one, two, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS,
                                  Glob.createGlob(
                                      ImmutableList.of("foo/**"),
                                      ImmutableList.of("foo/exclude.txt")));

    assertThatPath(two).containsFiles("foo/include.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testNoGlobs() throws Exception {
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    Files.createDirectories(one.resolve("foo"));
    Files.createDirectories(one.resolve("bar"));
    touch(one.resolve("foo/include.txt"));
    touch(one.resolve("foo/exclude.txt"));
    touch(one.resolve("bar/nonono.txt"));

    FileUtil.copyFilesRecursively(one, two, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);

    assertThatPath(two)
        .containsFiles("foo/include.txt", "foo/exclude.txt", "bar/nonono.txt")
        .containsNoMoreFiles();
  }
}
