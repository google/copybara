/*
 * Copyright (C) 2019 Google LLC
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

import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.CoreModule;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FilterReplaceTest {


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

  private void transform(Transformation transformation) throws Exception {
    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void testSimple() throws Exception {
    String original = """
        afoo
        aaaaaa
        bbbbbb
        abaz
        """;
    write("file1.txt", original);
    write("file2.txt", "other\n");

    Transformation transformation = eval(CoreModule.SIMPLE_FILTER_REPLACE_EXAMPLE);
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", """
            abar
            aaaaaa
            bbbbbb
            abam
            """)
        .containsFile("file2.txt", "other\n")
        .containsNoMoreFiles();

    transform(transformation.reverse());

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", original)
        .containsFile("file2.txt", "other\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testWithVariablesInContent() throws Exception {
    String original = """
        afoo
        a${foo}\\${bar}
        $
        """;
    write("file1.txt", original);

    Transformation transformation = eval("""
        core.filter_replace(
            regex = 'a.*',
            mapping = {
                'afoo': 'abar',
            }
        )
        """);
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", """
            abar
            a${foo}\\${bar}
            $
            """)
        .containsNoMoreFiles();
  }

  @Test
  public void testPath() throws Exception {

    write("file1.txt", "foo\n");
    write("file2.txt", "foo\n");

    Transformation transformation = filterReplace(""
        + "regex = '.*',"
        + "mapping = {'foo': 'bar'},"
        + "paths = glob(['file1.txt']),"
    );
    transform(transformation);

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "bar\n")
        .containsFile("file2.txt", "foo\n")
        .containsNoMoreFiles();

    transform(transformation.reverse());

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "foo\n")
        .containsFile("file2.txt", "foo\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testGroup() throws Exception {

    write("file1.txt", """
        #import <foo>
        #import <aaaa>
        #import <baz>
        """);
    write("file2.txt", "#import<other>\n");

    transform(filterReplace(""
        + "regex = '#import <(.*)>',"
        + "mapping = {"
        + "    'foo': 'bar',\n"
        + "    'baz': 'bam'\n"
        + "},"
        + "group = 1,"
    ));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", """
            #import <bar>
            #import <aaaa>
            #import <bam>
            """)
        .containsFile("file2.txt", "#import<other>\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testGroupOptional() throws Exception {

    write("file1.txt", """
        #import <foo>
        #import <aaaa>copybara
        """);
    write("file2.txt", "#import<other>\n");

    transform(filterReplace(""
        + "regex = '#import <(.*)>(copybara)?',"
        + "mapping = {"
        + "    'copybara': 'baracopy',\n"
        + "},"
        + "group = 2,"
    ));
    assertThatPath(checkoutDir)
        .containsFile("file1.txt", """
            #import <foo>
            #import <aaaa>baracopy
            """)
        .containsFile("file2.txt", "#import<other>\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testCustomReverse() throws Exception {
    String original = """
        #import <foo1>
        #import <foo2>
        #import <bar1>
        """;
    write("file.txt", original);

    Transformation t = filterReplace(""
        + "regex = '#import <fo.*>',"
        + "reverse = '#import <.*> // by Copybara',"
        + "mapping = {"
        + "    '#import <foo1>': '#import <bar1> // by Copybara',\n"
        + "    '#import <foo2>': '#import <bar2> // by Copybara',\n"
        + "},"
    );
    transform(t);

    assertThatPath(checkoutDir)
        .containsFile("file.txt","""
            #import <bar1> // by Copybara
            #import <bar2> // by Copybara
            #import <bar1>
            """)
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFile("file.txt",original)
        .containsNoMoreFiles();
  }

  @Test
  public void testCoreReplace() throws Exception {

    String original = """
        import foo.bar.last.Class;
        import other.bar.last.Class;
        import bar.foo.last.Class;
        """;
    write("file.txt", original);

    Transformation t = filterReplace(""
        + "regex = '(^|\\n)import (.*);\\n',"
        + "group = 2,"
        + "mapping = core.replace_mapper(["
        + "  core.replace("
        + "      before = '${start}foo${pkg}.${last_pkg}.${n}',"
        + "      after = '${start}prefix.foo${pkg}.${last_pkg}.${last_pkg}.${n}',"
        + "      regex_groups = {'start': '^', 'pkg' : '.*', 'last_pkg' : '.*','n' : '[A-Z].*', },"
        + "      repeated_groups = True,"
        + "  ),"
        + "  core.replace("
        + "      before = '${start}bar${pkg}.${last_pkg}.${n}',"
        + "      after = '${start}prefix.bar${pkg}.${last_pkg}.${last_pkg}.${n}',"
        + "      regex_groups = {'start': '^', 'pkg' : '.*', 'last_pkg' : '.*','n' : '[A-Z].*', },"
        + "      repeated_groups = True,"
        + "  ),"
        + "]),"
    );
    transform(t);

    assertThatPath(checkoutDir)
        .containsFile("file.txt", """
            import prefix.foo.bar.last.last.Class;
            import other.bar.last.Class;
            import prefix.bar.foo.last.last.Class;
            """)
        .containsNoMoreFiles();

    transform(t.reverse());
    assertThatPath(checkoutDir)
        .containsFile("file.txt", original)
        .containsNoMoreFiles();

  }

  @Test
  public void testNestedFilterReplace() throws Exception {

    String original = """
        before
        // BEGIN SCRUBBER
        // some comment
        // some other comment
        //   with indentation
        // END SCRUBBER
        // other comment
        some code
        // BEGIN SCRUBBER
        // some comment
        // some other comment
        //   with indentation
        // END SCRUBBER
        after
        """;
    write("file.txt", original);

    Transformation t = filterReplace(""
        + "regex = '// BEGIN SCRUBBER\\n((\\n|.)*?\\n)// END SCRUBBER\\n',\n"
        + "mapping = core.filter_replace(\n"
        + "   regex = '// .*\\n',\n"
        + "   mapping = core.replace_mapper([\n"
        + "     core.replace(before = '// BEGIN SCRUBBER\\n', after = '', multiline = True),\n"
        + "     core.replace(before = '// END SCRUBBER\\n', after = '', multiline = True),\n"
        + "     core.replace(\n"
        + "       before = '// ${content}\\n',\n"
        + "       after = '${content}\\n',\n"
        + "       regex_groups = {'content' : '.*'},\n"
        + "       multiline = True,"
        + "      )\n"
        + "   ]),\n"
        + ")"
    );
    transform(t);

    assertThatPath(checkoutDir)
        .containsFile("file.txt", """
            before
            some comment
            some other comment
              with indentation
            // other comment
            some code
            some comment
            some other comment
              with indentation
            after
            """)
        .containsNoMoreFiles();
  }

  /**
   * Equivalent to core.todo_replace
   */
  @Test
  public void testCoreReplaceAll() throws Exception {
    write("file.txt", """
        Some text // TODO(foo, bar, baz)
        more text
        """);

    Transformation t = eval(CoreModule.TODO_FILTER_REPLACE_EXAMPLE);

    transform(t);

    assertThatPath(checkoutDir)
        .containsFile(
            "file.txt",
            """
            Some text // TODO(fooz, bar, bazz)
            more text
            """)
        .containsNoMoreFiles();

    transform(t.reverse());

    assertThatPath(checkoutDir)
        .containsFile(
            "file.txt",
            """
            Some text // TODO(foo, bar, baz)
            more text
            """)
        .containsNoMoreFiles();
  }

  private void write(String file, String foo) throws IOException {
    Path f = checkoutDir.resolve(file);
    Files.createDirectories(f.getParent());
    Files.write(f, foo.getBytes(UTF_8));
  }

  private <T extends Transformation> T filterReplace(String content) throws ValidationException {
    return eval("core.filter_replace(" + content + ")");
  }

  private <T extends Transformation> T eval(String s)
      throws ValidationException {
    return skylark.eval("r",
        "r = " + s);
  }

}
