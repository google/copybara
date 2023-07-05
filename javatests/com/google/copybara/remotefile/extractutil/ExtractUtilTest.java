/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.copybara.remotefile.extractutil;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExtractUtilTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  Path testFolder;
  String testFilenameA = "filea.txt";
  String testFilenameB = "fileb.md";
  String testFileContentsA = "aaaa";
  String testFileContentsB = "bbbb";
  Path testZip;

  @Before
  public void setUp() throws IOException {
    testFolder = folder.getRoot().toPath();
    Path testFileA = testFolder.resolve(testFilenameA);
    Path testFileB = testFolder.resolve(testFilenameB);
    MoreFiles.asByteSink(testFileA).asCharSink(UTF_8).write(testFileContentsA);
    MoreFiles.asByteSink(testFileB).asCharSink(UTF_8).write(testFileContentsB);

    // zip the test archive and place into the checkout directory
    testZip = testFolder.resolve("testfile.zip");
    try (ZipOutputStream zipOs = new ZipOutputStream(Files.newOutputStream(testZip))) {
      ImmutableList.of(testFileA, testFileB)
          .forEach(
              (Path file) -> {
                try {
                  ZipEntry ze = new ZipEntry(file.getFileName().toString());
                  zipOs.putNextEntry(ze);
                  MoreFiles.asByteSource(file).copyTo(zipOs);
                  zipOs.closeEntry();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  @Test
  public void testExtractArchive() throws Exception {
    // extract it with null glob
    InputStream is = Files.newInputStream(testZip);
    Path outputPath = testFolder.resolve("output");
    ExtractUtil.extractArchive(is, outputPath, ExtractType.ZIP, null);
    // test that files have been extracted
    assertThat(MoreFiles.asByteSource(outputPath.resolve(testFilenameA)).asCharSource(UTF_8).read())
        .isEqualTo(testFileContentsA);
  }

  @Test
  public void testFilteredExtractArchive() throws Exception {
    // extract it using a glob
    InputStream is = Files.newInputStream(testZip);
    Path outputPath = testFolder.resolve("output");
    ExtractUtil.extractArchive(
        is,
        outputPath,
        ExtractType.ZIP,
        Glob.createGlob(ImmutableList.of("*.md"), ImmutableList.of()));
    // test that files matching the glob have been extracted
    assertThat(MoreFiles.asByteSource(outputPath.resolve(testFilenameB)).asCharSource(UTF_8).read())
        .isEqualTo(testFileContentsB);
    // test that files not matching the glob have been filtered out
    assertThat(Files.exists(outputPath.resolve(testFilenameA))).isFalse();
  }
}
