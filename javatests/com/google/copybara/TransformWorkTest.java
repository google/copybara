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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Change;
import com.google.copybara.revision.Changes;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransformWorkTest {

  private static final Author ORIGINAL_AUTHOR = new Author("Foo Bar", "foo@bar.com");

  private SkylarkTestExecutor skylark;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private Path workdir;
  private TestingConsole console;

  @Before
  public void setup() throws IOException {
    origin = new DummyOrigin().setAuthor(ORIGINAL_AUTHOR);
    destination = new RecordsProcessCallDestination();
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setForce(true); // We don't care about force for this test
    skylark = new SkylarkTestExecutor(options);
    workdir = Files.createTempDirectory("workdir");
  }

  @Test
  public void testAddLabel() throws Exception {
    checkAddLabel(
        "foo",
        """
        foo

        TEST=VALUE
        """);
  }

  @Test
  public void testAddLabelToGroup() throws Exception {
    checkAddLabel(
        """
        foo

        A=B

        """,
        """
        foo

        A=B
        TEST=VALUE
        """);
  }

  @Test
  public void testAddLabelNoEmptyLineBeforeGroup() throws Exception {
    checkAddLabel(
        """
        foo
        A=B

        """,
        """
        foo
        A=B

        TEST=VALUE
        """);
  }

  @Test
  public void testAddLabelNoGroupNoEndLine() throws Exception {
    checkAddLabel(
        """
        foo
        A=B\
        """,
        """
        foo
        A=B

        TEST=VALUE
        """);
  }

  @Test
  public void testAddOrReplaceExistingLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        other=other
        """,
        "ctx.add_or_replace_label('SOME', 'REPLACED')",
        """
        Foo

        SOME=REPLACED
        other=other
        """);
  }

  @Test
  public void testAddTextBeforeLabels() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.add_text_before_labels('\\nFixes #1234')",
        """
        Foo

        Fixes #1234

        SOME=TEST
        """);
  }

  @Test
  public void testInvalidLabel() throws Exception {
    TransformWork work =
        create(
            """
            Foo

            SOME=TEST
            OTHER=FOO
            """);

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> work.removeLabel("[invalid]", /* wholeMessage= */ true));

    assertThat(e).hasMessageThat().isEqualTo("Label '[invalid]' is not a valid label");
  }

  @Test
  public void testAddHiddenLabel() throws Exception {
    TransformWork work =
        create(
            """
            Foo

            SOME=TEST
            """);
    ExplicitReversal t =
        skylark.eval(
            "t",
            """
            def user_transform(ctx):
                ctx.add_label('FOO','BAR', hidden = True)
                ctx.add_label('FOO','BAR', hidden = True)
            t = core.transform([user_transform])
            """);
    t.transform(work);
    assertThat(work.getMessage())
        .isEqualTo(
            """
            Foo

            SOME=TEST
            """);
    assertThat(work.getAllLabels("FOO").getImmutableList()).isEqualTo(ImmutableList.of("BAR"));
  }

  @Test
  public void testGetHiddenLabel() throws Exception {
    TransformWork work =
        create(
            """
            Foo

            SOME=TEST
            """);
    ExplicitReversal t =
        skylark.eval(
            "t",
            """
            def user_transform(ctx):
                ctx.add_label('FOO','ONE', hidden = True)
                ctx.add_label('FOO','TWO', hidden = True)
            t = core.transform([user_transform])
            """);
    t.transform(work);
    assertThat(work.getLabel("FOO")).isEqualTo("TWO");
    assertThat(work.getAllLabels("FOO").getImmutableList())
        .isEqualTo(ImmutableList.of("ONE", "TWO"));
  }

  @Test
  public void testAddTwoDifferentHiddenLabels() throws Exception {
    TransformWork work =
        create(
            """
            Foo

            SOME=TEST
            """);
    ExplicitReversal t =
        skylark.eval(
            "t",
            """
            def user_transform(ctx):
                ctx.add_label('ONE','val2', hidden = True)
                ctx.add_label('ONE','val2', hidden = True)
                ctx.add_label('ONE','val1', hidden = True)
                ctx.add_label('TWO','val2', hidden = True)
                ctx.add_label('TWO','val2', hidden = True)
                ctx.add_label('TWO','val1', hidden = True)
            t = core.transform([user_transform])
            """);
    t.transform(work);
    assertThat(work.getAllLabels("ONE").getImmutableList())
        .isEqualTo(ImmutableList.of("val2", "val1"));
    assertThat(work.getAllLabels("TWO").getImmutableList())
        .isEqualTo(ImmutableList.of("val2", "val1"));
  }

  @Test
  public void testAddLabelWhitespaceInMsg() throws Exception {
    checkAddLabel(
        "    foo",
        """
            foo

        TEST=VALUE
        """);
  }

  @Test
  public void testAddLabelLastParagraphList() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

          - list
          - other
        """,
        "ctx.add_label('TEST', 'VALUE')",
        """
        Foo

          - list
          - other

        TEST=VALUE
        """);
  }

  @Test
  public void testAddLabelLastParagraphContainsLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

          - list
        I_AM_A: Label
          - other
        """,
        "ctx.add_label('TEST', 'VALUE')",
        """
        Foo

          - list
        I_AM_A: Label
          - other
        TEST=VALUE
        """);
  }

  @Test
  public void testAddTextBeforeLabelsNoGroup() throws Exception {
    checkLabelWithSkylark(
        """
        Foo
        """,
        "ctx.add_text_before_labels('\\nFixes #1234')",
        """
        Foo

        Fixes #1234
        """);
  }

  @Test
  public void testReplaceLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.replace_label('SOME', 'REPLACED')",
        """
        Foo

        SOME=REPLACED
        """);
  }

  @Test
  public void testReplaceNonExistentLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        FOO=TEST
        """,
        "ctx.replace_label('SOME', 'REPLACED')",
        """
        Foo

        FOO=TEST
        """);
  }

  @Test
  public void testReplaceNonExistentLabelNoGroup() throws Exception {
    checkLabelWithSkylark(
        """
        Foo
        """,
        "ctx.replace_label('SOME', 'REPLACED')",
        """
        Foo
        """);
  }

  @Test
  public void testsDeleteNotFound() throws Exception {
    checkLabelWithSkylark(
        """
        Foo
        """,
        "ctx.remove_label('SOME', False)",
        """
        Foo
        """);
  }

  @Test
  public void testsDeleteNotFound_whole() throws Exception {
    checkLabelWithSkylark(
        """
        Foo
        """,
        "ctx.remove_label('SOME', True)",
        """
        Foo
        """);
  }

  @Test
  public void testsDeleteLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.remove_label('SOME', False)",
        """
        Foo
        """);
  }

  @Test
  public void testsDeleteLabel_whole() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.remove_label('SOME', True)",
        """
        Foo
        """);
  }

  @Test
  public void testsDeleteOnlyOneLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        OTHER=aaa
        """,
        "ctx.remove_label('SOME', False)",
        """
        Foo

        OTHER=aaa
        """);
  }

  @Test
  public void testsDeleteOnlyOneLabel_whole() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        OTHER=aaa
        """,
        "ctx.remove_label('SOME', True)",
        """
        Foo

        OTHER=aaa
        """);
  }

  @Test
  public void testsDeleteNonExistentLabel() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.remove_label('FOO', False)",
        """
        Foo

        SOME=TEST
        """);
  }

  @Test
  public void testsDeleteNonExistentLabel_whole() throws Exception {
    checkLabelWithSkylark(
        """
        Foo

        SOME=TEST
        """,
        "ctx.remove_label('FOO', True)",
        """
        Foo

        SOME=TEST
        """);
  }

  private Change<DummyRevision> toChange(DummyRevision dummyRevision) {
    return TransformWorks.toChange(dummyRevision, ORIGINAL_AUTHOR);
  }

  @Test
  public void testDateFormat() throws Exception {
    checkLabelWithSkylark("", "ctx.set_message(ctx.now_as_string())",
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(ZonedDateTime.now(ZoneOffset.UTC)));

    boolean isDst = TimeZone.getTimeZone("Europe/Madrid").inDaylightTime(new Date());
    checkLabelWithSkylark("",
        "ctx.set_message(ctx.now_as_string('yyyy MM dd XXX', 'Europe/Madrid'))",
        DateTimeFormatter.ofPattern("yyyy MM dd").format(
            ZonedDateTime.now(ZoneId.of("Europe/Madrid"))) + (isDst ? " +02:00" : " +01:00"));

    checkLabelWithSkylark("",
        "ctx.set_message(ctx.now_as_string('yyyy MM dd VV', 'Europe/Madrid'))",
        DateTimeFormatter.ofPattern("yyyy MM dd").format(
            ZonedDateTime.now(ZoneId.of("Europe/Madrid"))) + " Europe/Madrid");
  }

  @Test
  public void testGetLabel() {
    TransformWork work =
        create(
                """
                Foo

                SOME=TEST
                """)
            .withChanges(
                new Changes(
                    ImmutableList.of(
                        toChange(
                            new DummyRevision("1")
                                .withLabels(
                                    ImmutableListMultimap.of(
                                        "ONE", "one", "SOME", "SHOULD_NOT_HAPPEN"))),
                        toChange(
                            new DummyRevision("2")
                                .withLabels(ImmutableListMultimap.of("TWO", "two"))),
                        toChange(
                            new DummyRevision("3")
                                .withLabels(ImmutableListMultimap.of("THREE", "three")))),
                    ImmutableList.of()))
            .withResolvedReference(
                new DummyRevision("resolved")
                    .withLabels(
                        ImmutableListMultimap.of(
                            "RESOLVED",
                            "resolved",
                            "ONE",
                            "SHOULD_NOT_HAPPEN",
                            "SOME",
                            "SHOULD_NOT_HAPPEN")));
    work.setAuthor(ORIGINAL_AUTHOR);

    assertThat(work.getAllLabels("COPYBARA_AUTHOR")).containsExactly("Foo Bar", "foo@bar.com");
    assertThat(work.getLabel("SOME")).isEqualTo("TEST");
    assertThat(work.getLabel("ONE")).isEqualTo("one");
    assertThat(work.getLabel("TWO")).isEqualTo("two");
    assertThat(work.getLabel("THREE")).isEqualTo("three");
    assertThat(work.getLabel("RESOLVED")).isEqualTo("resolved");
    assertThat(work.getLabel("FOO")).isEqualTo(null);
  }

  @Test
  public void testGetDateLabel_null() {
    TransformWork workNullTime =
        create(
                """
                Foo

                SOME=TEST
                """)
            .withCurrentRev(new DummyRevision("1").withTimestamp(null));
    assertThat(workNullTime.getLabel("COPYBARA_CURRENT_REV_DATE_TIME")).isEqualTo(null);
  }

  @Test
  public void testGetDateLabel_value() {
    TransformWork work =
        create(
                """
                Foo

                SOME=TEST
                """)
            .withCurrentRev(
                new DummyRevision("1")
                    .withTimestamp(
                        ZonedDateTime.ofInstant(
                            Instant.ofEpochSecond(1591743457), ZoneId.of("UTC"))));
    assertThat(work.getLabel("COPYBARA_CURRENT_REV_DATE_TIME")).isEqualTo("2020-06-09T22:57:37Z");
   }

  @Test
  public void testReversible() throws ValidationException {
    TransformWork work =
        create(
            """
            Foo

            SOME=TEST
            OTHER=FOO
            """);
    work.addOrReplaceLabel("EXAMPLE", "VALUE", "=");
    work.replaceLabel("EXAMPLE", "OTHER VALUE", "=", true);
    assertThat(work.getMessage())
        .isEqualTo(
            """
            Foo

            SOME=TEST
            OTHER=FOO
            EXAMPLE=OTHER VALUE
            """);
    work.removeLabel("EXAMPLE", /*wholeMessage=*/true);
    assertThat(work.getMessage())
        .isEqualTo(
            """
            Foo

            SOME=TEST
            OTHER=FOO
            """);
  }

  @Test
  public void testGetLabelFromCurrentRev_whenNoChangesMigrated() {
    TransformWork work =
        create("Foo :)")
            .withChanges(new Changes(ImmutableList.of(), ImmutableList.of()))
            .withCurrentRev(
                new DummyRevision("foo")
                    .withLabels(ImmutableListMultimap.of("CURRENT_VERSION", "1")))
            .withResolvedReference(
                new DummyRevision("foo")
                    .withLabels(ImmutableListMultimap.of("CURRENT_VERSION", "2")));
    assertThat(work.getLabel("CURRENT_VERSION")).isEqualTo("1");
  }

  @Test
  public void testConsole() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
           ctx.console.progress('Progress message')
           ctx.console.info('Informational message')
           ctx.console.warn('Warning message')
        """);

    console.assertThat().onceInLog(MessageType.PROGRESS, "Progress message");
    console.assertThat().onceInLog(MessageType.INFO, "Informational message");
    console.assertThat().onceInLog(MessageType.WARNING, "Warning message");
  }

  @Test
  public void squashModeSquashFlagSetToFalse_setsTransformWorkModeToSquash()
      throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /* matchesGlob= */ true);
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setForce(true);
    var unused = options.setSquash(false);
    runWorkflow(
        "test",
        """
        def test(ctx):
            ctx.console.info('Mode is: ' + ctx.mode)
        """,
        "SQUASH",
        options);
    console.assertThat().onceInLog(MessageType.INFO, "Mode is: SQUASH");
  }

  @Test
  public void iterativeModeSquashFlagSetToFalse_setsTransformWorkModeToIterative()
      throws IOException, ValidationException, RepoException {
    origin.addSimpleChangeWithFixedReference(0, "0");
    origin.addSimpleChangeWithFixedReference(1, "1");
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setLastRevision("0");
    options.setForce(true);
    var unused = options.setSquash(false);

    runWorkflow(
        "test",
        """
        def test(ctx):
            ctx.console.info('Mode is: ' + ctx.mode)
        """,
        "ITERATIVE",
        options);
    console.assertThat().onceInLog(MessageType.INFO, "Mode is: ITERATIVE");
  }

  @Test
  public void iterativeModeSquashFlagSetToTrue_setsTransformWorkModeToSquash()
      throws IOException, ValidationException, RepoException {
    origin.addSimpleChangeWithFixedReference(0, "0");
    origin.addSimpleChangeWithFixedReference(1, "1");
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setLastRevision("0");
    options.setForce(true);
    var unused = options.setSquash(true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            ctx.console.info('Mode is: ' + ctx.mode)
        """,
        "ITERATIVE",
        options);
    console.assertThat().onceInLog(MessageType.INFO, "Mode is: SQUASH");
  }

  @Test
  public void testConsoleError() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                runWorkflow(
                    "test",
                    """
                    def test(ctx):
                       ctx.console.error('Error message')
                       ctx.console.error('Another error message')
                    """));
    assertThat(e).hasMessageThat().isEqualTo("2 error(s) while executing test");
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, "Error message")
        .onceInLog(MessageType.ERROR, "Another error message");
  }

  @Test
  public void testWithCurrentRev() {
    assertThat(TransformWorks.of(workdir, "test", console).withCurrentRev(new DummyRevision("a"))
        .isInsideExplicitTransform()).isFalse();
  }

  @Test
  public void testWithConsole() {
    assertThat(TransformWorks.of(workdir, "test", console)
        .insideExplicitTransform()
        .withConsole(console)
        .isInsideExplicitTransform()).isTrue();
  }

  @Test
  public void testRunGlob() throws IOException, ValidationException, RepoException {
    checkGlob("run");
  }

  @Test
  public void testRunGlobWithList() throws IOException, ValidationException, RepoException {
    checkGlob("list");
  }

  private void checkGlob(String method) throws IOException, RepoException, ValidationException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunGlob");
    touchFile(base, "folder/file.txt");
    touchFile(base, "folder/subfolder/file.txt");
    touchFile(base, "folder/subfolder/file.java");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            message = ''
            for f in sorted(ctx.%s(glob(['**']))):
                message += f.path +'\\n'
            ctx.set_message(message)\
        """
            .formatted(method));

    assertThat(destination.processed.get(0).getChangesSummary())
        .isEqualTo(
            """
            folder/file.txt
            folder/subfolder/file.java
            folder/subfolder/file.txt
            """);
  }

  @Test
  public void testCreateSymlink() throws Exception {
    checkCreateSymlink("a/b/c/d1", "a/b/c/d2");
    checkCreateSymlink("a/b/c/d1", "a/b/d/d2");
    checkCreateSymlink("a/b/c/d1", "a/d/e/d2");
    checkCreateSymlink("a/d1", "a/b/c/d1");
    checkCreateSymlink("f1", "f2");
    checkCreateSymlink("a/d1", "d2");
    checkCreateSymlink("d1", "b/d2");
    ValidationException regularFile =
        assertThrows(ValidationException.class, () -> checkCreateSymlink("d1", "d1"));
    assertThat(regularFile).hasMessageThat().contains("'d1' already exist and is a regular file");
  }

  @Test
  public void testCreateSymlinkDir() throws Exception {

    FileSystem fileSystem = Jimfs.newFileSystem();
    FileUtil.deleteRecursively(workdir);
    Path base = fileSystem.getPath("testRunGlob");
    writeFile(base, "b/test.txt", "FOOOO");
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    Path[] workdir = new Path[]{null};
    destination.onWrite(transformResult -> workdir[0] = transformResult.getPath());

    runWorkflow(
        "test",
        """
        def test(ctx):
            ctx.create_symlink(ctx.new_path('a'), ctx.new_path('b'))
        """);

    assertThat(workdir[0] != null).isTrue();

    FileSubjects.assertThatPath(workdir[0])
        .containsFile("b/test.txt", "FOOOO")
        .containsFile("a/test.txt", "FOOOO")
        .containsSymlink("a", "b")
        .containsNoMoreFiles();
  }

  private void checkCreateSymlink(String link, String target)
      throws IOException, RepoException, ValidationException {

    FileSystem fileSystem = Jimfs.newFileSystem();
    FileUtil.deleteRecursively(workdir);
    Path base = fileSystem.getPath("testRunGlob");
    writeFile(base, target, "FOOOO");
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    Path[] workdir = new Path[]{null};
    destination.onWrite(transformResult -> workdir[0] = transformResult.getPath());

    runWorkflow(
        "test",
        """
        def test(ctx):
            ctx.create_symlink(ctx.new_path('%s'), ctx.new_path('%s'))
        """
            .formatted(link, target));

    assertThat(workdir[0] != null).isTrue();

    FileSubjects.assertThatPath(workdir[0])
        .containsFile(target, "FOOOO")
        .containsSymlink(link, target)
        .containsNoMoreFiles();
  }

  @Test
  public void testRunDynamicTransforms() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunDynamicTransforms");
    touchFile(base, "folder/file1.txt");
    touchFile(base, "folder/file2.txt");
    touchFile(base, "folder/file3.txt");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            message = ''
            for f in ctx.run(glob(['**.txt'])):
                ctx.run(core.move(f.path, 'other/folder/prefix_' + f.name))\
        """);

    assertThat(destination.processed.get(0).getWorkdir().keySet()).containsExactly(
        "other/folder/prefix_file1.txt",
        "other/folder/prefix_file2.txt",
        "other/folder/prefix_file3.txt");
  }

  @Test
  public void testReadAndWrite() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunDynamicTransforms");
    writeFile(base, "folder/file.txt", "foo");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    String now = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(ZonedDateTime.now(ZoneOffset.UTC));

    runWorkflow(
        "test",
        """
        def test(ctx):
            path = ctx.new_path('folder/file.txt')
            ctx.read_path(path)
            ctx.write_path(path, ctx.read_path(path) + ctx.now_as_string())\
        """);

    assertThat(destination.processed.get(0).getWorkdir())
        .containsEntry("folder/file.txt", "foo" + now);
  }

  @Test
  public void testWriteCreatesSubdirs() throws Exception {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testRunDynamicTransforms");
    writeFile(base, "folder/file.txt", "foo");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            path = ctx.new_path('folder/file.txt')
            contents = ctx.read_path(path)
            ctx.write_path(ctx.new_path('other_folder/other_file.txt'), contents)\
        """);

    assertThat(destination.processed.get(0).getWorkdir())
        .containsEntry("other_folder/other_file.txt", "foo");

  }

  @Test
  public void testTreeStateRestored() throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("testTreeStateRestored");
    writeFile(base, "folder/file1.txt", "aaa");
    writeFile(base, "folder/file2.txt", "aaa");
    writeFile(base, "folder/file3.txt", "aaa");

    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            message = ''
            for f in ctx.run(glob(['**.txt'])):
                ctx.run(core.move(f.path, 'prefix_' + f.name))
                ctx.run(core.replace(before ='aaa', after = 'bbb', paths = glob(['prefix_' + f.name])))\
        """);

    assertThat(destination.processed.get(0).getWorkdir())
        .containsExactlyEntriesIn(ImmutableMap.of(
            "prefix_file1.txt", "bbb",
            "prefix_file2.txt", "bbb",
            "prefix_file3.txt", "bbb"));
  }

  @Test
  public void testRunFileOps() throws IOException, ValidationException, RepoException {
    checkPathOperations(
        "folder/file.txt",
        """
        path: folder/file.txt
        name: file.txt
        file exists: True
        sibling path: folder/baz.txt
        parent path: folder
        parent parent path:\s
        parent parent parent: None
        """,
        true);
  }

  @Test
  public void testRunFileOpsSubSubFolder() throws IOException, ValidationException, RepoException {
    checkPathOperations(
        "folder/other/file.txt",
        """
        path: folder/other/file.txt
        name: file.txt
        file exists: False
        sibling path: folder/other/baz.txt
        parent path: folder/other
        parent parent path: folder
        parent parent parent:\s
        """,
        false);
  }

  private void checkPathOperations(String filePath, String output, boolean createFile)
      throws IOException, RepoException, ValidationException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    touchFile(base.resolve("not_important.txt"), "");
    Files.createDirectories(workdir.resolve("folder"));
    if (createFile) {
      touchFile(base.resolve(filePath), "");
    }
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            f = ctx.new_path('%s')
            message = 'path: ' + f.path +'\\n'
            message += 'name: ' + f.name +'\\n'
            message += 'file exists: ' + str(f.exists()) +'\\n'
            message += 'sibling path: ' + f.resolve_sibling('baz.txt').path + '\\n'
            message += 'parent path: ' + f.parent.path + '\\n'
            message += 'parent parent path: ' + f.parent.parent.path + '\\n'
            message += 'parent parent parent: ' + str(f.parent.parent.parent) + '\\n'
            ctx.set_message(message)\
        """
            .formatted(filePath));

    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(
        output);
  }

  @Test
  public void testAttrSize() throws RepoException, IOException, ValidationException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("foo");
    Files.createDirectories(base);
    Files.write(base.resolve("file.txt"), "1234567890".getBytes(UTF_8));
    Files.createDirectories(workdir.resolve("folder"));
    origin.addChange(0, base, "message", /*matchesGlob=*/true);

    runWorkflow(
        "test",
        """
        def test(ctx):
            size = ctx.run(glob(['**.txt']))[0].attr.size
            ctx.console.info('File size: ' + str(size))\
        """);

    console.assertThat().onceInLog(MessageType.INFO, "File size: 10");
  }

  @Test
  public void canAccessModeInDynamicTransform() throws Exception {
    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                ctx.console.info('Mode is: ' + ctx.mode)

            transformation = core.transform([test])
            """);

    transformation.transform(TransformWorks.of(workdir, "test", console, "SQUASH"));
    console.assertThat().onceInLog(MessageType.INFO, "Mode is: SQUASH");
  }

  @Test
  public void testSymlinks_escape() throws Exception {
    Path base = Files.createDirectories(workdir.resolve("foo"));
    Path badTarget = Files.createTempFile("badPrefix", "THE CONTENT");
    Path badLink =
        Files.createSymbolicLink(
            Files.createDirectory(base.resolve("a")).resolve("file.txt"), badTarget);
    Files.createSymbolicLink(base.resolve("a/chained.txt"), badLink);

    Transformation readEscapedSymlink =
        skylark.eval(
            "transformation",
"""
def test(ctx):
    path = ctx.new_path("foo/a/file.txt")
    ctx.console.info(ctx.read_path(path))

transformation = core.transform([test])
""");

    ValidationException veRead =
        assertThrows(
            ValidationException.class,
            () -> readEscapedSymlink.transform(TransformWorks.of(workdir, "test", console)));

    assertThat(veRead).hasMessageThat().contains("is not inside the checkout directory");

    Transformation writeEscapedSymlink =
        skylark.eval(
            "transformation",
"""
def test(ctx):
    path = ctx.new_path("foo/a/file.txt")
    ctx.console.info(ctx.write_path(path, "foo"))

transformation = core.transform([test])
""");

    ValidationException veWrite =
        assertThrows(
            ValidationException.class,
            () -> writeEscapedSymlink.transform(TransformWorks.of(workdir, "test", console)));
    assertThat(veWrite).hasMessageThat().contains("is not inside the checkout directory");
  }

  @Test
  public void symlinks_withCwdSymlink_worksCorrectly() throws Exception {
    workdir =
        Files.createSymbolicLink(workdir.resolve("symlink"), workdir).resolve("nested_workdir");
    Path base = Files.createDirectories(workdir.resolve("foo"));
    Files.write(
        Files.createDirectory(base.resolve("a")).resolve("file.txt"),
        "THE CONTENT".getBytes(UTF_8));
    Files.createSymbolicLink(
        Files.createDirectory(base.resolve("b")).resolve("file.txt"), Paths.get("../a/file.txt"));
    Files.write(
        Files.createDirectories(base.resolve("c/folder")).resolve("file.txt"),
        "THE CONTENT2".getBytes(UTF_8));
    Files.createSymbolicLink(base.resolve("c/file.txt"), Paths.get("folder/file.txt"));

    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                for f in ctx.run(glob(['**'])):
                    ctx.console.info(f.path + ':' + str(f.attr.symlink))
                    if f.attr.symlink:
                        target = f.read_symlink()
                        ctx.console.info(f.path + ' -> ' + target.path)
                        ctx.console.info(f.path + ' content ' + ctx.read_path(f))
                        ctx.console.info(target.path + ' content ' + ctx.read_path(target))

            transformation = core.transform([test])
            """);

    transformation.transform(TransformWorks.of(workdir, "test", console));
    console.assertThat().onceInLog(MessageType.INFO, "foo/a/file.txt:False");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt:True");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt -> foo/a/file.txt");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt content THE CONTENT");
    console.assertThat().onceInLog(MessageType.INFO, "foo/a/file.txt content THE CONTENT");

    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt:True");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/folder/file.txt:False");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt -> foo/c/folder/file.txt");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt content THE CONTENT2");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/folder/file.txt content THE CONTENT2");
  }

  @Test
  public void testSymlinks() throws Exception {
    Path base = Files.createDirectories(workdir.resolve("foo"));
    Files.write(Files.createDirectory(base.resolve("a")).resolve("file.txt"),
        "THE CONTENT".getBytes(UTF_8));
    Files.createSymbolicLink(Files.createDirectory(base.resolve("b")).resolve("file.txt"),
        Paths.get("../a/file.txt"));
    Files.write(Files.createDirectories(base.resolve("c/folder")).resolve("file.txt"),
        "THE CONTENT2".getBytes(UTF_8));
    Files.createSymbolicLink(base.resolve("c/file.txt"), Paths.get("folder/file.txt"));

    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                for f in ctx.run(glob(['**'])):
                    ctx.console.info(f.path + ':' + str(f.attr.symlink))
                    if f.attr.symlink:
                        target = f.read_symlink()
                        ctx.console.info(f.path + ' -> ' + target.path)
                        ctx.console.info(f.path + ' content ' + ctx.read_path(f))
                        ctx.console.info(target.path + ' content ' + ctx.read_path(target))

            transformation = core.transform([test])
            """);

    transformation.transform(TransformWorks.of(workdir, "test", console));
    console.assertThat().onceInLog(MessageType.INFO, "foo/a/file.txt:False");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt:True");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt -> foo/a/file.txt");
    console.assertThat().onceInLog(MessageType.INFO, "foo/b/file.txt content THE CONTENT");
    console.assertThat().onceInLog(MessageType.INFO, "foo/a/file.txt content THE CONTENT");

    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt:True");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/folder/file.txt:False");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt -> foo/c/folder/file.txt");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/file.txt content THE CONTENT2");
    console.assertThat().onceInLog(MessageType.INFO, "foo/c/folder/file.txt content THE CONTENT2");
  }

  @Test
  public void testSymlinks_outside() throws IOException, ValidationException {
    Path base = Files.createDirectories(workdir.resolve("foo"));
    Path tempFile = Files.createTempFile("foo", "bar");

    Files.write(tempFile, "THE CONTENT".getBytes(UTF_8));
    Files.createSymbolicLink(base.resolve("symlink"), tempFile);

    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                ctx.new_path('foo/symlink').read_symlink()

            transformation = core.transform([test])
            """);

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> transformation.transform(TransformWorks.of(workdir, "test", console)));
    assertThat(e).hasMessageThat().contains("points to a file outside the checkout dir");
  }

  @Test
  public void testPathOperations_withCoreReplace() throws Exception {
    Files.write(workdir.resolve("file1.txt"), "contents of file 1; x".getBytes(UTF_8));

    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                ctx.run(core.replace('x', 'y'))
                ctx.write_path(ctx.new_path('file2.txt'), 'contents of file 2; y')
                ctx.run(core.replace('y', 'z'))

            transformation = core.transform([test])
            """);

    TransformWork work = TransformWorks.of(workdir, "test", console);
    transformation.transform(work);

    assertThat(Files.readAllLines(workdir.resolve("file1.txt")))
        .containsExactly("contents of file 1; z");
    assertThat(Files.readAllLines(workdir.resolve("file2.txt")))
        .containsExactly("contents of file 2; z");
  }

  @Test
  public void testPathOperations_setExecutable() throws Exception {
    Files.write(workdir.resolve("file1.txt"), "contents of file1".getBytes(UTF_8));
    Files.write(workdir.resolve("file2.txt"), "contents of file2".getBytes(UTF_8));
    workdir.resolve("file2.txt").toFile().setExecutable(true);

    Transformation transformation =
        skylark.eval(
            "transformation",
            """
            def test(ctx):
                ctx.set_executable(ctx.new_path('file1.txt'), True)
                ctx.set_executable(ctx.new_path('file2.txt'), False)

            transformation = core.transform([test])
            """);

    TransformWork work = TransformWorks.of(workdir, "test", console);
    transformation.transform(work);

    assertThat(Files.isExecutable(workdir.resolve("file1.txt"))).isTrue();
    assertThat(Files.isExecutable(workdir.resolve("file2.txt"))).isFalse();
  }

  private void touchFile(Path base, String path) throws IOException {
    writeFile(base, path, "");
  }

  private void writeFile(Path base, String path, String content) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    Files.write(base.resolve(path), content.getBytes(UTF_8));
  }

  private void runWorkflow(String functionName, String function)
      throws RepoException, IOException, ValidationException {
    skylark
        .loadConfig(
            """
            %s
            core.workflow(
                name = 'default',
                origin = testing.origin(),
                destination = testing.destination(),
                transformations = [%s],
                authoring = authoring.pass_thru('foo <foo@foo.com>'),
            )
            """
                .formatted(function, functionName))
        .getMigration("default")
        .run(workdir, ImmutableList.of());
  }

  private void runWorkflow(
      String functionName, String function, String mode, OptionsBuilder options)
      throws RepoException, IOException, ValidationException {
    SkylarkTestExecutor skylark = new SkylarkTestExecutor(options);

    skylark
        .loadConfig(
            """
            %s
            core.workflow(
                name = 'default',
                origin = testing.origin(),
                destination = testing.destination(),
                transformations = [%s],
                authoring = authoring.pass_thru('foo <foo@foo.com>'),
                mode = '%s')
            """
                .formatted(function, functionName, mode))
        .getMigration("default")
        .run(workdir, ImmutableList.of());
  }

  private void checkAddLabel(String originalMsg, String expected) throws Exception {
    checkLabelWithSkylark(originalMsg,
        "ctx.add_label('TEST','VALUE')",
        expected);
    checkLabelWithSkylark(originalMsg,
        "ctx.add_or_replace_label('TEST','VALUE')",
        expected);
  }

  private TransformWork create(String msg) {
    return TransformWorks.of(FileSystems.getDefault().getPath("/"), msg, console);
  }

  private void checkLabelWithSkylark(String originalMsg, String transform,
      String expectedOutputMsg)
      throws Exception {
    TransformWork work = create(originalMsg);
    ExplicitReversal t =
        skylark.eval(
            "t",
            """
            def user_transform(ctx):
                %s
            t = core.transform([user_transform])
            """
                .formatted(transform));
    t.transform(work);
    assertThat(work.getMessage()).isEqualTo(expectedOutputMsg);
  }
}
