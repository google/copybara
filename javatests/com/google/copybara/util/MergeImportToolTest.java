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

import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
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
  private Path diff3Workdir;
  private Diff3Util diff3Util;
  private MergeImportTool underTest;
  private TestingConsole console;

  @Before
  public void setUp() throws IOException {
    Path rootPath = tmpFolder.getRoot().toPath();
    originWorkdir = createDir(rootPath, "origin");
    destinationWorkdir = createDir(rootPath, "destination");
    baselineWorkdir = createDir(rootPath, "baseline");
    diff3Workdir = createDir(rootPath, "diff3");
    console = new TestingConsole();
    diff3Util = new Diff3Util(DIFF3_BIN, null);
    underTest = new MergeImportTool(console, diff3Util);
  }

  @Test
  public void testMergeSuccess() throws Exception {
    String fileName = "foo.txt";
    String commonFileContents = "a\nb\nc\n";
    writeFile(baselineWorkdir, fileName, commonFileContents);
    writeFile(originWorkdir, fileName, "foo\n".concat(commonFileContents));
    writeFile(destinationWorkdir, fileName, commonFileContents.concat("bar\n"));

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diff3Workdir);

    assertThat(Files.readString(originWorkdir.resolve(fileName)))
        .isEqualTo("foo\n".concat(commonFileContents).concat("bar\n"));
  }

  @Test
  public void testDestinationOnlyFilePersists() throws Exception {
    // file only exists in destination, not in baseline or origin
    String destinationFilename = "destination.txt";
    String destinationFileContents = "destination only stuff";
    writeFile(destinationWorkdir, destinationFilename, "destination only stuff");

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diff3Workdir);

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

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diff3Workdir);

    assertThat(Files.exists(originWorkdir.resolve(fileName))).isFalse();
  }

  @Test
  public void testMergeConflictShownInConsole() throws Exception {
    String fileName = "foo.txt";
    writeFile(baselineWorkdir, fileName, "a\nb\nc\n");
    writeFile(originWorkdir, fileName, "d\ne\nf\n");
    writeFile(destinationWorkdir, fileName, "g\nh\ni");

    underTest.mergeImport(originWorkdir, destinationWorkdir, baselineWorkdir, diff3Workdir);

    assertThat(console.getMessages().stream().map(Message::getText).collect(Collectors.toList()))
        .contains(String.format("Merge error for path %s", originWorkdir.resolve(fileName)));
  }

  private Path createDir(Path parent, String name) throws IOException {
    Path path = parent.resolve(name);
    Files.createDirectories(path);
    return path;
  }

  private void writeFile(Path parent, String fileName, String fileContents) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.writeString(parent.resolve(filePath), fileContents);
  }
}
