// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@RunWith(JUnit4.class)
public final class ReplaceRegexTest {

  private Path root;
  private ReplaceRegex.Yaml yaml;
  private Options options;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    yaml = new ReplaceRegex.Yaml();
    GeneralOptions generalOptions = new GeneralOptions(fs);
    generalOptions.init();
    options = new Options(generalOptions);
    root = generalOptions.getWorkdir();
  }

  @Test
  public void invalidRegex() {
    yaml.setRegex("(unfinished group");
    yaml.setReplacement("asdf");
    try {
      yaml.withOptions(options);
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("not a valid regex");
    }
  }

  @Test
  public void missingReplacement() {
    yaml.setRegex("asdf");
    try {
      yaml.withOptions(options);
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("missing required field 'replacement'");
    }
  }

  @Test
  public void testSimpleRegex() throws IOException {
    yaml.setRegex("foo");
    yaml.setReplacement("bar");
    Transformation transformation = yaml.withOptions(options);

    Path file1 = root.resolve("file1.txt");
    writeFile(file1, "foo");
    Path file2 = root.resolve("file2.txt");
    writeFile(file2, "foo\nbaz\nfoo");
    Path file3 = root.resolve("file3.txt");
    writeFile(file3, "bazbazbaz");
    BasicFileAttributes before = Files.readAttributes(file3, BasicFileAttributes.class);
    transformation.transform(root);
    assertFileContents("file1.txt", "bar");
    assertFileContents("file2.txt", "bar\nbaz\nbar");
    assertFileContents("file3.txt", "bazbazbaz");

    BasicFileAttributes after = Files.readAttributes(file3, BasicFileAttributes.class);

    // No file modification is done if the match is not found
    assertThat(before.lastModifiedTime()).isEqualTo(after.lastModifiedTime());
  }

  @Test
  public void testWithGroups() throws IOException {
    yaml.setRegex("foo(.*)bar");
    yaml.setReplacement("bar$1foo");
    Transformation transformation = yaml.withOptions(options);

    Path file1 = root.resolve("file1.txt");
    writeFile(file1, "fooBAZbar");
    transformation.transform(root);

    assertFileContents("file1.txt", "barBAZfoo");
  }

  @Test
  public void testWithGlob() throws IOException {
    yaml.setRegex("foo");
    yaml.setReplacement("bar");
    yaml.setPath("**.java");
    Transformation transformation = yaml.withOptions(options);

    prepareGlobTree();

    transformation.transform(root);

    assertFileContents("file1.txt", "foo");
    assertFileContents("file1.java", "bar");
    assertFileContents("folder/file1.txt", "foo");
    assertFileContents("folder/file1.java", "bar");
    assertFileContents("folder/subfolder/file1.java", "bar");

  }

  @Test
  public void testWithGlobFolderPrefix() throws IOException {
    yaml.setRegex("foo");
    yaml.setReplacement("bar");
    yaml.setPath("folder/**.java");
    Transformation transformation = yaml.withOptions(options);

    prepareGlobTree();

    transformation.transform(root);

    assertFileContents("file1.txt", "foo");
    assertFileContents("file1.java", "foo");
    assertFileContents("folder/file1.txt", "foo");
    assertFileContents("folder/file1.java", "bar");
    assertFileContents("folder/subfolder/file1.java", "bar");
  }

  @Test
  public void testWithGlobFolderPrefixUnlikeBash() throws IOException {
    yaml.setRegex("foo");
    yaml.setReplacement("bar");
    yaml.setPath("folder/**/*.java");
    Transformation transformation = yaml.withOptions(options);

    prepareGlobTree();

    transformation.transform(root);

    assertFileContents("file1.txt", "foo");
    assertFileContents("file1.java", "foo");
    assertFileContents("folder/file1.txt", "foo");
    // The difference between Java Glob PathMatcher and Bash is that
    // '/**/' is treated as at least one folder.
    assertFileContents("folder/file1.java", "foo");
    assertFileContents("folder/subfolder/file1.java", "bar");
  }

  private void prepareGlobTree() throws IOException {
    writeFile(root.resolve("file1.txt"), "foo");
    writeFile(root.resolve("file1.java"), "foo");
    Files.createDirectories(root.resolve("folder/subfolder"));
    writeFile(root.resolve("folder/file1.txt"), "foo");
    writeFile(root.resolve("folder/file1.java"), "foo");
    writeFile(root.resolve("folder/subfolder/file1.java"), "foo");
  }

  private void assertFileContents(String path, String expectedText) throws IOException {
    assertThat(new String(Files.readAllBytes(root.resolve(path)))).isEqualTo(expectedText);
  }

  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes());
  }
}
