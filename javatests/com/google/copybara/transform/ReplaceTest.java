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

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReplaceTest {

  private OptionsBuilder options;
  private FileSystem fs;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  private TransformationStatus transform(Transformation transformation) throws Exception {
    return transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void invalidRegex() throws ValidationException {
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
  public void missingReplacement() throws ValidationException {
    skylark.evalFails(
        "core.replace(\n" + "  before = 'asdf',\n" + ")",
        "missing 1 required positional argument: after");
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
  public void testAppendFile() throws Exception {
    Transformation transformation =
        eval("core.transform([\n"
            + "    core.replace(\n"
            + "       before = '${end}',\n"
            + "       after  = 'some append',\n"
            + "       multiline = True,\n"
            + "       regex_groups = { 'end' : r'\\z'},\n"
            + "    )\n"
            + "],\n"
            + "reversal = [\n"
            + "    core.replace(\n"
            + "       before = 'some append${end}',\n"
            + "       after = '',\n"
            + "       multiline = True,\n"
            + "       regex_groups = { 'end' : r'\\z'},\n"
            + "    )"
            + "])");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo\nbar\nbaz\n");
    transform(transformation);

    assertThatPath(checkoutDir).containsFile("file1.txt", "foo\nbar\nbaz\nsome append");
    transform(transformation.reverse());
    assertThatPath(checkoutDir).containsFile("file1.txt", "foo\nbar\nbaz\n");
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
  public void testWithGroupsAndIgnores() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = '<foo${middle}bar>',\n"
        + "  after = '<bar${middle}foo>',\n"
        + "  regex_groups = {\n"
        + "       'middle' : '.*',"
        + "  },\n"
        + "  ignore = [\n"
        + "       '^#include.*',\n"
        + "       '.*// IGNORE',\n"
        + "  ],\n"
        + ")");

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1,
        "#include <fooBAZbar>\n" +
        "<fooBAZbar>\n" +
        "fooQUXbar  // IGNORE");
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt",
            "#include <fooBAZbar>\n" +
            "<barBAZfoo>\n" +
            "fooQUXbar  // IGNORE");
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
  public void testWithRepeatedGroups() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo/${a}/${a}',\n"
        + "  after = '${a}',\n"
        + "  regex_groups = {\n"
        + "       'a' : '[a-z]+',\n"
        + "  },\n"
        + "  repeated_groups = True,\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), ""
        + "foo/bar/bar\n"
        + "foo/bar/baz\n"
        + "foo/baz/baz\n");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", ""
            + "bar\n"
            + "foo/bar/baz\n"
            + "baz\n");
  }

  @Test
  public void testNoBacktracking() throws Exception {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'foo/${a}${a}',\n"
        + "  after = '${a}',\n"
        + "  regex_groups = {\n"
        + "       'a' : '[a-z]+',\n"
        + "  },\n"
        + "  repeated_groups = True,\n"
        + ")");

    writeFile(checkoutDir.resolve("before_and_after"), "foo/barbar\n");
    // Because we don't use backtracking this repeated group is expected to fail.
    TransformationStatus status = transform(transformation);
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void beforeUsesUndeclaredGroup() throws ValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo${bar}${baz}',\n"
            + "  after = 'foo${baz}',\n"
            + "  regex_groups = {\n"
            + "       'baz' : '.*',\n"
            + "  },\n"
            + ")",
        "used but not defined: bar");
  }

  @Test
  public void afterUsesUndeclaredGroup() throws ValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo${bar}${iru}',\n"
            + "  after = 'foo${bar}',\n"
            + "  regex_groups = {\n"
            + "       'bar' : '.*',\n"
            + "  },\n"
            + ")",
        "used but not defined: iru");
  }

  @Test
  public void beforeDoesNotUseADeclaredGroup() throws ValidationException {
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
    skylark.evalFails("core.reverse([" + transform + "])",
        "The transformation is not automatically reversible. Add an explicit reversal field with "
            + "core.transform");
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
  public void showOriginalTemplateInToString() throws ValidationException {
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
  public void showOriginalGlobInToString() throws ValidationException {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + "  paths = glob(['foo/**/bar.htm'])"
        + ")");

    String string = transformation.toString();
    assertThat(string).contains("glob(include = [\"foo/**/bar.htm\"])");
  }

  @Test
  public void showReasonableDefaultGlobInToString() throws ValidationException {
    Replace transformation = eval("core.replace(\n"
        + "  before = 'before',\n"
        + "  after = 'after',\n"
        + ")");

    String string = transformation.toString();
    assertThat(string).contains("glob(include = [\"**\"])");
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
    TransformationStatus status = transform(replace);
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void replaceErrorEscapesNewLine() throws Exception {
    Replace replace = eval("core.replace(\n"
        + "  before = \"hello\\n\\r\\tbye!\",\n"
        + "  after = 'lulz',\n"
        + ")");
    TransformationStatus status = transform(replace);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("hello\\n\\r\\tbye!");
  }

  @Test
  public void noopReplaceAsWarning() throws Exception {
    writeFile(checkoutDir.resolve("foo"), "");
    TransformationStatus status = transform(eval("core.replace(\n"
        + "  before = \"BEFORE this string doesn't appear anywhere in source\",\n"
        + "  after = 'lulz',\n"
        + ")"));
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(".*BEFORE.*lulz.*was a no-op because it didn't change any of the matching files");

    status = transform(eval("core.replace(\n"
        + "  before = \"BEFORE this string doesn't appear anywhere in source\",\n"
        + "  after = 'lulz',\n"
        + "  paths = glob(['bad_path/**'])\n"
        + ")"));
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(".*BEFORE.*lulz.*was a no-op because it didn't match any file");
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

  @Test
  public void firstOnlyLineByLine() throws Exception {
    Replace replace = eval(""
        + "core.replace("
        + "  before = 'foo',"
        + "  after = 'bar',"
        + "  first_only = True,"
        + ")");

    writeFile(checkoutDir.resolve("file"), ""
        + "foo x y foo\n"
        + "foo\n");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", ""
            + "bar x y foo\n"
            + "bar\n");
  }

  @Test
  public void repeatGroupInBeforeTemplate() throws Exception {
    skylark.evalFails(""
        + "core.replace("
        + "  before = '${a}x${b}${a}',"
        + "  after = '${a}y${b}',"
        + "  regex_groups = {"
        + "       'a' : '[0-9]',"
        + "       'b' : '[LMNOP]',"
        + "  },\n"
        + ")",
        "Regex group is used in template multiple times");
  }

  @Test
  public void nestedGroups() throws Exception {
    Replace replace = skylark.eval("r", "r = "
        + "core.replace("
        + "  before = 'a${x}b${y}',"
        + "  after = '${x}${y}',"
        + "  regex_groups = {'x': 'f(oo)+(d)?', 'y': 'y+'},"
        + ")");
    writeFile(checkoutDir.resolve("file"), ""
        + "afoooodbyyy # matches\n"
        + "afooodbyyy # no match (odd number of o)\n");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", ""
            + "foooodyyy # matches\n"
            + "afooodbyyy # no match (odd number of o)\n");
  }

  @Test
  public void firstOnlyMultiline() throws Exception {
    Replace replace = eval(""
        + "core.replace("
        + "  before = 'foo',"
        + "  after = 'bar',"
        + "  first_only = True,"
        + "  multiline = True,"
        + ")");

    writeFile(checkoutDir.resolve("file"), ""
        + "foo x y foo\n"
        + "foo\n");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", ""
            + "bar x y foo\n"
            + "foo\n");
  }

  @Test
  public void emptyInterpolatedName() throws ValidationException {
    skylark.evalFails(""
        + "core.replace("
        + "  before = 'foo${}bar',"
        + "  after = 'ok',"
        + ")",
        "Expect non-empty interpolated value name");
  }

  @Test
  public void unterminatedInterpolation() throws ValidationException {
    skylark.evalFails(""
        + "core.replace("
        + "  before = 'foo${bar',"
        + "  after = 'ok',"
        + ")",
        "Unterminated '[$][{]'");
  }

  @Test
  public void badCharacterFollowingDollar() throws ValidationException {
    skylark.evalFails(""
        + "core.replace("
        + "  before = 'foo$bar',"
        + "  after = 'ok',"
        + ")",
        "Expect [$] or [{] after every [$]");
  }

  @Test
  public void noCharacterFollowingDollar() throws ValidationException {
    skylark.evalFails("core.replace(\n"
            + "  before = 'foo$',\n"
            + "  after = 'ok',\n"
            + ")",
        "Expect [$] or [{] after every [$]");
  }

  @Test
  public void multilineScrub() throws Exception {
    // The regex here has unnecessary constructs but it is based on a real-world use case.
    Replace replace = eval(""
        + "core.replace("
        + "  before = '${x}',"
        + "  after = '',"
        + "  multiline = True,"
        + "  regex_groups = {'x': '(?m)^.*BEGIN SCRUB[\\\\w\\\\W]*?END SCRUB.*$\\n'},"
        + ")");

    writeFile(checkoutDir.resolve("file"), ""
        + "foo x y foo\n"
        + "BEGIN SCRUB\n"
        + "foo super secret++++\n"
        + "asldkfjlskdfj\n"
        + "END SCRUB\n"
        + "foo\n");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", ""
            + "foo x y foo\n"
            + "foo\n");
  }

  @Test
  public void doNotProcessSymlinks() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    Replace replace = eval(""
        + "core.replace("
        + "  before = 'a',"
        + "  after = 'b',"
        + "  paths = glob(['*'], exclude = ['i-exist']),"
        + ")");
    // Invalid symlinks should not cause an exception.
    Files.createSymbolicLink(checkoutDir.resolve("invalid_symlink"), fs.getPath("i-dont-exist"));

    // Valid symlinks, even to something that contains matching, text should be skipped.
    Files.createSymbolicLink(checkoutDir.resolve("valid_symlink"), fs.getPath("i-exist"));
    writeFile(checkoutDir.resolve("i-exist"), "abc");

    transform(replace);
    assertThat(Files.isSymbolicLink(checkoutDir.resolve("valid_symlink")))
        .isTrue();
    assertThatPath(checkoutDir)
        .containsFile("i-exist", "abc");
  }

  private <T extends Transformation> T eval(String replace) throws ValidationException {
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
