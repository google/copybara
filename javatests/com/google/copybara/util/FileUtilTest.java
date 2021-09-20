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
import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;

@RunWith(JUnit4.class)
public class FileUtilTest {
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
    assertThrows(IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative("/foo"));
  }

  @Test
  public void checkRelativism_path_absolute() {
    assertThrows(
        IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative(Paths.get("/foo")));
  }

  @Test
  public void checkRelativism_oneDot() {
    assertThrows(
        IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative("foo/./bar"));
  }

  @Test
  public void checkRelativism_twoDots() {
    assertThrows(
        IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative("foo/../bar"));
  }

  @Test
  public void checkRelativism_path_twoDotsAtStart() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtil.checkNormalizedRelative(Paths.get("../bar")));
  }

  @Test
  public void checkRelativism_twoDotsAtEnd() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtil.checkNormalizedRelative(Paths.get("bar/..")));
  }

  @Test
  public void checkRelativism_oneDotAtStart() {
    assertThrows(IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative("./foo"));
  }

  @Test
  public void checkRelativism_oneDotAtEnd() {
    assertThrows(IllegalArgumentException.class, () -> FileUtil.checkNormalizedRelative("foo/."));
  }

  @Test
  public void checkRelativism_succeedsForDotInValidComponent() {
    FileUtil.checkNormalizedRelative("foo/.emacs.d");
    FileUtil.checkNormalizedRelative("foo/bar.baz");
  }


  @Test
  public void testCopyFilesRecursively_symlink_to_other_root() throws Exception{
    Path orig = Files.createDirectory(temp.resolve("orig"));
    Path dest = Files.createDirectory(temp.resolve("dest"));

    Files.createDirectory(orig.resolve("foo"));
    Files.createDirectory(orig.resolve("bar"));

    Files.write(orig.resolve("bar/bar.txt"), new byte[]{});
    Files.createSymbolicLink(orig.resolve("foo/foo.txt"),
        orig.getFileSystem().getPath("../bar/bar.txt"));

    FileUtil.copyFilesRecursively(orig, dest, FAIL_OUTSIDE_SYMLINKS,
        Glob.createGlob(ImmutableList.of("foo/**", "bar/**")));

  }

  /**
   * Regression test for folder.origin refs that contain '..' and globs that wouldn't match
   * globs like foo/../foo/file if we didn't normalize symlinks before.
   */
  @Test
  public void testCopyFilesRecursively_symlink_with_dot_dot() throws Exception {
    Path orig = Files.createDirectory(temp.resolve("orig"));
    Path dest = Files.createDirectory(temp.resolve("dest"));

    Files.createDirectory(orig.resolve("foo"));
    Files.createDirectory(orig.resolve("bar"));

    Files.write(orig.resolve("bar/bar.txt"), new byte[]{});
    Files.createSymbolicLink(orig.resolve("foo/foo.txt"),
        orig.getFileSystem().getPath("../bar/bar.txt"));

    FileUtil.copyFilesRecursively(orig.resolve("../" + orig.getFileName()), dest,
        FAIL_OUTSIDE_SYMLINKS, Glob.createGlob(ImmutableList.of("foo/*", "bar/*")));
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

    AbsoluteSymlinksNotAllowed expected =
        assertThrows(
            AbsoluteSymlinksNotAllowed.class,
            () -> FileUtil.copyFilesRecursively(one, two, FAIL_OUTSIDE_SYMLINKS));
    assertThat(expected).hasMessageThat().isNotNull();
    assertThat(expected.toString()).contains("is absolute or escaped the root:");
      assertThat(expected.toString()).contains("folder/absolute");
      assertThat(expected.toString()).contains("absolute/absolute");
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

    FileUtil.copyFilesRecursively(one, two, FAIL_OUTSIDE_SYMLINKS,
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

    FileUtil.copyFilesRecursively(one, two, FAIL_OUTSIDE_SYMLINKS);

    assertThatPath(two)
        .containsFiles("foo/include.txt", "foo/exclude.txt", "bar/nonono.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyWithGlob_oneRootNotPresent() throws Exception {

    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    // We don't create 'bar' directory.
    Files.createDirectories(one.resolve("foo"));
    touch(one.resolve("foo/include.txt"));

    FileUtil.copyFilesRecursively(one, two, FAIL_OUTSIDE_SYMLINKS,
        Glob.createGlob(ImmutableList.of("foo/**", "bar/**")));

    assertThatPath(two).containsFiles("foo/include.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyWithGlob_validatorCalled() throws Exception {
    FileUtil.CopyVisitorValidator validator = mock(FileUtil.CopyVisitorValidator.class);
    Path one = Files.createDirectory(temp.resolve("one"));
    Path two = Files.createDirectory(temp.resolve("two"));
    // We don't create 'bar' directory.
    Files.createDirectories(one.resolve("foo"));
    touch(one.resolve("foo/include.txt"));

    FileUtil.copyFilesRecursively(one, two, FAIL_OUTSIDE_SYMLINKS,
        Glob.createGlob(ImmutableList.of("foo/**", "bar/**")), Optional.of(validator));
    verify(validator).validate(ArgumentMatchers.eq(one.resolve("foo/include.txt")));
    verifyNoMoreInteractions(validator);
   }
}
