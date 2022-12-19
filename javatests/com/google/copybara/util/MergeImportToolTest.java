/*
 * Copyright (C) 2022 Google Inc.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MergeImportToolTest {

  private static final String DIFF3_BIN = "/usr/bin/diff3";

  @Rule public final TemporaryFolder tmpFolder = new TemporaryFolder();
  private Path originWorkdir;
  private Path destinationWorkdir;
  private Path baselineWorkdir;
  private Path diffToolWorkdir;
  private CommandLineDiffUtil commandLineDiffUtil;
  private MergeImportTool underTest;
  private TestingConsole console;

  @Before
  public void setUp() throws IOException {
    Path rootPath = tmpFolder.getRoot().toPath();
    originWorkdir = createDir(rootPath, "origin");
    destinationWorkdir = createDir(rootPath, "destination");
    baselineWorkdir = createDir(rootPath, "baseline");
    diffToolWorkdir = createDir(rootPath, "diffTool");
    console = new TestingConsole();
    commandLineDiffUtil = new CommandLineDiffUtil(DIFF3_BIN, null);
    underTest = new MergeImportTool(console, commandLineDiffUtil, 10);
  }

  @Test
  public void testMergeSuccess() throws Exception {
    String fileName = "foo.txt";
    String commonFileContents = "a\nb\nc\n";
    writeFile(baselineWorkdir, fileName, commonFileContents);
    writeFile(originWorkdir, fileName, "foo\n".concat(commonFileContents));
    writeFile(destinationWorkdir, fileName, commonFileContents.concat("bar\n"));

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diffToolWorkdir);

    assertThat(Files.readString(originWorkdir.resolve(fileName)))
        .isEqualTo("foo\n".concat(commonFileContents).concat("bar\n"));
  }

  @Test
  public void testDestinationOnlyFilePersists() throws Exception {
    // file only exists in destination, not in baseline or origin
    String destinationFilename = "foo/destination.txt";
    String destinationFileContents = "destination only stuff";
    writeFile(destinationWorkdir, destinationFilename, "destination only stuff");

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diffToolWorkdir);

    assertThat(Files.readString(originWorkdir.resolve(destinationFilename)))
        .isEqualTo(destinationFileContents);
  }

  @Test
  public void testDeletedFileDoesNotPersist() throws Exception {
    // file exists at baseline and in destination, but not in origin
    String fileName = "foo.txt";
    String fileContents = "a\nb\nc\n";
    writeFile(baselineWorkdir, fileName, fileContents);
    writeFile(destinationWorkdir, fileName, fileContents);

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diffToolWorkdir);

    assertThat(Files.exists(originWorkdir.resolve(fileName))).isFalse();
  }

  @Test
  public void testBinaryFileSkipped() throws Exception {
    String fileName = "foo.bin";
    Files.createDirectories(originWorkdir.resolve(fileName).getParent());
    writeBinaryFile(originWorkdir, fileName, new byte[] {1, 2, 3, 4, 5});
    Files.createDirectories(baselineWorkdir.resolve(fileName).getParent());
    writeBinaryFile(baselineWorkdir, fileName, new byte[] {});
    Files.createDirectories(destinationWorkdir.resolve(fileName).getParent());
    writeBinaryFile(
        destinationWorkdir,
        fileName,
        // captured from problematic binary file
        "\n˝\u000E\n#unittest_import_public_proto3.proto\u0012\u0018protobuf_unittest_import\"#\n\u0013PublicImportMessage\u0012\f\n\u0001e\u0018\u0001 \u0001(\u0005R\u0001eB\u001D™\u0002\u001AGoogle.Protobuf.TestProtosJÔ\n\u0006\u0012\u0004 \u0000(\u0001\nˆ\f\n \u0001\f\u0012\u0003 \u0000\u00122¡\f"
            .getBytes(UTF_8));

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diffToolWorkdir);

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            String.format(
                "diff3 exited with code 2 for path %s, skipping", originWorkdir.resolve(fileName)));
  }

  @Test
  public void testMergeConflict() throws Exception {
    String fileName = "foo.txt";
    writeFile(baselineWorkdir, fileName, "a\nb\nc\n");
    writeFile(originWorkdir, fileName, "d\ne\nf\n");
    writeFile(destinationWorkdir, fileName, "g\nh\ni");

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diffToolWorkdir);

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            String.format("Merge error for path %s", originWorkdir.resolve(fileName)));
    assertThat(Files.readString(originWorkdir.resolve(fileName)))
        .isEqualTo(
            String.format(
                "<<<<<<<"
                    + " %s/foo.txt\n"
                    + "d\n"
                    + "e\n"
                    + "f\n"
                    + "|||||||"
                    + " %s/foo.txt\n"
                    + "a\n"
                    + "b\n"
                    + "c\n"
                    + "=======\n"
                    + "g\n"
                    + "h\n"
                    + "i>>>>>>>"
                    + " %s/foo.txt\n",
                originWorkdir, baselineWorkdir, destinationWorkdir));
  }

  private Path createDir(Path parent, String name) throws IOException {
    Path path = parent.resolve(name);
    Files.createDirectories(path);
    return path;
  }

  private void writeBinaryFile(Path parent, String fileName, byte[] fileContents) throws Exception {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.write(filePath, fileContents);
  }

  private void writeFile(Path parent, String fileName, String fileContents) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.writeString(parent.resolve(filePath), fileContents);
  }
}
