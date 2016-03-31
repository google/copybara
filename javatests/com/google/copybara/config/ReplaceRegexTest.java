// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class ReplaceRegexTest {

  private Path root;
  private ReplaceRegex.Yaml yaml;

  @Before
  public void setup() throws IOException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    root = fileSystem.getPath("/");
    yaml = new ReplaceRegex.Yaml();
  }
  @Test
  public void invalidRegex() {
    yaml.setRegex("(unfinished group");
    yaml.setReplacement("asdf");
    try {
      yaml.withOptions(new Options(new GeneralOptions()));
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
      yaml.withOptions(new Options(new GeneralOptions()));
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
    Transformation transformation = yaml.withOptions(new Options(new GeneralOptions()));

    Path file1 = root.resolve("file1.txt");
    Files.write(file1, "foo".getBytes());
    Path file2 = root.resolve("file2.txt");
    Files.write(file2, "foo\nbaz\nfoo".getBytes());
    Path file3 = root.resolve("file3.txt");
    Files.write(file3, "bazbazbaz".getBytes());
    BasicFileAttributes before = Files.readAttributes(file3, BasicFileAttributes.class);
    transformation.transform(root);
    assertThat(new String(Files.readAllBytes(file1))).isEqualTo("bar");
    assertThat(new String(Files.readAllBytes(file2))).isEqualTo("bar\nbaz\nbar");
    assertThat(new String(Files.readAllBytes(file3))).isEqualTo("bazbazbaz");

    BasicFileAttributes after = Files.readAttributes(file3, BasicFileAttributes.class);

    // No file modification is done if the match is not found
    assertThat(before.lastModifiedTime()).isEqualTo(after.lastModifiedTime());
  }

  @Test
  public void testWithGroups() throws IOException {
    yaml.setRegex("foo(.*)bar");
    yaml.setReplacement("bar$1foo");
    Transformation transformation = yaml.withOptions(new Options(new GeneralOptions()));

    Path file1 = root.resolve("file1.txt");
    Files.write(file1, "fooBAZbar".getBytes());
    transformation.transform(root);

    assertThat(new String(Files.readAllBytes(file1))).isEqualTo("barBAZfoo");
  }
}
