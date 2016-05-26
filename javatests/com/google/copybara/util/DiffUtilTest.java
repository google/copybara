package com.google.copybara.util;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.testing.FileSubjects;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class DiffUtilTest {

  // Command requires the working dir as a File, and Jimfs does not support Path.toFile()
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private Path left;
  private Path right;
  private Path destination;

  @Before
  public void setUp() throws Exception {
    Path rootPath = tmpFolder.getRoot().toPath();
    left = createDir(rootPath, "left");
    right = createDir(rootPath, "right");
    destination = createDir(rootPath, "destination");
  }

  @Test
  public void pathsAreNotSiblings() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Paths 'one' and 'other' must be sibling directories");

    Path foo = createDir(left, "foo");
    DiffUtil.diff(left, foo, /*verbose*/ true);
  }

  @Test
  public void emptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "foo");
    writeFile(right, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right,/*verbose*/ true);

    assertThat(diffContents).isEmpty();
  }

  @Test
  public void applyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    writeFile(right, "file1.txt", "new foo");
    writeFile(right, "c/file3.txt", "bar");
    writeFile(destination, "file1.txt", "foo");
    writeFile(destination, "b/file2.txt", "bar");

    byte[] diffContents = DiffUtil.diff(left, right, /*verbose*/ true);

    DiffUtil.patch(destination, diffContents, /*verbose*/ true);

    assertAbout(FileSubjects.path())
        .that(left)
        .containsFile("file1.txt", "foo")
        .containsFile("b/file2.txt", "bar")
        .containsNoMoreFiles();
    assertAbout(FileSubjects.path())
        .that(right)
        .containsFile("file1.txt", "new foo")
        .containsFile("c/file3.txt", "bar")
        .containsNoMoreFiles();
    assertAbout(FileSubjects.path())
        .that(destination)
        .containsFile("file1.txt", "new foo")
        .containsFile("c/file3.txt", "bar")
        .containsNoMoreFiles();
  }

  @Test
  public void applyEmptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    DiffUtil.patch(left, /*empty diff*/ new byte[]{}, /*verbose*/ true);

    assertAbout(FileSubjects.path())
        .that(left)
        .containsFile("file1.txt", "foo")
        .containsFile("b/file2.txt", "bar")
        .containsNoMoreFiles();
  }

  private Path createDir(Path parent, String name) throws IOException {
    Path path = parent.resolve(name);
    Files.createDirectories(path);
    return path;
  }

  private void writeFile(Path parent, String fileName, String fileContents) throws IOException {
    Path filePath = parent.resolve(fileName);
    Files.createDirectories(filePath.getParent());
    Files.write(parent.resolve(filePath), fileContents.getBytes(StandardCharsets.UTF_8));
  }
}
