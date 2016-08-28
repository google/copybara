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

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileUtilTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

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
  public void testCopy() throws Exception {
    Path one = Files.createTempDirectory("one");
    Path two = Files.createTempDirectory("two");
    Path absolute = touch(Files.createTempDirectory("absolute").resolve("absolute"));
    Files.createDirectories(two.getParent());

    Files.setPosixFilePermissions(touch(one.resolve("foo")),
        ImmutableSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ));
    touch(one.resolve("some/folder/bar"));

    Files.createSymbolicLink(one.resolve("some/folder/baz"),
        one.getFileSystem().getPath("../../foo"));

    Path absoluteTarget = one.resolve("some/folder").relativize(absolute);
    Files.createSymbolicLink(one.resolve("some/folder/absolute"), absoluteTarget);

    FileUtil.copyFilesRecursively(one, two);

    assertThatPath(two)
        .containsFile("foo", "abc")
        .containsFile("some/folder/bar", "abc")
        .containsFile("some/folder/absolute", "abc")
        .containsFile("some/folder/baz", "abc")
        .containsNoMoreFiles();

    assertThat(Files.isExecutable(two.resolve("foo"))).isTrue();
    assertThat(Files.isExecutable(two.resolve("some/folder/bar"))).isFalse();
    assertThat(Files.readSymbolicLink(two.resolve("some/folder/baz")).toString())
        .isEqualTo(two.getFileSystem().getPath("../../foo").toString());
    assertThat(Files.readSymbolicLink(two.resolve("some/folder/absolute")).toString())
        .isEqualTo(absoluteTarget.toString());
  }

  private Path touch(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, "abc".getBytes());
    return path;
  }
}
