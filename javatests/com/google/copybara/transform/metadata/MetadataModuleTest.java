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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Author;
import com.google.copybara.Config;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowMode;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
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

  private static final Author ORIGINAL_AUTHOR = new Author("Foo Bar", "foo@bar.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;

  private SkylarkParser skylark;

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
    origin.addSimpleChange(0, "first commit\n\nExtended text")
        .setAuthor(new Author("Foo Bar", "foo@bar.com"))
        .addSimpleChange(1, "second commit\n\nExtended text")
        .setAuthor(new Author("Foo Baz", "foo@baz.com"))
        .addSimpleChange(2, "third commit\n\nExtended text");

    options.setLastRevision("0");
    testingConsole = new TestingConsole();
    options.setConsole(testingConsole);
  }

  private void passThruAuthoring() {
    authoring = "authoring.pass_thru('" + DEFAULT_AUTHOR + "')";
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8))
            , "copy.bara.sky"),
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
    options.setLastRevision(origin.getHead());

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
  public void testAddHeaderLabelNotFound() throws Exception {
    options.setLastRevision(origin.getHead());
    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]')");
    origin.addSimpleChange(0, "foo");

    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find label 'LABEL'");

    wf.run(workdir, /*sourceRef=*/null);
  }

  @Test
  public void testAddHeaderLabelNotFoundIgnore() throws Exception {
    options.setLastRevision(origin.getHead());

    Workflow wf = createWorkflow(WorkflowMode.ITERATIVE,
        "metadata.add_header('[HEADER with ${LABEL}]', "
            + "ignore_if_label_not_found = True)");

    origin.addSimpleChange(0, "A change\n");

    wf.run(workdir, /*sourceRef=*/null);

    ProcessedChange change = Iterables.getLast(destination.processed);

    assertThat(change.getChangesSummary()).isEqualTo("A change\n");
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

  private void checkScrubber(String commitMsg, String scrubber, String expectedMsg)
      throws IOException, ValidationException, RepoException {
    Workflow<?> wf = createWorkflow(WorkflowMode.ITERATIVE, scrubber);
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
        + "core.project( name = 'copybara_project')\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin =  testing.origin(),\n"
        + "    authoring = " + authoring + "\n,"
        + "    destination = testing.destination(),\n"
        + "    mode = '" + mode + "',\n"
        + "    transformations = [" + Joiner.on(", ").join(transforms) + "]\n"
        + ")\n");
    return (Workflow) config.getActiveMigration();
  }
}
