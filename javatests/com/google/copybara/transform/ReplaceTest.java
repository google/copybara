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
    skylark.evalFails(
        """
        core.replace(
          before = '${foo}',
          after = '${foo}bar',
          regex_groups = {
               'foo' : '(unfinished group',
          },
        )\
        """,
        "'regex_groups' includes invalid regex for key foo");
  }

  @Test
  public void missingReplacement() throws ValidationException {
    skylark.evalFails(
        """
        core.replace(
          before = 'asdf',
        )\
        """,
        "missing 1 required positional argument: after");
  }

  @Test
  public void testSimpleReplaceWithoutGroups() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo',
              after  = 'bar',
            )\
            """);

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
        eval("""
            core.transform([
                core.replace(
                   before = '${end}',
                   after  = 'some append',
                   multiline = True,
                   regex_groups = { 'end' : r'\\z'},
                )
            ],
            reversal = [
                core.replace(
                   before = 'some append${end}',
                   after = '',
                   multiline = True,
                   regex_groups = { 'end' : r'\\z'},
                )
            ])
            """);

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "foo\nbar\nbaz\n");
    transform(transformation);

    assertThatPath(checkoutDir).containsFile("file1.txt", "foo\nbar\nbaz\nsome append");
    transform(transformation.reverse());
    assertThatPath(checkoutDir).containsFile("file1.txt", "foo\nbar\nbaz\n");
  }

  @Test
  public void testWithGroups() throws Exception {
    Replace transformation = eval("""
        core.replace(
          before = 'foo${middle}bar',
          after = 'bar${middle}foo',
          regex_groups = {
               'middle' : '.*',
          },
        )
        """);

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(file1, "fooBAZbar");
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "barBAZfoo");
  }

  @Test
  public void testWithGroupsAndIgnores() throws Exception {
    Replace transformation = eval("""
        core.replace(
          before = '<foo${middle}bar>',
          after = '<bar${middle}foo>',
          regex_groups = {
               'middle' : '.*',
          },
          ignore = [
               '^#include.*',
               '.*// IGNORE',
          ],
        )
        """);

    Path file1 = checkoutDir.resolve("file1.txt");
    writeFile(
        file1,
        """
        #include <fooBAZbar>
        <fooBAZbar>
        fooQUXbar  // IGNORE\
        """);
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile(
            "file1.txt",
            """
            #include <fooBAZbar>
            <barBAZfoo>
            fooQUXbar  // IGNORE\
            """);
  }


  @Test
  public void testWithGlob() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo',
              after = 'bar',
              paths = glob(['**.java']),
            )\
            """);

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
  public void testWithSequence() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo',
              after = 'bar',
              paths = ['file1.java', 'folder/file1.java'],
            )\
            """);

    prepareGlobTree();

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "foo")
        .containsFile("file1.java", "bar")
        .containsFile("folder/file1.txt", "foo")
        .containsFile("folder/file1.java", "bar");
  }

  @Test
  public void testWithGlobFolderPrefix() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo',
              after = 'bar',
              paths = glob(['folder/**.java']),
            )\
            """);

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
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo',
              after = 'bar',
              paths = glob(['folder/**/*.java']),
            )\
            """);

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
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'bef${a}ore${b}',
              after = 'af${b}ter${a}',
              regex_groups = {
                   'a' : 'a+',
                   'b' : '[bB]',
              },
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), """
        not a match: beforeB
        is a match: befaaaaaoreB # trailing content
        """);

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", """
                not a match: beforeB
                is a match: afBteraaaaa # trailing content
                """);
  }

  @Test
  public void testWithRepeatedGroups() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo/${a}/${a}',
              after = '${a}',
              regex_groups = {
                   'a' : '[a-z]+',
              },
              repeated_groups = True,
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), """
        foo/bar/bar
        foo/bar/baz
        foo/baz/baz
        """);

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", """
            bar
            foo/bar/baz
            baz
            """);
  }

  @Test
  public void testNoBacktracking() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'foo/${a}${a}',
              after = '${a}',
              regex_groups = {
                   'a' : '[a-z]+',
              },
              repeated_groups = True,
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "foo/barbar\n");
    // Because we don't use backtracking this repeated group is expected to fail.
    TransformationStatus status = transform(transformation);
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void beforeUsesUndeclaredGroup() throws ValidationException {
    skylark.evalFails(
        """
        core.replace(
          before = 'foo${bar}${baz}',
          after = 'foo${baz}',
          regex_groups = {
               'baz' : '.*',
          },
        )\
        """,
        "used but not defined: bar");
  }

  @Test
  public void afterUsesUndeclaredGroup() throws ValidationException {
    skylark.evalFails(
        """
        core.replace(
          before = 'foo${bar}${iru}',
          after = 'foo${bar}',
          regex_groups = {
               'bar' : '.*',
          },
        )\
        """,
        "used but not defined: iru");
  }

  @Test
  public void beforeDoesNotUseADeclaredGroup() throws ValidationException {
    skylark.evalFails(
        """
        core.replace(
          before = 'foo${baz}',
          after = 'foo${baz}${bar}',
          regex_groups = {
               'baz' : '.*',
               'bar' : '[a-z]+',
          },
        )\
        """,
        "defined but not used: \\[bar\\]");
  }

  @Test
  public void afterDoesNotUseADeclaredGroup() throws Exception {

    String transform = """
        core.replace(
          before = 'foo${baz}${bar}',
          after = 'foo${baz}',
          regex_groups = {
               'baz' : '[0-9]+',
               'bar' : '[a-z]+',
          },
        )""";

    // Not using all the groups in after is OK if we don't reverse the replace in the config
    Replace replace = skylark.eval("r", "r = " + transform);
    Files.write(checkoutDir.resolve("foo"), "foo123abc".getBytes(UTF_8));
    transform(replace);
    FileSubjects.assertThatPath(checkoutDir)
        .containsFile("foo","foo123");

    // But it fails if we ask for the reverse
    skylark.evalFails("core.reverse([" + transform + "])",
        """
        The transformation is not automatically reversible. Add an explicit reversal field with core.transform\
        """);
  }

  @Test
  public void categoryComplementDoesNotSpanLine() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'bef${a}ore',
              after = 'aft${a}er',
              regex_groups = {
                   'a' : '[^/]+',
              },
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), """
        obviously match: befASDFore/
        should not match: bef
        ore""");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", """
            obviously match: aftASDFer/
            should not match: bef
            ore""");
  }

  @Test
  public void multipleMatchesPerLine() throws Exception {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after',
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");

    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after ... still after");
  }

  @Test
  public void showOriginalTemplateInToString() throws ValidationException {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'a${b}c',
              after = 'c${b}a',
              regex_groups = {
                   'b' : '.*',
              },
            )\
            """);

    String string = transformation.toString();
    assertThat(string).contains("before=a${b}c");
    assertThat(string).contains("after=c${b}a");
  }

  @Test
  public void showOriginalGlobInToString() throws ValidationException {
    Replace transformation = eval("""
        core.replace(
          before = 'before',
          after = 'after',
          paths = glob(['foo/**/bar.htm'])
        )""");

    String string = transformation.toString();
    assertThat(string).contains("glob(include = [\"foo/**/bar.htm\"])");
  }

  @Test
  public void showReasonableDefaultGlobInToString() throws ValidationException {
    Replace transformation =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after',
            )\
            """);

    String string = transformation.toString();
    assertThat(string).contains("glob(include = [\"**\"])");
  }

  @Test
  public void showMultilineInToString() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after',
              multiline = True,
            )\
            """);

    assertThat(replace.toString())
        .contains("multiline=true");
    assertThat(replace.reverse().toString())
        .contains("multiline=true");

    replace =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after',
              multiline = False,
            )\
            """);

    assertThat(replace.toString())
        .contains("multiline=false");
    assertThat(replace.reverse().toString())
        .contains("multiline=false");
  }

  @Test
  public void nopReplaceShouldThrowException() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = "this string doesn't appear anywhere in source",
              after = 'lulz',
            )\
            """);
    TransformationStatus status = transform(replace);
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void replaceErrorEscapesNewLine() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = "hello\\n\\r\\tbye!",
              after = 'lulz',
            )\
            """);
    TransformationStatus status = transform(replace);
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage()).contains("hello\\n\\r\\tbye!");
  }

  @Test
  public void noopReplaceAsWarning() throws Exception {
    writeFile(checkoutDir.resolve("foo"), "");
    TransformationStatus status =
        transform(
            eval(
                """
                core.replace(
                  before = "BEFORE this string doesn't appear anywhere in source",
                  after = 'lulz',
                )\
                """));
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(".*BEFORE.*lulz.*was a no-op because it didn't change any of the matching files");

    status =
        transform(
            eval(
                """
                core.replace(
                  before = "BEFORE this string doesn't appear anywhere in source",
                  after = 'lulz',
                  paths = glob(['bad_path/**'])
                )\
                """));
    assertThat(status.isNoop()).isTrue();
    assertThat(status.getMessage())
        .matches(".*BEFORE.*lulz.*was a no-op because it didn't match any file");
  }

  @Test
  public void useDollarSignInAfter() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after$$',
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInAfter() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'before',
              after = 'after\\\\',
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "before ... still before");
    transform(replace);
    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void useEscapedDollarInBeforeAndAfter() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'be$$fore',
              after = 'after$$',
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "be$fore ... still be$fore");
    transform(replace);
    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after$ ... still after$");
  }

  @Test
  public void useBackslashInBeforeAndAfter() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'be\\\\fore',
              after = 'after\\\\',
            )\
            """);

    writeFile(checkoutDir.resolve("before_and_after"), "be\\fore ... still be\\fore");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("before_and_after", "after\\ ... still after\\");
  }

  @Test
  public void reverse() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'x${foo}y',
              after = 'y${foo}x',
              regex_groups = {
                   'foo' : '[0-9]+',
              },
            )\
            """);
    writeFile(checkoutDir.resolve("file"), "!@# y123x ...");
    transform(replace.reverse());

    assertThatPath(checkoutDir)
        .containsFile("file", "!@# x123y ...");
  }

  @Test
  public void multiline() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'foo\\nbar',
              after = 'bar\\nfoo',
              multiline = True,
            )\
            """);

    writeFile(checkoutDir.resolve("file"), "aaa foo\nbar bbb foo\nbar ccc");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", "aaa bar\nfoo bbb bar\nfoo ccc");
  }

  @Test
  public void multilinePythonLike() throws Exception {
    Replace replace = eval("""
        core.replace(
          before = \"\"\"foo
        bar\"\"\",
          after = \"\"\"bar
        foo\"\"\",
          multiline = True,
        )
        """);

    writeFile(checkoutDir.resolve("file"), "aaa foo\nbar bbb foo\nbar ccc");
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", "aaa bar\nfoo bbb bar\nfoo ccc");
  }

  @Test
  public void multilineFieldActivatesRegexMultilineSemantics() throws Exception {
    Replace replace =
        eval(
            """
            core.replace(
              before = 'foo${eol}',
              after = 'bar${eol}',
              regex_groups = {
                   'eol' : '$',
              },
              multiline = True,
            )\
            """);

    writeFile(checkoutDir.resolve("file"), """
        a foo
        b foo
        c foo d
        """);
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", """
            a bar
            b bar
            c foo d
            """);
  }

  @Test
  public void firstOnlyLineByLine() throws Exception {
    Replace replace = eval("""
        core.replace(
          before = 'foo',
          after = 'bar',
          first_only = True,
        )
        """);

    writeFile(checkoutDir.resolve("file"), """
        foo x y foo
        foo
        """);
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", """
            bar x y foo
            bar
            """);
  }

  @Test
  public void repeatGroupInBeforeTemplate() throws Exception {
    skylark.evalFails("""
        core.replace(
          before = '${a}x${b}${a}',
          after = '${a}y${b}',
          regex_groups = {
               'a' : '[0-9]',
               'b' : '[LMNOP]',
          },
        )""",
        "Regex group is used in template multiple times");
  }

  @Test
  public void nestedGroups() throws Exception {
    Replace replace = skylark.eval("r", """
        r = core.replace(
          before = 'a${x}b${y}',
          after = '${x}${y}',
          regex_groups = {'x': 'f(oo)+(d)?', 'y': 'y+'},
        )
        """);
    writeFile(checkoutDir.resolve("file"), """
        afoooodbyyy # matches
        afooodbyyy # no match (odd number of o)
        """);
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", """
            foooodyyy # matches
            afooodbyyy # no match (odd number of o)
            """);
  }

  @Test
  public void firstOnlyMultiline() throws Exception {
    Replace replace = eval("""
        core.replace(
          before = 'foo',
          after = 'bar',
          first_only = True,
          multiline = True,
        )
        """);

    writeFile(checkoutDir.resolve("file"), """
        foo x y foo
        foo
        """);
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", """
            bar x y foo
            foo
            """);
  }

  @Test
  public void emptyInterpolatedName() throws ValidationException {
    skylark.evalFails("""
        core.replace(
          before = 'foo${}bar',
          after = 'ok',
        )""",
        "Expect non-empty interpolated value name");
  }

  @Test
  public void unterminatedInterpolation() throws ValidationException {
    skylark.evalFails("""
        core.replace(
          before = 'foo${bar',
          after = 'ok',
        )""",
        "Unterminated '[$][{]'");
  }

  @Test
  public void badCharacterFollowingDollar() throws ValidationException {
    skylark.evalFails("""
        core.replace(
          before = 'foo$bar',
          after = 'ok',
        )""",
        "Expect [$] or [{] after every [$]");
  }

  @Test
  public void noCharacterFollowingDollar() throws ValidationException {
    skylark.evalFails(
        """
        core.replace(
          before = 'foo$',
          after = 'ok',
        )\
        """,
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

    writeFile(checkoutDir.resolve("file"), """
        foo x y foo
        BEGIN SCRUB
        foo super secret++++
        asldkfjlskdfj
        END SCRUB
        foo
        """);
    transform(replace);

    assertThatPath(checkoutDir)
        .containsFile("file", """
            foo x y foo
            foo
            """);
  }

  @Test
  public void doNotProcessSymlinks() throws Exception {
    options.workflowOptions.ignoreNoop = true;
    Replace replace = eval("""
        core.replace(
          before = 'a',
          after = 'b',
          paths = glob(['*'], exclude = ['i-exist']),
        )""");
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
