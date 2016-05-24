package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    assertFileContents(left, "file1.txt", "foo");
    assertFileContents(left, "b/file2.txt", "bar");
    assertOnlyFiles(left, "file1.txt", "b/file2.txt");
    assertFileContents(right, "file1.txt", "new foo");
    assertFileContents(right, "c/file3.txt", "bar");
    assertOnlyFiles(right, "file1.txt", "c/file3.txt");
    assertFileContents(destination, "file1.txt", "new foo");
    assertFileContents(destination, "c/file3.txt", "bar");
    assertOnlyFiles(destination, "file1.txt", "c/file3.txt");
  }

  @Test
  public void applyEmptyDiff() throws Exception {
    writeFile(left, "file1.txt", "foo");
    writeFile(left, "b/file2.txt", "bar");
    DiffUtil.patch(left, /*empty diff*/ new byte[]{}, /*verbose*/ true);

    assertFileContents(left, "file1.txt", "foo");
    assertFileContents(left, "b/file2.txt", "bar");
    assertOnlyFiles(left, "file1.txt", "b/file2.txt");
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

  private void assertFileContents(Path parent, String expectedFilename, String expectedContents)
      throws IOException {
    assertFileContents(parent.resolve(expectedFilename), expectedContents);
  }

  private void assertFileContents(Path expectedFilePath, String expectedContents)
      throws IOException {
    assertWithMessage("Expected file not found %s", expectedFilePath)
        .that(Files.exists(expectedFilePath)).isTrue();
    String realContents = new String(Files.readAllBytes(expectedFilePath), StandardCharsets.UTF_8);
    assertWithMessage("Unexpected contents for file %s", expectedFilePath)
        .that(realContents).isEqualTo(expectedContents);
  }

  private void assertOnlyFiles(final Path parent, String... expectedFiles) throws IOException {
    final Set<String> onlyFiles = new HashSet<>(Arrays.asList(expectedFiles));
    Files.walkFileTree(parent, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!attrs.isDirectory()) {
          assertThat(onlyFiles).contains(parent.relativize(file).toString());
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
