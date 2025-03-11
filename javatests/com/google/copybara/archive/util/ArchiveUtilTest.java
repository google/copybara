/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.archive.util;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.copybara.CheckoutPath;
import com.google.copybara.remotefile.extractutil.ExtractType;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.util.Glob;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ArchiveUtilTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  Path testFolder;
  Path unarchiveResultFolder;

  @Before
  public void setup() throws Exception {
    folder.newFolder("subdir");
    testFolder = folder.getRoot().toPath();
    MoreFiles.asByteSink(testFolder.resolve("filea.txt")).asCharSink(UTF_8).write("aaaa");
    MoreFiles.asByteSink(testFolder.resolve("fileb.md")).asCharSink(UTF_8).write("bbbb");
    MoreFiles.asByteSink(testFolder.resolve("subdir/filec.txt")).asCharSink(UTF_8).write("cccc");
  }

  @Before
  public void doBeforeEachTest() throws Exception {
    folder.newFolder("unarchive_result");
    unarchiveResultFolder = folder.getRoot().toPath().resolve("unarchive_result");
  }

  @Test
  public void testCreateArchive_zipFile() throws Exception {
    Path archivePath = testFolder.resolve("subdir/test.zip");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.ZIP,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    ZipArchiveEntry archiveEntry;
    try (ZipArchiveInputStream inputStream =
        new ZipArchiveInputStream(Files.newInputStream(archivePath))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFile("unarchive_result/filea.txt", "aaaa")
        .containsFile("unarchive_result/fileb.md", "bbbb")
        .containsFile("unarchive_result/subdir/filec.txt", "cccc");
  }

  @Test
  public void testCreateArchive_jarFile() throws Exception {
    Path archivePath = testFolder.resolve("test.jar");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.JAR,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    ZipArchiveEntry archiveEntry;
    try (ZipArchiveInputStream inputStream =
        new ZipArchiveInputStream(Files.newInputStream(archivePath))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFile("unarchive_result/filea.txt", "aaaa")
        .containsFile("unarchive_result/fileb.md", "bbbb")
        .containsFile("unarchive_result/subdir/filec.txt", "cccc");
  }

  @Test
  public void testCreateArchive_tarFile() throws Exception {
    Path archivePath = testFolder.resolve("test.tar");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.TAR,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    TarArchiveEntry archiveEntry;
    try (TarArchiveInputStream inputStream =
        new TarArchiveInputStream(Files.newInputStream(archivePath))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFile("unarchive_result/filea.txt", "aaaa")
        .containsFile("unarchive_result/fileb.md", "bbbb")
        .containsFile("unarchive_result/subdir/filec.txt", "cccc");
  }

  @Test
  public void testCreateArchive_tarGzFile() throws Exception {
    Path archivePath = testFolder.resolve("test.tar.gz");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.TAR_GZ,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    TarArchiveEntry archiveEntry;
    try (TarArchiveInputStream inputStream =
        new TarArchiveInputStream(
            new GzipCompressorInputStream(Files.newInputStream(archivePath)))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFile("unarchive_result/filea.txt", "aaaa")
        .containsFile("unarchive_result/fileb.md", "bbbb")
        .containsFile("unarchive_result/subdir/filec.txt", "cccc");
  }

  @Test
  public void testCreateArchive_tarXzFile() throws Exception {
    Path archivePath = testFolder.resolve("test.tar.xz");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.TAR_XZ,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    TarArchiveEntry archiveEntry;
    try (TarArchiveInputStream inputStream =
        new TarArchiveInputStream(new XZCompressorInputStream(Files.newInputStream(archivePath)))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFile("unarchive_result/filea.txt", "aaaa")
        .containsFile("unarchive_result/fileb.md", "bbbb")
        .containsFile("unarchive_result/subdir/filec.txt", "cccc");
  }

  @Test
  public void testCreateArchive_tarFile_withGlob() throws Exception {
    Path archivePath = testFolder.resolve("test.tar");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.TAR,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**subdir/**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());

    TarArchiveEntry archiveEntry;
    try (TarArchiveInputStream inputStream =
        new TarArchiveInputStream(Files.newInputStream(archivePath))) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Files.createDirectories(unarchiveResultFolder.resolve(archiveEntry.getName()).getParent());
        MoreFiles.asByteSink(unarchiveResultFolder.resolve(archiveEntry.getName()))
            .writeFrom(inputStream);
      }
    }

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString())
        .containsFile("unarchive_result/subdir/filec.txt", "cccc")
        .containsNoFiles("unarchive_result/filea.txt", "unarchive_result/fileb.md");
  }

  @Test
  public void testCreateArchive_zipFile_globNoMatch() throws Exception {
    Path archivePath = testFolder.resolve("test.zip");
    CheckoutPath checkoutPath =
        CheckoutPath.createWithCheckoutDir(archivePath.getFileName(), testFolder);

    ArchiveUtil.createArchive(
        Files.newOutputStream(archivePath),
        ExtractType.ZIP,
        checkoutPath,
        Glob.createGlob(ImmutableList.of("**abcd/**")));

    FileSubjects.assertThatPath(testFolder)
        .containsFiles(testFolder.relativize(archivePath).toString());
    // Can refer https://en.wikipedia.org/wiki/ZIP_(file_format)#Limits.
    assertThat(Files.size(archivePath)).isEqualTo(22);
  }
}
