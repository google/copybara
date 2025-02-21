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

package com.google.copybara.archive;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArchiveModuleTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    console = new TestingConsole();
    optionsBuilder.setConsole(console);
    skylark = new SkylarkTestExecutor(optionsBuilder);

    // Create the test archive
    Path testFile1 = workdir.resolve("foo.txt");
    Path testFile2 = workdir.resolve("bar.txt");
    Files.writeString(testFile1, "copybara");
    Files.writeString(testFile2, "baracopy");
    Path testZip = workdir.resolve("test.zip");
    try (ZipOutputStream zipOutputStream =
        new ZipOutputStream(Files.newOutputStream(testZip))) {
      for (Path file : ImmutableList.of(testFile1, testFile2)) {
        ZipEntry ze = new ZipEntry(file.getFileName().toString());
        zipOutputStream.putNextEntry(ze);
        MoreFiles.asByteSource(file).copyTo(zipOutputStream);
        zipOutputStream.closeEntry();
      }
    }

  }

  @Test
  public void testExtract_zipType() throws Exception {
    TransformWork work = TransformWorks.of(workdir, "test", console);
    Transformation t = skylark.eval("t", ""
        + "def test(ctx):\n"
        + "   zip = ctx.new_path(\"test.zip\")\n"
        + "   destination = ctx.new_path(\"archive_result\")\n"
        + "   archive.extract(\n"
        + "       archive = zip,\n"
        + "       type = \"ZIP\",\n"
        + "       destination_folder = destination,\n"
        + "   )\n"
        + "t = core.dynamic_transform(test)");

    t.transform(work);
    Path resultFile = workdir.resolve("archive_result/foo.txt");
    assertThat(Files.exists(resultFile)).isTrue();
    assertThat(Files.readString(resultFile)).isEqualTo("copybara");
  }

  @Test
  public void testExtract_autoTypeTarGz() throws Exception {
    Path testFile1 = workdir.resolve("foo.txt");
    Files.writeString(testFile1, "copybara");

    try (TarArchiveOutputStream tarGzOutputStream =
        new TarArchiveOutputStream(
            new GZIPOutputStream(Files.newOutputStream(workdir.resolve("test.tar.gz"))))) {
      tarGzOutputStream.putArchiveEntry(new TarArchiveEntry(testFile1.toFile(), "foo.txt"));
      tarGzOutputStream.write(Files.readAllBytes(testFile1));
      tarGzOutputStream.closeArchiveEntry();
    }
    TransformWork work = TransformWorks.of(workdir, "test", console);
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   tarball = ctx.new_path(\"test.tar.gz\")\n"
                + "   destination = ctx.new_path(\"archive_result\")\n"
                + "   archive.extract(\n"
                + "       archive = tarball,\n"
                + "       destination_folder = destination,\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t.transform(work);
    Path resulPath = workdir.resolve("archive_result");
    FileSubjects.assertThatPath(resulPath).containsFile("foo.txt", "copybara");
  }

  @Test
  public void testExtract_autoTypeZip() throws Exception {
    TransformWork work = TransformWorks.of(workdir, "test", console);
    Transformation t = skylark.eval("t", ""
        + "def test(ctx):\n"
        + "   zip = ctx.new_path(\"test.zip\")\n"
        + "   destination = ctx.new_path(\"archive_result\")\n"
        + "   archive.extract(\n"
        + "       archive = zip,\n"
        + "       destination_folder = destination,\n"
        + "   )\n"
        + "t = core.dynamic_transform(test)");

    t.transform(work);
    Path resultFile = workdir.resolve("archive_result/foo.txt");
    assertThat(Files.exists(resultFile)).isTrue();
    assertThat(Files.readString(resultFile)).isEqualTo("copybara");
  }

  @Test
  public void testExtract_autoType_detectTarXz() throws Exception {
    Path testfile = workdir.resolve("foo.txt");
    Files.writeString(testfile, "copybara");

    try (TarArchiveOutputStream tarXzOutputStream =
        new TarArchiveOutputStream(
            new XZCompressorOutputStream(Files.newOutputStream(workdir.resolve("test.tar.xz"))))) {
      tarXzOutputStream.putArchiveEntry(new TarArchiveEntry(testfile.toFile(), "foo.txt"));
      tarXzOutputStream.write(Files.readAllBytes(testfile));
      tarXzOutputStream.closeArchiveEntry();
    }

    TransformWork work = TransformWorks.of(workdir, "test", console);
    Transformation t =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.extract(\n"
                + "       archive = ctx.new_path(\"test.tar.xz\"),\n"
                + "       destination_folder = ctx.new_path(\"archive_result\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t.transform(work);
    Path resultPath = workdir.resolve("archive_result");
    FileSubjects.assertThatPath(resultPath).containsFile("foo.txt", "copybara");
  }

  @Test
  public void testExtractWithPathsGlob() throws Exception {
    TransformWork work = TransformWorks.of(workdir, "test", console);
    Transformation t = skylark.eval("t", ""
        + "def test(ctx):\n"
        + "   zip = ctx.new_path(\"test.zip\")\n"
        + "   destination = ctx.new_path(\"archive_result\")\n"
        + "   archive.extract(\n"
        + "       archive = zip,\n"
        + "       type = \"ZIP\",\n"
        + "       destination_folder = destination,\n"
        + "       paths = glob([\"bar.txt\"])"
        + "   )\n"
        + "t = core.dynamic_transform(test)");

    t.transform(work);
    assertThat(Files.exists(workdir.resolve("archive_result/foo.txt"))).isFalse();
    Path resultFile = workdir.resolve("archive_result/bar.txt");
    assertThat(Files.exists(resultFile)).isTrue();
    assertThat(Files.readString(resultFile)).isEqualTo("baracopy");
  }

  @Test
  public void testExtractToCurrentFolderDefault() throws Exception {
    TransformWork work = TransformWorks.of(workdir, "test", console);
    // The default directory where the archive is located is used, which is "archive_result" here
    Path archiveResult = workdir.resolve("archive_result");
    Files.createDirectories(archiveResult);
    Files.move(workdir.resolve("test.zip"), archiveResult.resolve("test.zip"));
    Transformation t = skylark.eval("t", ""
        + "def test(ctx):\n"
        + "   zip = ctx.new_path(\"archive_result/test.zip\")\n"
        + "   archive.extract(\n"
        + "       archive = zip,\n"
        + "       type = \"ZIP\",\n"
        + "   )\n"
        + "t = core.dynamic_transform(test)");

    //This should extract the files to archive_result
    t.transform(work);
    Path resultFile1 = workdir.resolve("foo.txt");
    Path resultFile2 = workdir.resolve("bar.txt");
    assertThat(Files.exists(resultFile1)).isTrue();
    assertThat(Files.readString(resultFile1)).isEqualTo("copybara");
    assertThat(Files.exists(resultFile2)).isTrue();
    assertThat(Files.readString(resultFile2)).isEqualTo("baracopy");
  }
}
