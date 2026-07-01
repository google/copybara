/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.folder;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.Glob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FolderDestinationReaderTest {
  private Path folderDir;
  private Path workDir;

  private FolderDestinationReader reader;

  @Before
  public void setup() throws Exception {
    folderDir = Files.createTempDirectory("folderdir");
    workDir = Files.createTempDirectory("workdir");
    reader = new FolderDestinationReader(folderDir, workDir);
  }

  @Test
  public void testReadFile() throws Exception {
    Files.writeString(folderDir.resolve("test1.txt"), "abcd");
    String result = reader.readFile("test1.txt");
    assertThat(result).isEqualTo("abcd");
  }

  @Test
  public void testExists() throws Exception {
    Files.writeString(folderDir.resolve("test1.txt"), "abcd");
    assertThat(reader.exists("test1.txt")).isTrue();
    assertThat(reader.exists("doesNotExist.txt")).isFalse();
  }

  @Test
  public void testCopyDestinationFiles() throws Exception {
    ImmutableMap<Path, String> files =
        ImmutableMap.of(
            folderDir.resolve("test1.txt"), "aaaa",
            folderDir.resolve("test2.txt"), "bbbb",
            folderDir.resolve("foo/test.txt"), "cccc",
            folderDir.resolve("bar/test.txt"), "dddd");

    for (Entry<Path, String> file : files.entrySet()) {
      Files.createDirectories(file.getKey().getParent());
      Files.writeString(file.getKey(), file.getValue());
    }

    reader.copyDestinationFiles(Glob.createGlob(ImmutableList.of("*.txt", "foo/**")), null);

    assertThat(Files.readString(workDir.resolve("test1.txt"))).isEqualTo("aaaa");
    assertThat(Files.readString(workDir.resolve("test2.txt"))).isEqualTo("bbbb");
    assertThat(Files.readString(workDir.resolve("foo/test.txt"))).isEqualTo("cccc");
    assertThat(Files.exists(workDir.resolve("bar/test.txt"))).isFalse();
  }
}
