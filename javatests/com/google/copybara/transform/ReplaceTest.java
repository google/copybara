// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.Core;
import com.google.copybara.TransformWork;
import com.google.copybara.ValidationException;
import com.google.copybara.VoidOperationException;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
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
public final class ReplaceTest {

  private OptionsBuilder options;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options, Core.class);
  }

  private void transform(Replace replace) throws IOException, ValidationException {
    replace.transform(new TransformWork(checkoutDir, "testmsg"), console);
  }

  @Test
  public void invalidRegex() throws ConfigValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = '${foo}',\n"
            + "  after = '${foo}bar',\n"
            + "  regex_groups = {\n"
            + "       'foo' : '(unfinished group',\n"
            + "  },\n"
            + ")",
        "'regex_groups' includes invalid regex for key foo");
  }

  @Test
  public void missingReplacement() throws ConfigValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'asdf',\n"
            + ")",
        "missing mandatory positional argument 'after'");
  }

  @Test
  public void testSimpleReplaceWithoutGroups() throws Exception {
    Replace transformation =
        eval("core.replace(\n"
            + "  before = 'foo',\n"
            + "  after  = 'bar',\n"
            + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo");
    Path file2 = checkoutDir.resolve("file2.txt");
    writeFile(file2, "foo\nbaz\nfoo");
    Path file3 = checkoutDir.resolve("file3.txt");
    writeFile(file3, "bazbazbaz");
    BasicFileAttributes before = Files.readAttributes(file3, BasicFileAttributes.class);
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "bar")
        .containsFile("file2.txt", "bar\nbaz\nbar")
        .containsFile("file3.txt", "bazbazbaz");

    BasicFileAttributes after = Files.readAttributes(file3, BasicFileAttributes.class);

    // No file modification is done if the match is not found
    assertThat(before.lastModifiedTime()).isEqualTo(after.lastModifiedTime());
  }

  @Test
  public void testWithGroups() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo${middle}bar',\n"
        + "  after = 'bar${middle}foo',\n"
        + "  regex_groups = {\n"
        + "       'middle' : '.*',"
        + "  },\n"
        + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "fooBAZbar");
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "barBAZfoo");
  }

  @Test
  public void testWithGlob() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo',\n"
        + "  after = 'bar',\n"
        + "  paths = glob(['**.java']),\n"
        + ")");

    prepareGlobTree();

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "bar")
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/file1.java", "bar")
        .containsFile("folder/subfolder/file1.java", "bar");
  }

  @Test
  public void testWithGlobFolderPrefix() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo',\n"
        + "  after = 'bar',\n"
        + "  paths = glob(['folder/**.java']),\n"
        + ")");

    prepareGlobTree();

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "foo")
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/file1.java", "bar")
        .containsFile("folder/subfolder/file1.java", "bar");
  }


  @Test
  public void testWithGlobFolderPrefixUnlikeBash() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo',\n"
        + "  after = 'bar',\n"
        + "  paths = glob(['folder/**/*.java']),\n"
        + ")");

    prepareGlobTree();

    transform(transformation);

    assertThatPath(checkoutDir)
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
    Replace transformation = eval("core.replace(\n"
        + "  before = 'bef${a}ore${b}',\n"
        + "  after = 'af${b}ter${a}',\n"
        + "  regex_groups = {\n"
        + "       'a' : 'a+',\n"
        + "       'b' : '[bB]',\n"
        + "  },\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), ""
        + "not a match: beforeB\n"
        + "is a match: befaaaaaoreB # trailing content\n");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", ""
                + "not a match: beforeB\n"
                + "is a match: afBteraaaaa # trailing content\n");
  }

  @Test
  public void beforeUsesUndeclaredGroup() throws ConfigValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo${bar}${baz}',\n"
            + "  after = 'foo${baz}',\n"
            + "  regex_groups = {\n"
            + "       'baz' : '.*',\n"
            + "  },\n"
            + ")",
        "used but not defined: \\[bar\\]");
  }

  @Test
  public void afterUsesUndeclaredGroup() throws ConfigValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo${bar}${iru}',\n"
            + "  after = 'foo${bar}',\n"
            + "  regex_groups = {\n"
            + "       'bar' : '.*',\n"
            + "  },\n"
            + ")",
        "used but not defined: \\[iru\\]");
  }

  @Test
  public void beforeDoesNotUseADeclaredGroup() throws ConfigValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo${baz}',\n"
            + "  after = 'foo${baz}${bar}',\n"
            + "  regex_groups = {\n"
            + "       'baz' : '.*',\n"
            + "       'bar' : '[a-z]+',\n"
            + "  },\n"
            + ")",
        "defined but not used: \\[bar\\]");
  }

  @Test
  public void afterDoesNotUseADeclaredGroup() throws Exception {

    String transform = ""
        + "core.replace(\n"
        + "  before = 'foo${baz}${bar}',\n"
        + "  after = 'foo${baz}',\n"
        + "  regex_groups = {\n"
        + "       'baz' : '[0-9]+',\n"
        + "       'bar' : '[a-z]+',\n"
        + "  },\n"
        + ")";

    // Not using all the groups in after is OK if we don't reverse the replace in the config
    Replace replace = skylark.eval("r", "r = " + transform);
    Files.write(checkoutDir.resolve("foo"), "foo123abc".getBytes(UTF_8));
    transform(replace);
    FileSubjects.assertThatPath(checkoutDir)
        .containsFile("foo","foo123");

    // But it fails if we ask for the reverse
    skylark.evalFails("core.reverse([" + transform + "])", "defined but not used: \\[bar\\]");
  }

  @Test
  public void categoryComplementDoesNotSpanLine() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'bef${a}ore',\n"
        + "  after = 'aft${a}er',\n"
        + "  regex_groups = {\n"
        + "       'a' : '[^/]+',\n"
        + "  },\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), ""
        + "obviously match: befASDFore/\n"
        + "should not match: bef\n"
        + "ore");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", ""
            + "obviously match: aftASDFer/\n"
            + "should not match: bef\n"
            + "ore");
  }

  @Test
  public void multipleMatchesPerLine() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after ... still after");
  }

  @Test
  public void showOriginalTemplateInToString() throws ConfigValidationException {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'a${b}c',\n"
        + "  after = 'c${b}a',\n"
        + "  regex_groups = {\n"
        + "       'b' : '.*',\n"
        + "  },\n"
        + ")");

    String string = transformation.toString();
    assertThat(string).contains("before=a${b}c");
    assertThat(string).contains("after=c${b}a");
  }

  @Test
  public void showOriginalGlobInToString() throws ConfigValidationException {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + "  paths = glob(['foo/**/bar.htm'])"
        + ")");

    String string = transformation.toString();
    assertThat(string).contains("include=[foo/**/bar.htm], exclude=[]");
  }

  @Test
  public void showReasonableDefaultGlobInToString() throws ConfigValidationException {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + ")");

    String string = transformation.toString();
    assertThat(string).contains("include=[**], exclude=[]");
  }

  @Test
  public void showMultilineInToString() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + "  multiline = True,\n"
        + ")");

    assertThat(replace.toString())
        .contains("multiline=true");
    assertThat(replace.reverse().toString())
        .contains("multiline=true");

    replace = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + "  multiline = False,\n"
        + ")");

    assertThat(replace.toString())
        .contains("multiline=false");
    assertThat(replace.reverse().toString())
        .contains("multiline=false");
  }

  @Test
  public void nopReplaceShouldThrowException() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = \"this string doesn't appear anywhere in source\",\n"
        + "  after = 'lulz',\n"
        + ")");
    thrown.expect(VoidOperationException.class);
    transform(replace);
  }

  @Test
  public void noopReplaceAsWarning() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    Replace replace = eval("core.replace(\n"
        + "  before = \"BEFORE this string doesn't appear anywhere in source\",\n"
        + "  after = 'lulz',\n"
        + ")");

    transform(replace);
    console.assertThat()
        .onceInLog(MessageType.WARNING, ".*BEFORE.*lulz.*didn't affect the workdir[.]");
  }

  @Test
  public void useDollarSignInAfter() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after$$',\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInAfter() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after\\\\',\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");
    transform(replace);
    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void useEscapedDollarInBeforeAndAfter() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'be$$fore',\n"
        + "  after = 'after$$',\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "be$fore ... still be$fore");
    transform(replace);
    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInBeforeAndAfter() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'be\\\\fore',\n"
        + "  after = 'after\\\\',\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "be\\fore ... still be\\fore");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void reverse() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'x${foo}y',\n"
        + "  after = 'y${foo}x',\n"
        + "  regex_groups = {\n"
        + "       'foo' : '[0-9]+',\n"
        + "  },\n"
        + ")");
    writeFile(checkoutDir.resolve("file"), "!@# y123x ...");
    transform(replace.reverse());

    assertThatPath(checkoutDir)
        .containsFile("file", "!@# x123y ...");
  }

  @Test
  public void multiline() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'foo\\nbar',\n"
        + "  after = 'bar\\nfoo',\n"
        + "  multiline = True,\n"
        + ")");

    writeFile(checkoutDir.resolve("file"), "aaa foo\nbar bbb foo\nbar ccc");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", "aaa bar\nfoo bbb bar\nfoo ccc");
  }

  @Test
  public void multilinePythonLike() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = \"\"\"foo\n"
        + "bar\"\"\",\n"
        + "  after = \"\"\"bar\n"
        + "foo\"\"\",\n"
        + "  multiline = True,\n"
        + ")");

    writeFile(checkoutDir.resolve("file"), "aaa foo\nbar bbb foo\nbar ccc");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", "aaa bar\nfoo bbb bar\nfoo ccc");
  }

  @Test
  public void multilineFieldActivatesRegexMultilineSemantics() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = 'foo${eol}',\n"
        + "  after = 'bar${eol}',\n"
        + "  regex_groups = {\n"
        + "       'eol' : '$',\n"
        + "  },\n"
        + "  multiline = True,\n"
        + ")");

    writeFile(checkoutDir.resolve("file"), ""
        + "a foo\n"
        + "b foo\n"
        + "c foo d\n");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", ""
            + "a bar\n"
            + "b bar\n"
            + "c foo d\n");
  }

  private Replace eval(String replace) throws ConfigValidationException {
    return skylark.eval("r", "r = " + replace);
  }

  private void prepareGlobTree() throws IOException {
    writeFile(checkoutDir.resolve("file1.txt"), "foo");
    writeFile(checkoutDir.resolve("file1.java"), "foo");
    Files.createDirectories(checkoutDir.resolve("folder/subfolder"));
    writeFile(checkoutDir.resolve("folder/file1.txt"), "foo");
    writeFile(checkoutDir.resolve("folder/file1.java"), "foo");
    writeFile(checkoutDir.resolve("folder/subfolder/file1.java"), "foo");
  }

  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes(UTF_8));
  }
}
