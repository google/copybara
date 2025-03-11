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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ArchiveModuleTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    // Create a temporary working directory for adding test files.
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    Files.createDirectories(workdir.resolve("directory/subdir"));
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    console = new TestingConsole();
    optionsBuilder.setConsole(console);
    skylark = new SkylarkTestExecutor(optionsBuilder);

    // Create test files / sub-directories and add dummy content to them.
    Path testFile1 = workdir.resolve("foo.txt");
    Files.writeString(testFile1, "copybara");
    Path testFile2 = workdir.resolve("bar.txt");
    Files.writeString(testFile2, "baracopy");
    Path testFile3 = workdir.resolve("directory/file_in_dir.txt");
    Files.writeString(testFile3, "hello");
    Path testFile4 = workdir.resolve("directory/subdir/file_in_subdir.txt");
    Files.writeString(testFile4, "world");

    // Create the test zip archive.
    Path testZip = workdir.resolve("test.zip");
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(testZip))) {
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

  @Test
  @TestParameters({
    "{ fileExtension: 'zip' }",
    "{ fileExtension: 'jar' }",
    "{ fileExtension: 'tar' }",
    "{ fileExtension: 'tar.gz' }",
    "{ fileExtension: 'tar.xz' }",
  })
  public void testCreateArchive_allFileExtensions(String fileExtension) throws Exception {
    // The directory where the generated archive is to be stored.
    Path archiveResultDir = workdir.resolve("archive_result");
    Files.createDirectories(archiveResultDir);

    // The directory where the unarchived files are to be stored.
    Path unarchiveResultDir = workdir.resolve("unarchive_result");
    Files.createDirectories(unarchiveResultDir);

    TransformWork work1 = TransformWorks.of(workdir, "test", console);
    String archiveName = "archive_result/test." + fileExtension;
    Transformation t1 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.create(\n"
                + "       archive = ctx.new_path(\""
                + archiveName
                + "\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t1.transform(work1);
    assertThat(Files.exists(workdir.resolve(archiveName))).isTrue();

    TransformWork work2 = TransformWorks.of(workdir, "test", console);
    Transformation t2 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.extract(\n"
                + "       archive = ctx.new_path(\""
                + archiveName
                + "\"),\n"
                + "       destination_folder = ctx.new_path(\"unarchive_result\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t2.transform(work2);
    Path resultPath = workdir.resolve("unarchive_result");
    FileSubjects.assertThatPath(resultPath).containsFile("foo.txt", "copybara");
    FileSubjects.assertThatPath(resultPath).containsFile("bar.txt", "baracopy");
    FileSubjects.assertThatPath(resultPath).containsFile("directory/file_in_dir.txt", "hello");
    FileSubjects.assertThatPath(resultPath)
        .containsFile("directory/subdir/file_in_subdir.txt", "world");
  }

  @Test
  public void testCreateArchive_withGlob() throws Exception {
    // The directory where the unarchived files are to be stored.
    Path unarchiveResultDir = workdir.resolve("unarchive_result");
    Files.createDirectories(unarchiveResultDir);

    TransformWork work1 = TransformWorks.of(workdir, "test", console);
    Transformation t1 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.create(\n"
                + "       archive = ctx.new_path(\"test.zip\"),\n"
                + "       files = glob([\"bar.txt\", \"**file_in_subdir.txt\"]),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t1.transform(work1);
    assertThat(Files.exists(workdir.resolve("test.zip"))).isTrue();

    TransformWork work2 = TransformWorks.of(workdir, "test", console);
    Transformation t2 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.extract(\n"
                + "       archive = ctx.new_path(\"test.zip\"),\n"
                + "       destination_folder = ctx.new_path(\"unarchive_result\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    t2.transform(work2);
    Path resultPath = workdir.resolve("unarchive_result");
    FileSubjects.assertThatPath(resultPath).containsFile("bar.txt", "baracopy");
    FileSubjects.assertThatPath(resultPath)
        .containsFile("directory/subdir/file_in_subdir.txt", "world");
  }

  @Test
  public void testCreateArchive_invalidType_throwsException() throws Exception {
    // The directory where the unarchived files are to be stored.
    Path unarchiveResultDir = workdir.resolve("unarchive_result");
    Files.createDirectories(unarchiveResultDir);

    TransformWork work1 = TransformWorks.of(workdir, "test", console);
    Transformation t1 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.create(\n"
                + "       archive = ctx.new_path(\"test.abc\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    ValidationException thrown = assertThrows(ValidationException.class, () -> t1.transform(work1));
    assertThat(thrown)
        .hasMessageThat()
        .contains("The archive type couldn't be inferred for the file: test.abc");
  }

  @Test
  public void testCreateArchive_fileLocked_throwsException() throws Exception {
    // The directory where the unarchived files are to be stored.
    Path unarchiveResultDir = workdir.resolve("unarchive_result");
    Files.createDirectories(unarchiveResultDir);
    Files.createFile(workdir.resolve("test.tar"));

    // Simulate a locked file by creating a read-only file
    Files.setPosixFilePermissions(
        workdir.resolve("test.tar"), ImmutableSet.of(PosixFilePermission.OWNER_READ));

    TransformWork work1 = TransformWorks.of(workdir, "test", console);
    Transformation t1 =
        skylark.eval(
            "t",
            ""
                + "def test(ctx):\n"
                + "   archive.create(\n"
                + "       archive = ctx.new_path(\"test.tar\"),\n"
                + "   )\n"
                + "t = core.dynamic_transform(test)");

    ValidationException thrown = assertThrows(ValidationException.class, () -> t1.transform(work1));
    assertThat(thrown)
        .hasMessageThat()
        // AccessDeniedException is a subclass of IOException.
        .contains("There was an error creating the archive: java.nio.file.AccessDeniedException");
  }
}
