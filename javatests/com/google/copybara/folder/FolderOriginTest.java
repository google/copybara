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
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.junit.Assert.assertThrows;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FolderOriginTest {

  private OptionsBuilder options;

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  private Path workdir;
  private SkylarkTestExecutor skylark;

  private final Authoring authoring = new Authoring(new Author("foo", "default@example.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());


  @Before
  public void setup() throws IOException, RepoException {
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setHomeDir(StandardSystemProperty.USER_HOME.value());
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void simpleTest() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");
    touch(localFolder.resolve("foo/file1"), "one");
    touch(localFolder.resolve("foo/file2"), "two");
    touch(localFolder.resolve("file3"), "three");
    touch(localFolder.resolve("file4"), "four");
    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    reader.checkout(ref, workdir);
    assertThatPath(workdir)
        .containsFile("foo/file1","one")
        .containsFile("foo/file2","two")
        .containsFile("file3","three")
        .containsFile("file4","four")
        .containsNoMoreFiles();
  }

  @Test
  public void testChangesFilePermissions() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");
    Path regularFile = localFolder.resolve("foo/regular_file");
    Path cannotWrite = localFolder.resolve("foo/file_cannot_write");
    Path executableCannotWrite = localFolder.resolve("foo/executable_cannot_write");
    touch(regularFile, "one");
    touch(cannotWrite, "two");
    touch(executableCannotWrite, "three");

    // Regular file already has [GROUP_READ, OWNER_WRITE, OWNER_READ, OTHERS_READ]
    // Removing write permissions from the second file
    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(cannotWrite);
    permissions.removeAll(ImmutableSet.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE));
    Files.setPosixFilePermissions(cannotWrite, permissions);
    // Setting executable and removing write for third one
    Set<PosixFilePermission> secondPermissions =
        Files.getPosixFilePermissions(executableCannotWrite);
    secondPermissions.removeAll(ImmutableSet.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE));
    secondPermissions.add(OWNER_EXECUTE);
    Files.setPosixFilePermissions(executableCannotWrite, secondPermissions);

    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    reader.checkout(ref, workdir);
    assertThatPath(workdir)
        .containsFile("foo/regular_file","one")
        .containsFile("foo/file_cannot_write","two")
        .containsFile("foo/executable_cannot_write","three")
        .containsNoMoreFiles();
    Path cannotWriteDestination = workdir.resolve("foo/file_cannot_write");
    assertThat(Files.getPosixFilePermissions(cannotWriteDestination))
        .containsAtLeast(OWNER_READ, OWNER_WRITE);
    Path executableCannotWriteDestination = workdir.resolve("foo/executable_cannot_write");
    assertThat(Files.getPosixFilePermissions(executableCannotWriteDestination))
        .containsAtLeast(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
  }

  @Test
  public void testWithGlob() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");
    touch(localFolder.resolve("foo/file1"), "one");
    touch(localFolder.resolve("foo/file2"), "two");
    touch(localFolder.resolve("bar/bar"), "bar");
    touch(localFolder.resolve("file3"), "three");
    touch(localFolder.resolve("file4"), "four");
    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(
        Glob.createGlob(ImmutableSet.of("foo/*1", "file4")), authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    reader.checkout(ref, workdir);
    assertThatPath(workdir)
        .containsFile("foo/file1", "one")
        .containsFile("file4", "four")
        .containsNoMoreFiles();
  }

  /**
   * Regression test for folder.origin refs that contain '..' and globs that wouldn't match
   * globs like foo/../foo/file if we didn't normalize symlinks before.
   */
  @Test
  public void testDotDot() throws Exception {
    Path localFolder = Files.createDirectory(
        Files.createTempDirectory("local_folder").resolve("root"));

    touch(localFolder.resolve("file1"), "one");
    Files.createSymbolicLink(localFolder.resolve("file2"),
        localFolder.getFileSystem().getPath("file1"));

    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(
        Glob.createGlob(ImmutableSet.of("root/file*")), authoring);
    FolderRevision ref = origin.resolve(localFolder.resolve("..").toString());
    reader.checkout(ref, workdir);
    assertThatPath(workdir)
        .containsFile("root/file1", "one")
        .containsSymlink("root/file2", "root/file1")
        .containsNoMoreFiles();
  }

  private Path touch(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.write(file, content.getBytes(UTF_8));
  }

  @Test
  public void testChangesWithDefaults() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");
    Change<FolderRevision> change = getChange(localFolder);

    assertThat(change.getAuthor().toString()).isEqualTo("Copybara <noreply@copybara.io>");
    assertThat(change.getMessage()).isEqualTo("Copybara code migration");
    assertThat(change.getRevision().asString()).isEqualTo(localFolder.toString());
  }

  private Change<FolderRevision> getChange(Path localFolder)
      throws ValidationException, RepoException {
    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    Change<FolderRevision> change = reader.change(ref);

    assertThat(
        Iterables.getOnlyElement(reader.changes(/*fromRef=*/ null, ref).getChanges()))
        .isEqualTo(change);
    return change;
  }

  @Test
  public void testChangesWithFlags() throws Exception {
    options.folderOrigin.author = "Foo <foo@foo.bar>";
    options.folderOrigin.message = "A message";
    Path localFolder = Files.createTempDirectory("local_folder");
    Change<FolderRevision> change = getChange(localFolder);

    assertThat(change.getAuthor().toString()).isEqualTo("Foo <foo@foo.bar>");
    assertThat(change.getMessage()).isEqualTo("A message");
    assertThat(change.getRevision().asString()).isEqualTo(localFolder.toString());
  }

  @Test
  public void testAbsolutePaths() throws Exception {
    ValidationException thrown =
        assertThrows(ValidationException.class, () -> runAbsolutePaths("folder.origin()"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Some symlinks refer to locations outside of the folder and"
                + " 'materialize_outside_symlinks' config option was not used");
  }

  @Test
  public void testAbsolutePathsMaterialize() throws Exception {
    runAbsolutePaths("folder.origin(materialize_outside_symlinks = True)");

    assertThatPath(workdir)
        .containsFile("foo", "abc")
        .containsNoMoreFiles();
    assertThat(Files.isSymbolicLink(workdir.resolve("foo"))).isFalse();
  }

  @Test
  public void testInvalidSymlinks() throws Exception {
    RepoException e = assertThrows(RepoException.class, () -> checkInvalidSymlink());
    assertThat(e).hasCauseThat().isInstanceOf(NoSuchFileException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("foo");
  }

  @Test
  public void testInvalidSymlinks_ignore() throws Exception {
    options.folderOrigin.ignoreInvalidSymlinks = true;
    checkInvalidSymlink();
  }

  private void checkInvalidSymlink()
      throws IOException, ValidationException, RepoException {
    Path localFolder = Files.createTempDirectory("local_folder");
    Files.createSymbolicLink(localFolder.resolve("foo"), localFolder.resolve("invalid"));

    FolderOrigin origin = skylark.eval("f", "f = " + "folder.origin()");

    Reader<FolderRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    reader.checkout(ref, workdir);

    assertThatPath(workdir).containsNoMoreFiles();
  }

  @Test
  public void testRelativePath() throws ValidationException, RepoException, IOException {
    Path folder = Files.createDirectories(tmpFolder.getRoot().toPath().resolve("folder"));
    Files.createDirectory(folder.resolve("foo"));
    options.setForce(false);
    options.setWorkdirToRealTempDir(folder.toAbsolutePath().toString());
    FolderOrigin origin = skylark.eval("f", "f = folder.origin()");
    // Check that the path is stored as absolute, since recursive copy only supports absolute paths
    assertThat(origin.resolve("foo").path.isAbsolute()).isTrue();
  }

  private void runAbsolutePaths(String originStr)
      throws IOException, ValidationException, RepoException {
    Path other = Files.createTempDirectory("other");
    touch(other.resolve("foo"),"abc");

    Path localFolder = Files.createTempDirectory("local_folder");
    Files.createSymbolicLink(localFolder.resolve("foo"), other.resolve("foo"));

    FolderOrigin origin = skylark.eval("f", "f = " + originStr);

    Reader<FolderRevision> reader = origin.newReader(Glob.ALL_FILES, authoring);
    FolderRevision ref = origin.resolve(localFolder.toString());
    reader.checkout(ref, workdir);
  }
}
