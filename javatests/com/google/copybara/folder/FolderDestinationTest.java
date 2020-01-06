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

package com.google.copybara.folder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination;
import com.google.copybara.Revision;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.DirFactory;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FolderDestinationTest {

  private OptionsBuilder options;
  private ImmutableList<String> excludedPathsForDeletion;

  private Path workdir;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setHomeDir(StandardSystemProperty.USER_HOME.value());
    Files.createDirectory(workdir.resolve("dir"));
    Files.write(workdir.resolve("test.txt"), new byte[]{});
    Files.write(workdir.resolve("dir/file.txt"), new byte[]{});
    excludedPathsForDeletion = ImmutableList.of();
    skylark = new SkylarkTestExecutor(options);
  }

  private void write() throws ValidationException, RepoException, IOException {
    WriterContext writerContext =
        new WriterContext("FolderDestinationTest", "test", false, new DummyRevision("origin_ref"),
            Glob.ALL_FILES.roots());
    skylark
        .<Destination<Revision>>eval("dest", "dest = folder.destination()")
        .newWriter(writerContext)
        .write(
            TransformResults.of(workdir, new DummyRevision("origin_ref")),
            Glob.createGlob(ImmutableList.of("**"), excludedPathsForDeletion),
            options.general.console());
  }

  @Test
  public void testDeleteWithEmptyExcludes() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Path localFolder = Files.createTempDirectory("local_folder");

    Files.write(workdir.resolve("file1.txt"), new byte[]{});
    Files.write(localFolder.resolve("file2.txt"), new byte[]{});

    options.folderDestination.localFolder = localFolder.toString();

    write();

    assertThatPath(localFolder)
        .containsFiles("file1.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testRelativePaths() throws Exception {
    Path dest = Files.createDirectories(Files.createTempDirectory("workdir").resolve("one"));
    workdir = Files.createTempDirectory("local_folder");

    Files.write(Files.createDirectories(dest.resolve("folder")).resolve("file1.txt"),
        "foo".getBytes(UTF_8));
    Files.write(Files.createDirectories(workdir.resolve("folder")).resolve("file1.txt"),
        "bar".getBytes(UTF_8));

    options.setWorkdirToRealTempDir();
    options.folderDestination.localFolder = dest.resolve("../one").toString();

    WriterContext writerContext =
        new WriterContext("not_important", "not_important", false,
            new DummyRevision("not_important"), Glob.ALL_FILES.roots());
    skylark.<FolderDestination>eval("dest", "dest = folder.destination()")
        .newWriter(writerContext)
        .write(TransformResults.of(workdir, new DummyRevision("not_important")),
            Glob.createGlob(ImmutableList.of("folder/file1.txt"), ImmutableList.of()),
            options.general.console());

    assertThatPath(dest)
        .containsFile("folder/file1.txt", "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void testCopyWithExcludes() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");

    Files.createDirectory(localFolder.resolve("one"));
    Files.createDirectory(localFolder.resolve("two"));
    Files.write(localFolder.resolve("root_file"), new byte[]{});
    Files.write(localFolder.resolve("root_file2"), new byte[]{});
    Files.write(localFolder.resolve("one/file.txt"), new byte[]{});
    Files.write(localFolder.resolve("one/file.java"), new byte[]{});
    Files.write(localFolder.resolve("two/file.java"), new byte[]{});

    options.folderDestination.localFolder = localFolder.toString();
    excludedPathsForDeletion = ImmutableList.of("root_file", "**\\.java");

    write();

    assertThatPath(localFolder)
        .containsFiles("one", "two", "root_file",
            "one/file.java", "two/file.java", "test.txt", "dir/file.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testDefaultRoot() throws Exception {
    Path defaultRootPath = Files.createTempDirectory("defaultRoot");
    options.setHomeDir(defaultRootPath.toString());

    write();

    Path outputPath;
    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(defaultRootPath.resolve("copybara/" + DirFactory.TMP))) {
      outputPath = Iterables.getOnlyElement(stream);
    }

    assertThatPath(outputPath)
        .containsFiles("test.txt", "dir/file.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testAccessDenied() throws Exception {
    options.folderDestination.localFolder = "/foo-bar-123456789";
    ValidationException expected = assertThrows(ValidationException.class, () -> write());
    assertThat(expected).hasMessageThat().isEqualTo("Path is not accessible: /foo-bar-123456789");
  }
}
