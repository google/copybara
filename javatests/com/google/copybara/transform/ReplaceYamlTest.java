// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReplaceYamlTest {

  private Replace.Yaml yaml;
  private OptionsBuilder options;
  private Path workdir;
  private TestingConsole console;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/");
    Files.createDirectories(workdir);
    yaml = new Replace.Yaml();
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
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
    yaml.withOptions(options.build());
  }

  @Test
  public void testSimpleReplaceWithoutGroups() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
    Transformation transformation = yaml.withOptions(options.build());

    Path file1 = workdir.resolve("file1.txt");
    writeFile(file1, "foo");
    Path file2 = workdir.resolve("file2.txt");
    writeFile(file2, "foo\nbaz\nfoo");
    Path file3 = workdir.resolve("file3.txt");
    writeFile(file3, "bazbazbaz");
    BasicFileAttributes before = Files.readAttributes(file3, BasicFileAttributes.class);
    transformation.transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file1.txt", "bar")
        .containsFile("file2.txt", "bar\nbaz\nbar")
        .containsFile("file3.txt", "bazbazbaz");

    BasicFileAttributes after = Files.readAttributes(file3, BasicFileAttributes.class);

    // No file modification is done if the match is not found
    assertThat(before.lastModifiedTime()).isEqualTo(after.lastModifiedTime());
  }

  @Test
  public void testWithGroups() throws Exception {
    yaml.setBefore("foo${middle}bar");
    yaml.setAfter("bar${middle}foo");
    yaml.setRegexGroups(ImmutableMap.of("middle", ".*"));
    Transformation transformation = yaml.withOptions(options.build());

    Path file1 = workdir.resolve("file1.txt");
    writeFile(file1, "fooBAZbar");
    transformation.transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file1.txt", "barBAZfoo");
  }

  @Test
  public void testWithGlob() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
    yaml.setPath("**.java");
    Transformation transformation = yaml.withOptions(options.build());

    prepareGlobTree();

    transformation.transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "bar")
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/file1.java", "bar")
        .containsFile("folder/subfolder/file1.java", "bar");
  }

  @Test
  public void testWithGlobFolderPrefix() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
    yaml.setPath("folder/**.java");
    Transformation transformation = yaml.withOptions(options.build());

    prepareGlobTree();

    transformation.transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "foo")
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/file1.java", "bar")
        .containsFile("folder/subfolder/file1.java", "bar");
  }

  @Test
  public void testWithGlobFolderPrefixUnlikeBash() throws Exception {
    yaml.setBefore("foo");
    yaml.setAfter("bar");
    yaml.setPath("folder/**/*.java");
    Transformation transformation = yaml.withOptions(options.build());

    prepareGlobTree();

    transformation.transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "foo")
        .containsFile("folder/file1.txt", "foo")
        // The difference between Java Glob PathMatcher and Bash is that
        // '/**/' is treated as at least one folder.
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/subfolder/file1.java", "bar");
  }

  @Test
  public void testUsesTwoDifferentGroups() throws Exception {
    yaml.setBefore("bef${a}ore${b}");
    yaml.setAfter("af${b}ter${a}");
    yaml.setRegexGroups(ImmutableMap.of(
            "a", "a+",
            "b", "[bB]"));

    writeFile(workdir.resolve("before_and_after"), ""
        + "not a match: beforeB\n"
        + "is a match: befaaaaaoreB # trailing content\n");

    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", ""
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
    yaml.withOptions(options.build());
  }

  @Test
  public void afterUsesUndeclaredGroup() throws ConfigValidationException {
    yaml.setBefore("foo${bar}${iru}");
    yaml.setAfter("foo${bar}");
    yaml.setRegexGroups(ImmutableMap.of("bar", ".*"));

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("used but not defined: [iru]");
    yaml.withOptions(options.build());
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
    yaml.withOptions(options.build());
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
    yaml.withOptions(options.build());
  }

  @Test
  public void categoryComplementDoesNotSpanLine() throws Exception {
    yaml.setBefore("bef${a}ore");
    yaml.setAfter("aft${a}er");
    yaml.setRegexGroups(ImmutableMap.of("a", "[^/]+"));

    writeFile(workdir.resolve("before_and_after"), ""
        + "obviously match: befASDFore/\n"
        + "should not match: bef\n"
        + "ore");

    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", ""
            + "obviously match: aftASDFer/\n"
            + "should not match: bef\n"
            + "ore");
  }

  @Test
  public void multipleMatchesPerLine() throws Exception {
    yaml.setBefore("before");
    yaml.setAfter("after");
    writeFile(workdir.resolve("before_and_after"), "before ... still before");

    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", "after ... still after");
  }

  @Test
  public void showOriginalTemplateInToString() throws ConfigValidationException {
    yaml.setBefore("a${b}c");
    yaml.setAfter("c${b}a");
    yaml.setRegexGroups(ImmutableMap.of("b", ".*"));
    String string = yaml.withOptions(options.build()).toString();
    assertThat(string).contains("before=a${b}c");
    assertThat(string).contains("after=c${b}a");
  }

  @Test
  public void showOriginalGlobInToString() throws ConfigValidationException {
    yaml.setBefore("before");
    yaml.setAfter("after");
    yaml.setPath("foo/**/bar.htm");
    String string = yaml.withOptions(options.build()).toString();
    assertThat(string).contains("include=[foo/**/bar.htm], exclude=[]");
  }

  @Test
  public void showReasonableDefaultGlobInToString() throws ConfigValidationException {
    yaml.setBefore("before");
    yaml.setAfter("after");
    String string = yaml.withOptions(options.build()).toString();
    assertThat(string).contains("include=[**], exclude=[]");
  }

  @Test
  public void showMultilineInToString() throws ConfigValidationException {
    yaml.setBefore("before");
    yaml.setAfter("after");
    yaml.setMultiline(true);

    Replace replace = yaml.withOptions(options.build());
    assertThat(replace.toString())
        .contains("multiline=true");
    assertThat(replace.reverse().toString())
        .contains("multiline=true");

    yaml.setMultiline(false);
    replace = yaml.withOptions(options.build());
    assertThat(replace.toString())
        .contains("multiline=false");
    assertThat(replace.reverse().toString())
        .contains("multiline=false");
  }

  @Test
  public void nopReplaceShouldThrowException() throws Exception {
    yaml.setBefore("this string doesn't appear anywhere in source");
    yaml.setAfter("lulz");
    thrown.expect(VoidTransformationException.class);
    yaml.withOptions(options.build()).transform(workdir, console);
  }

  @Test
  public void noopReplaceAsWarning() throws Exception {
    options.transform.noop_is_warning = true;
    yaml.setBefore("BEFORE this string doesn't appear anywhere in source");
    yaml.setAfter("lulz");
    yaml.withOptions(options.build()).transform(workdir, console);
    console.assertThat()
        .onceInLog(MessageType.WARNING, ".*BEFORE.*lulz.*didn't affect the workdir[.]");
  }

  @Test
  public void useDollarSignInAfter() throws Exception {
    yaml.setBefore("before");
    yaml.setAfter("after$$");
    writeFile(workdir.resolve("before_and_after"), "before ... still before");
    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInAfter() throws Exception {
    yaml.setBefore("before");
    yaml.setAfter("after\\");
    writeFile(workdir.resolve("before_and_after"), "before ... still before");
    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void useEscapedDollarInBeforeAndAfter() throws Exception {
    yaml.setBefore("be$$ore");
    yaml.setAfter("after$$");
    writeFile(workdir.resolve("before_and_after"), "be$ore ... still be$ore");
    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInBeforeAndAfter() throws Exception {
    yaml.setBefore("be\\ore");
    yaml.setAfter("after\\");
    writeFile(workdir.resolve("before_and_after"), "be\\ore ... still be\\ore");
    yaml.withOptions(options.build()).transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void reverse() throws Exception {
    yaml.setBefore("x${foo}y");
    yaml.setAfter("y${foo}x");
    yaml.setRegexGroups(ImmutableMap.of("foo", "[0-9]+"));
    writeFile(workdir.resolve("file"), "!@# y123x ...");
    yaml.withOptions(options.build())
        .reverse()
        .transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file", "!@# x123y ...");
  }

  @Test
  public void multiline() throws Exception {
    yaml.setBefore("foo\nbar");
    yaml.setAfter("bar\nfoo");
    yaml.setMultiline(true);
    writeFile(workdir.resolve("file"), "aaa foo\nbar bbb foo\nbar ccc");
    yaml.withOptions(options.build())
        .transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file", "aaa bar\nfoo bbb bar\nfoo ccc");
  }

  @Test
  public void multilineFieldActivatesRegexMultilineSemantics() throws Exception {
    yaml.setBefore("foo${eol}");
    yaml.setRegexGroups(ImmutableMap.of("eol", "$"));
    yaml.setAfter("bar${eol}");
    yaml.setMultiline(true);
    writeFile(workdir.resolve("file"), ""
        + "a foo\n"
        + "b foo\n"
        + "c foo d\n");
    yaml.withOptions(options.build())
        .transform(workdir, console);

    assertThatPath(workdir)
        .containsFile("file", ""
            + "a bar\n"
            + "b bar\n"
            + "c foo d\n");
  }

  private void prepareGlobTree() throws IOException {
    writeFile(workdir.resolve("file1.txt"), "foo");
    writeFile(workdir.resolve("file1.java"), "foo");
    Files.createDirectories(workdir.resolve("folder/subfolder"));
    writeFile(workdir.resolve("folder/file1.txt"), "foo");
    writeFile(workdir.resolve("folder/file1.java"), "foo");
    writeFile(workdir.resolve("folder/subfolder/file1.java"), "foo");
  }

  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes(StandardCharsets.UTF_8));
  }
}
