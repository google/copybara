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

package com.google.copybara.transform.metadata;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.TransformWorks.EMPTY_CHANGES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Config;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.RepoException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowMode;
import com.google.copybara.authoring.Author;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetadataModuleTest {

  private static final Author FOO_BAR = new Author("Foo Bar", "foo@bar.com");
  private static final Author FOO_BAZ = new Author("Foo Baz", "foo@baz.com");
  private static final Author ORIGINAL_AUTHOR = FOO_BAR;
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;

  private SkylarkParser skylark;
  private SkylarkTestExecutor skylarkExecutor;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;
  private TestingConsole testingConsole;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    authoring = "authoring.overwrite('" + DEFAULT_AUTHOR + "')";
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    origin = new DummyOrigin().setAuthor(ORIGINAL_AUTHOR);
    destination = new RecordsProcessCallDestination();
    options.setConsole(new TestingConsole());
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkParser(
        ImmutableSet.of(TestingModule.class, MetadataModule.class));
    skylarkExecutor = new SkylarkTestExecutor(options, MetadataModule.class);

    origin.addSimpleChange(0, "first commit\n\nExtended text")
        .setAuthor(FOO_BAR)
        .addSimpleChange(1, "second commit\n\nExtended text")
        .setAuthor(FOO_BAZ)
        .addSimpleChange(2, "third commit\n\nExtended text");

    options.setLastRevision("0");
    options.setForce(true); // We don't care about already migrated code
    testingConsole = new TestingConsole();
    options.setConsole(testingConsole);
  }

  private void passThruAuthoring() {
    authoring = "authoring.pass_thru('" + DEFAULT_AUTHOR + "')";
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"),
        options.build());
  }

  @Test
  public void testMessageTransformerForSquashCompact() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n',"
        + "  oldest_first = True,"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - 1 second commit by Foo Bar <foo@bar.com>\n"
            + "  - 2 third commit by Foo Baz <foo@baz.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashCompactNoRefOrAuthor() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n',"
        + "  oldest_first = True,"
        + "  show_ref = False,"
        + "  show_author = False,"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - second commit\n"
            + "  - third commit\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashReverse() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n'"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - 2 third commit by Foo Baz <foo@baz.com>\n"
            + "  - 1 second commit by Foo Bar <foo@bar.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForNoDescription() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n',"
        + "  show_description = False,"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - 2 by Foo Baz <foo@baz.com>\n"
            + "  - 1 by Foo Bar <foo@bar.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashExtended() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n',"
        + "  compact = False\n"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "--\n"
            + "2 by Foo Baz <foo@baz.com>:\n"
            + "\n"
            + "third commit\n"
            + "\n"
            + "Extended text\n"
            + "--\n"
            + "1 by Foo Bar <foo@bar.com>:\n"
            + "\n"
            + "second commit\n"
            + "\n"
            + "Extended text\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashExtendedNoDescription() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n',"
        + "  show_description = False,"
        + "  compact = False\n"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "--\n"
            + "2 by Foo Baz <foo@baz.com>\n"
            + "--\n"
            + "1 by Foo Bar <foo@bar.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testUseLastChange() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, "metadata.use_last_change()");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);

    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "third commit\n"
            + "\n"
            + "Extended text");
    assertThat(change.getAuthor()).isEqualTo(FOO_BAZ);
  }

  @Test
  public void testUseLastChange_defaultMessage() throws Exception {
    // Don't get an empty migration error:
    options.setForce(true);
    options.setLastRevision("2");

    createWorkflow(WorkflowMode.SQUASH,
        "metadata.use_last_change(default_message = 'Internal change\\n')")
        .run(workdir, "2");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo("Internal change\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testUseLastChange_noMessage() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, "metadata.use_last_change(message=False)");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo("Project import generated by Copybara.\n");
    assertThat(change.getAuthor()).isEqualTo(FOO_BAZ);
  }

  @Test
  public void testUseLastChange_noAuthor() throws Exception {
    runWorkflow(WorkflowMode.SQUASH, "metadata.use_last_change(author=False)");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo(""
        + "third commit\n"
        + "\n"
        + "Extended text");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testUseLastChange_nothingSet() throws Exception {
    thrown.expect(ValidationException.class);
    runWorkflow(WorkflowMode.SQUASH, "metadata.use_last_change(author=False, message=False)");
  }

  @Test
  public void testExposeLabel() throws Exception {
    checkExposeLabel("some message\n",
        "metadata.expose_label('SOME')",
        "some message\n\nSOME=value\n");

    checkExposeLabel("some message\n",
        "metadata.expose_label('NOT_FOUND')",
        "some message\n");

    checkExposeLabel("some message\n",
        "metadata.expose_label('SOME', new_name = 'OTHER')",
        "some message\n\nOTHER=value\n");

    checkExposeLabel("some message\n",
        "metadata.expose_label('SOME', separator = ': ')",
        "some message\n\nSOME: value\n");

    checkExposeLabel("some message\n\nSOME: oldvalue\n",
        "metadata.expose_label('SOME', new_name = 'OTHER')",
        "some message\n\nOTHER=oldvalue\n");

    checkExposeLabel("some message\n\nSOME=oldvalue\n",
        "metadata.expose_label('SOME', separator = ': ')",
        "some message\n\nSOME: oldvalue\n");
  }

  @Test
  public void testExposeLabel_no_ignore_if_not_found() throws Exception {
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find label NOT_FOUND");
    checkExposeLabel("some message\n",
        "metadata.expose_label('NOT_FOUND', ignore_label_not_found = False)",
        "DOES NOT MATTER");
  }

  private void checkExposeLabel(String msg, String transform, String expectedOutput)
      throws ValidationException, IOException {
    TransformWork tw = TransformWorks.of(workdir, msg, testingConsole)
        .withChanges(EMPTY_CHANGES)
        .withResolvedReference(new DummyRevision("123")
            .withLabels(ImmutableMap.of("SOME", "value")));
    Transformation t = skylarkExecutor.eval("t", "t = " + transform);
    t.transform(tw);
    assertThat(tw.getMessage()).isEqualTo(expectedOutput);
  }

  @Test
  public void testsSaveAuthor() throws Exception {
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, "metadata.save_author()");
    origin.setAuthor(new Author("keep me", "keep@me.com"))
        .addSimpleChange(0, "A change");
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).contains("ORIGINAL_AUTHOR=keep me <keep@me.com>");
  }

  @Test
  public void testsSaveAuthorOtherLabel() throws Exception {
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, "metadata.save_author('OTHER_LABEL')");
    origin.setAuthor(new Author("keep me", "keep@me.com"))
        .addSimpleChange(0, "A change");
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).contains("OTHER_LABEL=keep me <keep@me.com>");
    assertThat(change.getChangesSummary()).doesNotContain("ORIGINAL_AUTHOR");
  }

  @Test
  public void testsSaveReplaceAuthor() throws Exception {
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, "metadata.save_author()");
    origin.setAuthor(new Author("keep me", "keep@me.com"))
        .addSimpleChange(0, "A change\n\nORIGINAL_AUTHOR=bye bye <bye@bye.com>");
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).contains("ORIGINAL_AUTHOR=keep me <keep@me.com>");
    assertThat(change.getChangesSummary()).doesNotContain("bye bye");
  }

  @Test
  public void testRestoreAuthor() throws Exception {
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, "metadata.restore_author()");
    origin.setAuthor(new Author("remove me", "remove@me.com"))
        .addSimpleChange(0, "A change\n\nORIGINAL_AUTHOR=restore me <restore@me.com>\n");
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).doesNotContain("restore@me.com");
    assertThat(change.getChangesSummary()).doesNotContain("ORIGINAL_AUTHOR");
    assertThat(change.getAuthor().toString()).isEqualTo("restore me <restore@me.com>");
  }

  @Test
  public void testRestoreAuthorOtherLabel() throws Exception {
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, "metadata.restore_author('OTHER_LABEL')");
    origin.setAuthor(new Author("remove me", "remove@me.com"))
        .addSimpleChange(0, "A change\n\n"
            + "OTHER_LABEL=restore me <restore@me.com>\n"
            + "ORIGINAL_AUTHOR=no no <no@no.com>\n");
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).doesNotContain("restore@me.com");
    assertThat(change.getChangesSummary()).contains("ORIGINAL_AUTHOR=no no <no@no.com>");
    assertThat(change.getChangesSummary()).doesNotContain("OTHER_LABEL");
    assertThat(change.getAuthor().toString()).isEqualTo("restore me <restore@me.com>");
  }

  @Test
  public void testAddHeader() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());

    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]')");

    origin.addSimpleChange(0, ""
        + "A change\n"
        + "\n"
        + "LABEL=some label\n");

    wf.run(workdir, /*sourceRef=*/null);

    ProcessedChange change = Iterables.getLast(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo(""
        + "[HEADER with some label]\n"
        + "A change\n"
        + "\n"
        + "LABEL=some label\n");
  }

  @Test
  public void testAddHeader_noNewLine() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());

    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]: ', new_line = False)");

    origin.addSimpleChange(0, ""
        + "A change\n"
        + "\n"
        + "LABEL=some label\n");

    wf.run(workdir, /*sourceRef=*/null);

    ProcessedChange change = Iterables.getLast(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo(""
        + "[HEADER with some label]: A change\n"
        + "\n"
        + "LABEL=some label\n");
  }

  @Test
  public void testAddHeaderLabelNotFound() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]')");
    origin.addSimpleChange(0, "foo");

    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find label 'LABEL'");

    wf.run(workdir, /*sourceRef=*/null);
  }

  @Test
  public void testAddHeaderLabelNotFoundIgnore() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());

    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]', "
            + "ignore_label_not_found = True)");

    origin.addSimpleChange(0, "A change\n");

    wf.run(workdir, /*sourceRef=*/null);

    ProcessedChange change = Iterables.getLast(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo("A change\n");
  }

  @Test
  public void testMetadataVerifyMatch() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow<?, ?> wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.verify_match(\"<public>(.|\\n)*</public>\")");
    origin.addSimpleChange(0, "this\nshould\n<public>match\n\nreally!</public>match");
    wf.run(workdir, /*sourceRef=*/"HEAD");
  }

  @Test
  public void testMetadataVerifyMatchFails() throws Exception {
    Workflow<?, ?> wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.verify_match(\"foobar\")");
    try {
      wf.run(workdir, /*sourceRef=*/"HEAD");
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage()).contains("Could not find 'foobar' in the change message");
    }
  }

  @Test
  public void testMetadataVerifyNoMatch() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow<?, ?> wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.verify_match(\"foo\", verify_no_match = True)");
    origin.addSimpleChange(0, "bar");
    wf.run(workdir, /*sourceRef=*/"HEAD");
  }

  @Test
  public void testMetadataVerifyNoMatchFails() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow<?, ?> wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.verify_match(\"bar\", verify_no_match = True)");
    origin.addSimpleChange(0, "bar");
    try {
      wf.run(workdir, /*sourceRef=*/"HEAD");
      fail("Should fail");
    } catch (ValidationException e) {
      assertThat(e.getMessage()).contains("'bar' found in the change message");
    }
  }

  @Test
  public void testScrubber() throws Exception {
    checkScrubber("foo\nbar\nfooooo", "metadata.scrubber('foo+\\n?')", "bar\n");
  }

  @Test
  public void testScrubberFail() throws Exception {
    try {
      checkScrubber("not important", "metadata.scrubber('(')", "not important");
      fail();
    } catch (ValidationException e) {
      testingConsole.assertThat().onceInLog(MessageType.ERROR,
          "(.|\n)*error parsing regexp: missing closing \\)(.|\n)*");
    }
  }

  @Test
  public void testScrubberMissingGroups() throws Exception {
    try {
      checkScrubber("Automated g4 rollback of changelist",
          "metadata.scrubber(\n"
              + "    '^Automated g4 rollback of changelist*$',\n"
              + "    replacement =\n"
              + "        'BEGIN_PUBLIC\\nAutomated rollback of changelist $1\\nEND_PUBLIC',\n"
              + ")",
          "not important");
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage())
          .contains(
              "Could not find matching group. Are you missing a group in your regex "
                  + "'^Automated g4 rollback of changelist*$'?");
    }
  }

  @Test
  public void testScrubberForTags() throws Exception {
    checkScrubber(
        "this\nis\nvery confidential<public>but this is public\nvery public\n</public>"
            + "\nand this is a secret too\n",
        "metadata.scrubber('^(?:\\n|.)*<public>((?:\\n|.)*)</public>(?:\\n|.)*$', "
            + "replacement = '$1')",
        "but this is public\nvery public\n");
  }

  @Test
  public void testScrubberForLabel() throws Exception {
    checkScrubber(
        "this is public\nvery public\nCONFIDENTIAL:"
            + "\nand this\nis a secret\n",
        "metadata.scrubber('(^|\\n)CONFIDENTIAL:(.|\\n)*')",
        "this is public\nvery public");
  }

  @Test
  public void testMapAuthor() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, MetadataModule.MAP_AUTHOR_EXAMPLE_SIMPLE);

    origin.setAuthor(new Author("john", "john@example.com"))
        .addSimpleChange(0, "change 0");
    origin.setAuthor(new Author("Example Example", "madeupexample@google.com"))
        .addSimpleChange(1, "change 1");
    origin.setAuthor(new Author("John Example", "john.example@example.com"))
        .addSimpleChange(2, "change 2");
    origin.setAuthor(new Author("Other Example", "john.example@example.com"))
        .addSimpleChange(3, "change 3");
    origin.setAuthor(new Author("John Example", "other.example@example.com"))
        .addSimpleChange(4, "change 4");

    destination.processed.clear();
    wf.run(workdir, /*sourceRef=*/null);
    assertThat(destination.processed.get(0).getAuthor().toString())
        .isEqualTo("Some Person <some@example.com>");
    assertThat(destination.processed.get(1).getAuthor().toString())
        .isEqualTo("Other Person <someone@example.com>");
    assertThat(destination.processed.get(2).getAuthor().toString())
        .isEqualTo("Another Person <some@email.com>");
    assertThat(destination.processed.get(3).getAuthor().toString())
        .isEqualTo("Other Example <john.example@example.com>"); // No match
    assertThat(destination.processed.get(4).getAuthor().toString())
        .isEqualTo("John Example <other.example@example.com>"); // No match
  }

  @Test
  public void testMapAuthor_failIfNotFound() throws Exception {
    options.setLastRevision(origin.resolve("HEAD").asString());
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE, ""
        + "metadata.map_author({\n"
        + "    'a' : 'x <x@example.com>',\n"
        + "    'b@example.com' : 'y <y@example.com>',\n"
        + "    'c <c@example.com>' : 'z <z@example.com>',\n"
        + "}, fail_if_not_found = True)");

    origin.setAuthor(new Author("a", "a@example.com"))
        .addSimpleChange(0, "change 0");
    origin.setAuthor(new Author("b", "b@example.com"))
        .addSimpleChange(1, "change 1");
    origin.setAuthor(new Author("c", "c@example.com"))
        .addSimpleChange(2, "change 2");
    origin.setAuthor(new Author("Not found", "d@example.com"))
        .addSimpleChange(3, "change 3");

    destination.processed.clear();

    try {
      wf.run(workdir, /*sourceRef=*/null);
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessage("Cannot find a mapping for author 'Not found <d@example.com>'");
    }

    assertThat(destination.processed.get(0).getAuthor().toString())
        .isEqualTo("x <x@example.com>");
    assertThat(destination.processed.get(1).getAuthor().toString())
        .isEqualTo("y <y@example.com>");
    assertThat(destination.processed.get(2).getAuthor().toString())
        .isEqualTo("z <z@example.com>");
  }

  @Test
  public void testMapAuthor_reversible() throws Exception {
    Transformation m = skylarkExecutor.eval("m", "m = "
        + "metadata.map_author({\n"
        + "    'a <a@example.com>' : 'b <b@example.com>',\n"
        + "},"
        + "reversible = True)");

    TransformWork work = TransformWorks.of(workdir, "test", testingConsole);
    work.setAuthor(new Author("a", "a@example.com"));
    m.transform(work);

    assertThat(work.getAuthor().toString()).isEqualTo("b <b@example.com>");

    m.reverse().transform(work);

    assertThat(work.getAuthor().toString()).isEqualTo("a <a@example.com>");
  }

  @Test
  public void testMapAuthor_nonReversible() throws Exception {
    Transformation m = skylarkExecutor.eval("m", "m = "
        + "metadata.map_author({\n"
        + "    'a' : 'b <b@example.com>',\n"
        + "},"
        + "reversible = True)");
    thrown.expect(NonReversibleValidationException.class);
    m.reverse();
  }

  @Test
  public void testMapAuthor_reverseFaiIfNotFound() throws Exception {
    Transformation m = skylarkExecutor.eval("m", "m = "
        + "metadata.map_author({\n"
        + "    'a <a@example.com>' : 'b <b@example.com>',\n"
        + "},"
        + "reversible = True, reverse_fail_if_not_found = True)");

    TransformWork work = TransformWorks.of(workdir, "test", testingConsole);
    work.setAuthor(new Author("x", "x@example.com"));

    // normal workflow works:
    m.transform(work);

    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find a mapping for author 'x <x@example.com>'");
    m.reverse().transform(work);
  }

  private void checkScrubber(String commitMsg, String scrubber, String expectedMsg)
      throws IOException, ValidationException, RepoException {
    Workflow<?, ?> wf = createWorkflow(WorkflowMode.ITERATIVE, scrubber);
    origin.addSimpleChange(0, commitMsg);
    wf.run(workdir, /*sourceRef=*/null);
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getChangesSummary()).isEqualTo(expectedMsg);
  }

  private void runWorkflow(WorkflowMode mode, String... transforms)
      throws IOException, RepoException, ValidationException {
    createWorkflow(mode, transforms).run(workdir, "2");
  }

  private Workflow createWorkflow(WorkflowMode mode, String... transforms)
      throws IOException, ValidationException {
    passThruAuthoring();

    Config config = loadConfig(""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin =  testing.origin(),\n"
        + "    authoring = " + authoring + "\n,"
        + "    destination = testing.destination(),\n"
        + "    mode = '" + mode + "',\n"
        + "    transformations = [" + Joiner.on(", ").join(transforms) + "]\n"
        + ")\n");
    return (Workflow) config.getMigration("default");
  }
}
