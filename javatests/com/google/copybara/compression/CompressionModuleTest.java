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

package com.google.copybara.compression;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.MoreFiles;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
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
public class CompressionModuleTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private SkylarkTestExecutor starlark;
  Path checkoutDirectory;

  @Before
  public void setUp() throws Exception {
    checkoutDirectory = folder.getRoot().toPath();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.testingOptions.checkoutDirectory = checkoutDirectory;
    starlark = new SkylarkTestExecutor(optionsBuilder);

    String testContents = "testzipcontents";
    // create test file contents
    Path testFile = checkoutDirectory.resolve("testfile.txt");
    MoreFiles.asByteSink(testFile).asCharSink(UTF_8).write(testContents);

    // zip the test archive and place into the checkout directory
    Path testZip = checkoutDirectory.resolve("testfile.zip");
    try (ZipOutputStream zipOs = new ZipOutputStream(Files.newOutputStream(testZip))) {
      ZipEntry ze = new ZipEntry(testFile.getFileName().toString());
      zipOs.putNextEntry(ze);
      MoreFiles.asByteSource(testFile).copyTo(zipOs);
      zipOs.closeEntry();
    }
  }

  @Test
  public void unzipPath() throws IOException, ValidationException {
    var unused =
        starlark.eval(
            "zip_path",
            "zip_path = testing.get_checkout(\"testfile.zip\")\n"
                + "unzipped_path = testing.get_checkout(\"unzipped\")\n"
                + "compression.unzip_path(zip_path, unzipped_path)");

    Path unzippedFile = checkoutDirectory.resolve("unzipped/testfile.txt");
    assertThat(MoreFiles.asByteSource(unzippedFile).asCharSource(UTF_8).read())
        .isEqualTo("testzipcontents");
  }

  @Test
  public void testFilter() throws ValidationException {
    // test that filter parameter works
    var unused =
        starlark.eval(
            "zip_path",
            "zip_path = testing.get_checkout(\"testfile.zip\")\n"
                + "unzipped_path = testing.get_checkout(\"unzipped\")\n"
                + "compression.unzip_path(zip_path, unzipped_path, filter=glob([\"*.asdf\"]))");

    Path unzippedFile = checkoutDirectory.resolve("unzipped/testfile.txt");
    assertThat(Files.exists(unzippedFile)).isFalse();
  }
}
