// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@RunWith(JUnit4.class)
public final class ReplaceTest {

  private Path root;
  private Replace.Yaml yaml;
  private Options options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    yaml = new Replace.Yaml();
    GeneralOptions generalOptions = new GeneralOptions(fs.getPath("/"), /*verbose=*/true);
    options = new Options(ImmutableList.of(generalOptions));
    root = generalOptions.getWorkdir();
  }

  @Test
  public void invalidRegex() throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'regexGroups' includes invalid regex for key foo");
    yaml.setRegexGroups(ImmutableMap.of("foo", "(unfinished group"));
  }

  @Test
  public void missingReplacement() throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("missing required field 'after'");
    yaml.setBefore("asdf");
    yaml.withOptions(options);
  }

  @Test
  public void testSimpleReplaceWithoutGroups() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
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
  public void testWithGroups() throws Exception {
    yaml.setBefore("foo${middle}bar");
    yaml.setAfter("bar${middle}foo");
    yaml.setRegexGroups(ImmutableMap.of("middle", ".*"));
    Transformation transformation = yaml.withOptions(options);

    Path file1 = root.resolve("file1.txt");
    writeFile(file1, "fooBAZbar");
    transformation.transform(root);

    assertFileContents("file1.txt", "barBAZfoo");
  }

  @Test
  public void testWithGlob() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
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
  public void testWithGlobFolderPrefix() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
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
  public void testWithGlobFolderPrefixUnlikeBash() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
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

  @Test
  public void testUsesTwoDifferentGroups() throws Exception {
    yaml.setBefore("bef${a}ore${b}");
    yaml.setAfter("af${b}ter${a}");
    yaml.setRegexGroups(ImmutableMap.of(
            "a", "a+",
            "b", "[bB]"));

    writeFile(root.resolve("before_and_after"), ""
        + "not a match: beforeB\n"
        + "is a match: befaaaaaoreB # trailing content\n");

    yaml.withOptions(options).transform(root);

    assertFileContents("before_and_after", ""
        + "not a match: beforeB\n"
        + "is a match: afBteraaaaa # trailing content\n");
  }

  @Test
  public void beforeUsesUndeclaredGroup() throws ConfigValidationException {
    yaml.setBefore("foo${bar}${baz}");
    yaml.setAfter("foo${baz}");
    yaml.setRegexGroups(ImmutableMap.of("baz", ".*"));

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("used but not defined: [bar]");
    yaml.withOptions(options);
  }

  @Test
  public void afterUsesUndeclaredGroup() throws ConfigValidationException {
    yaml.setBefore("foo${bar}${iru}");
    yaml.setAfter("foo${bar}");
    yaml.setRegexGroups(ImmutableMap.of("bar", ".*"));

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("used but not defined: [iru]");
    yaml.withOptions(options);
  }

  @Test
  public void beforeDoesNotUseADeclaredGroup() throws ConfigValidationException {
    yaml.setBefore("foo${baz}");
    yaml.setAfter("foo${baz}${bar}");
    yaml.setRegexGroups(ImmutableMap.of(
            "baz", ".*",
            "bar", "[a-z]+"));

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("defined but not used: [bar]");
    yaml.withOptions(options);
  }

  @Test
  public void afterDoesNotUseADeclaredGroup() throws ConfigValidationException {
    yaml.setBefore("foo${baz}${bar}");
    yaml.setAfter("foo${baz}");
    yaml.setRegexGroups(ImmutableMap.of(
            "baz", ".*",
            "bar", "[a-z]+"));

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("defined but not used: [bar]");
    yaml.withOptions(options);
  }

  @Test
  public void categoryComplementDoesNotSpanLine() throws Exception {
    yaml.setBefore("bef${a}ore");
    yaml.setAfter("aft${a}er");
    yaml.setRegexGroups(ImmutableMap.of("a", "[^/]+"));

    writeFile(root.resolve("before_and_after"), ""
        + "obviously match: befASDFore/\n"
        + "should not match: bef\n"
        + "ore");

    yaml.withOptions(options).transform(root);

    assertFileContents("before_and_after", ""
        + "obviously match: aftASDFer/\n"
        + "should not match: bef\n"
        + "ore");
  }

  @Test
  public void multipleMatchesPerLine() throws Exception {
    yaml.setBefore("before");
    yaml.setAfter("after");
    writeFile(root.resolve("before_and_after"), "before ... still before");

    yaml.withOptions(options).transform(root);

    assertFileContents("before_and_after", "after ... still after");
  }

  @Test
  public void errorIfNotRoundTrippable() throws Exception {
    yaml.setBefore("before");
    yaml.setAfter("after");
    writeFile(root.resolve("before_and_after"), "before_and_after");

    thrown.expect(NotRoundtrippableException.class);
    thrown.expectMessage("before_and_after");

    yaml.withOptions(options).transform(root);
  }

  @Test
  public void showOriginalTemplateInToString() throws ConfigValidationException {
    yaml.setBefore("a${b}c");
    yaml.setAfter("c${b}a");
    yaml.setRegexGroups(ImmutableMap.of("b", ".*"));
    String string = yaml.withOptions(options).toString();
    assertThat(string).contains("before=a${b}c");
    assertThat(string).contains("after=c${b}a");
  }

  @Test
  public void showOriginalGlobInToString() throws ConfigValidationException {
    yaml.setBefore("before");
    yaml.setAfter("after");
    yaml.setPath("foo/**/bar.htm");
    String string = yaml.withOptions(options).toString();
    assertThat(string).contains("path=foo/**/bar.htm");
  }

  @Test
  public void showReasonableDefaultGlobInToString() throws ConfigValidationException {
    yaml.setBefore("before");
    yaml.setAfter("after");
    String string = yaml.withOptions(options).toString();
    assertThat(string).contains("path=**");
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
