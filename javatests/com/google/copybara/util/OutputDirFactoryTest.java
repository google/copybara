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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OutputDirFactoryTest {

  private Path rootPath;

  @Before
  public void setUp() throws Exception {
    rootPath = Files.createTempDirectory("output_dir_factory_test");
  }

  @Test
  public void tempDirsDontCollide() throws Exception {
    OutputDirFactory outputDirFactory = new OutputDirFactory(rootPath, /*reuseOutputDirs*/ false);
    Path fooDir = outputDirFactory.newDirectory("foo");
    Files.write(fooDir.resolve("file"), "First version".getBytes(StandardCharsets.UTF_8));
    Path fooDir2 = outputDirFactory.newDirectory("foo");
    Files.write(fooDir2.resolve("file"), "Second version".getBytes(StandardCharsets.UTF_8));
    assertThat(fooDir.toAbsolutePath().equals(fooDir2.toAbsolutePath())).isFalse();
    assertThatPath(fooDir)
        .containsFile("file", "First version")
        .containsNoMoreFiles();
    assertThatPath(fooDir2)
        .containsFile("file", "Second version")
        .containsNoMoreFiles();
  }

  @Test
  public void outputDirsAreReused() throws Exception {
    OutputDirFactory outputDirFactory = new OutputDirFactory(rootPath, /*reuseOutputDirs*/ true);
    Path fooDir = outputDirFactory.newDirectory("foo");
    Files.write(fooDir.resolve("file1"), "First version".getBytes(StandardCharsets.UTF_8));
    Files.write(fooDir.resolve("file2"), "First version".getBytes(StandardCharsets.UTF_8));
    Path fooDir2 = outputDirFactory.newDirectory("foo");
    Files.write(fooDir.resolve("file1"), "Second version".getBytes(StandardCharsets.UTF_8));

    assertThat(fooDir.toAbsolutePath().equals(fooDir2.toAbsolutePath())).isTrue();
    assertThatPath(fooDir2)
        .containsFile("file1", "Second version")
        .containsNoMoreFiles();
  }
}
