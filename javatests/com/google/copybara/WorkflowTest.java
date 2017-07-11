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
import static com.google.copybara.WorkflowMode.CHANGE_REQUEST;
import static com.google.copybara.WorkflowMode.SQUASH;
import static com.google.copybara.git.GitRepository.bareRepo;
import static com.google.copybara.git.GitRepository.initScratchRepo;
import static com.google.copybara.testing.DummyOrigin.HEAD;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.authoring.Author;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.folder.FolderModule;
import com.google.copybara.git.GitModule;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.transform.metadata.MetadataModule;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WorkflowTest {

  private static final String PREFIX = "TRANSFORMED";
  private static final Author ORIGINAL_AUTHOR =
      new Author("Foo Bar", "foo@bar.com");
  private static final Author NOT_WHITELISTED_ORIGINAL_AUTHOR =
      new Author("Secret Coder", "secret@coder.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;

  private SkylarkParser skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private ImmutableList<String> transformations;
  private Path workdir;
  private boolean includeReleaseNotes;
  private String originFiles;
  private String destinationFiles;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    authoring = "authoring.overwrite('" + DEFAULT_AUTHOR + "')";
    includeReleaseNotes = false;
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    origin = new DummyOrigin().setAuthor(ORIGINAL_AUTHOR);
    originFiles = "glob(['**'], exclude = ['copy.bara.sky', 'excluded/**'])";
    destinationFiles = "glob(['**'])";
    destination = new RecordsProcessCallDestination();
    transformations = ImmutableList.of(""
        + "        core.replace(\n"
        + "             before = '${linestart}${number}',\n"
        + "             after = '${linestart}" + PREFIX + "${number}',\n"
        + "             regex_groups = {\n"
        + "                 'number'    : '[0-9]+',\n"
        + "                 'linestart' : '^',\n"
        + "             },\n"
        + "             multiline = True,"
        + "        )");
    options.setConsole(new TestingConsole());
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setForce(true); // Force by default unless we are testing the flag.
    skylark = new SkylarkParser(ImmutableSet.of(TestingModule.class, MetadataModule.class,
        FolderModule.class, GitModule.class));
  }

  private TestingConsole console() {
    return (TestingConsole) options.general.console();
  }

  private Workflow workflow() throws ValidationException, IOException {
    origin.addSimpleChange(/*timestamp*/ 42);
    return skylarkWorkflow("default", SQUASH);
  }

  private Workflow<?, ?> skylarkWorkflow(String name, WorkflowMode mode)
      throws IOException, ValidationException {
    List<String> transformations = Lists.newArrayList(this.transformations);
    if (includeReleaseNotes) {
      transformations.add("metadata.squash_notes()");
    }
    String config = ""
        + "core.workflow(\n"
        + "    name = '" + name + "',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    origin_files = " + originFiles + ",\n"
        + "    destination_files = " + destinationFiles + ",\n"
        + "    transformations = " + transformations + ",\n"
        + "    authoring = " + authoring + ",\n"
        + "    mode = '" + mode + "',\n"
        + ")\n";
    System.err.println(config);
    return (Workflow<?, ?>) loadConfig(config).getMigration(name);
  }

  private Workflow iterativeWorkflow(@Nullable String previousRef)
      throws ValidationException, IOException {
    options.workflowOptions.lastRevision = previousRef;
    options.general = new GeneralOptions(
        options.general.getFileSystem(), options.general.isVerbose(), console());
    return skylarkWorkflow("default", WorkflowMode.ITERATIVE);
  }

  private Workflow changeRequestWorkflow(@Nullable String baseline)
      throws ValidationException, IOException {
    options.workflowOptions.changeBaseline = baseline;
    return skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
  }

  @Test
  public void defaultNameIsDefault() throws Exception {
    assertThat(workflow().getName()).isEqualTo("default");
  }

  @Test
  public void toStringIncludesName() throws Exception {
    assertThat(skylarkWorkflow("toStringIncludesName", SQUASH).toString())
        .contains("toStringIncludesName");
  }

  @Test
  public void squashWorkflowTestRecordContextReference() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    workflow.run(workdir, /*sourceRef=*/"HEAD");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getRequestedRevision().contextReference()).isEqualTo("HEAD");
  }

  @Test
  public void squashReadsLatestAffectedChangeInRoot() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    workflow.run(workdir, /*sourceRef=*/"HEAD");
    origin.addSimpleChange(/*timestamp*/ 2);
    DummyRevision expected = origin.resolve("HEAD");
    origin.addChange(/*timestamp*/ 3, Paths.get("not important"), "message",
                     /*matchesGlob=*/false);

    options.setForce(false);
    workflow = skylarkWorkflow("default", SQUASH);
    workflow.run(workdir, /*sourceRef=*/"HEAD");
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getOriginRef().asString()).isEqualTo(expected.asString());
  }

  @Test
  public void iterativeWorkflowTestRecordContextReference() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow workflow = iterativeWorkflow("0");

    // First change is migrated without context reference. Then we run again with
    // context ("HEAD").
    workflow.run(workdir, /*sourceRef=*/ "1");
    workflow = iterativeWorkflow(/*previousRef=*/ null);
    workflow.run(workdir, /*sourceRef=*/ "HEAD");
    for (ProcessedChange change : destination.processed) {
      assertThat(change.getRequestedRevision().contextReference())
          .isEqualTo(change.getOriginRef().asString().equals("1") ? null : "HEAD");
    }
  }

  @Test
  public void changeRequestWorkflowTestRecordContextReference() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    Workflow workflow = changeRequestWorkflow(null);
    workflow.run(workdir, "HEAD");
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isEqualTo("42");
    assertThat(change.getRequestedRevision().contextReference()).isEqualTo("HEAD");
  }

  @Test
  public void iterativeWorkflowTest_defaultAuthoring() throws Exception {
    for (int timestamp = 0; timestamp < 61; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow workflow = iterativeWorkflow(/*previousRef=*/ "42");

    workflow.run(workdir, /*sourceRef=*/ "50");
    assertThat(destination.processed).hasSize(8);
    int nextChange = 43;
    for (ProcessedChange change : destination.processed) {
      assertThat(change.getChangesSummary()).isEqualTo(nextChange + " change");
      String asString = Integer.toString(nextChange);
      assertThat(change.getOriginRef().asString()).isEqualTo(asString);
      assertThat(change.numFiles()).isEqualTo(1);
      assertThat(change.getContent("file.txt")).isEqualTo(PREFIX + asString);
      assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
      nextChange++;
    }

    workflow = iterativeWorkflow(null);
    workflow.run(workdir, /*sourceRef=*/ "60");
    assertThat(destination.processed).hasSize(18);
  }

  @Test
  public void testIterativeModeWithLimit() throws Exception {
    for (int timestamp = 0; timestamp < 51; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    // First change so that iterative can find last imported revisions
    iterativeWorkflow(/*previousRef=*/"40").run(workdir, /*sourceRef=*/"41");
    options.workflowOptions.iterativeLimitChanges = 1;
    int numClsBefore = destination.processed.size();
    for (int i = 42; i <= 50; i++) {
      iterativeWorkflow(/*previousRef=*/null).run(workdir, /*sourceRef=*/"50");
      assertThat(destination.processed).hasSize(numClsBefore + 1);
      numClsBefore++;
      assertThat(Iterables.getLast(destination.processed).getChangesSummary())
          .isEqualTo(i + " change");
    }

    // Check that we don't import anything else after we have migrated all pending changes.
    thrown.expect(EmptyChangeException.class);
    iterativeWorkflow(/*previousRef=*/null).run(workdir, /*sourceRef=*/null);
  }

  @Test
  public void testIterativeModeProducesNoop() throws Exception {
    assertThat(checkIterativeModeWithError(new EmptyChangeException("This was an empty change!")))
        .hasMessage(
            "Iterative workflow produced no changes in the destination for resolved ref: 3");
    console().assertThat()
        .onceInLog(MessageType.WARNING,
            "Migration of origin revision '2' resulted in an empty change.*")
        .onceInLog(MessageType.WARNING,
            "Migration of origin revision '3' resulted in an empty change.*");

  }

  @Test
  public void testIterativeValidationException() throws Exception {
    assertThat(checkIterativeModeWithError(new ValidationException("Your change is wrong!")))
        .hasMessage("Your change is wrong!");
    console().assertThat()
        .onceInLog(MessageType.ERROR,
            "Migration of origin revision '2' failed with error: Your change is wrong.*");
  }

  @Test
  public void testIterativeRepoException() throws Exception {
    assertThat(checkIterativeModeWithError(new RepoException("Your change is wrong!")))
        .hasMessage("Your change is wrong!");
    console().assertThat()
        .onceInLog(MessageType.ERROR,
            "Migration of origin revision '2' failed with error: Your change is wrong.*");
  }

  @SuppressWarnings("unchecked")
  private <T extends Exception> T checkIterativeModeWithError(final T exception)
      throws IOException, ValidationException {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    // Override destination with one that always throws EmptyChangeException.
    options.testingOptions.destination = new RecordsProcessCallDestination() {
      @Override
      public Writer newWriter(Glob destinationFiles, boolean dryRun, @Nullable Writer oldWriter) {
        return new WriterImpl(destinationFiles, dryRun) {
          @Override
          public WriterResult write(TransformResult transformResult, Console console)
              throws ValidationException, RepoException, IOException {
            assert exception != null;
            Throwables.propagateIfPossible(exception, ValidationException.class,
                RepoException.class);
            throw new RuntimeException(exception);
          }
        };
      }
    };
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"1");

    try {
      workflow.run(workdir, /*sourceRef=*/"3");
      fail();
    } catch (Exception expected) {
      assertThat(expected).isInstanceOf(expected.getClass());
      return (T) expected;
    }
    return exception;
  }

  @Test
  public void iterativeWorkflowTest_whitelistAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    whiteListAuthoring();

    Workflow workflow = iterativeWorkflow("0");

    workflow.run(workdir, /*sourceRef=*/HEAD);
    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  private void whiteListAuthoring() {
    authoring = ""
        + "authoring.whitelisted(\n"
        + "   default = '" + DEFAULT_AUTHOR + "',\n"
        + "   whitelist = ['" + ORIGINAL_AUTHOR.getEmail() + "'],\n"
        + ")";
  }

  @Test
  public void iterativeWorkflowTest_passThruAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    passThruAuthoring();

    iterativeWorkflow("0").run(workdir, /*sourceRef=*/HEAD);

    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(NOT_WHITELISTED_ORIGINAL_AUTHOR);
  }

  private void passThruAuthoring() {
    authoring = "authoring.pass_thru('" + DEFAULT_AUTHOR + "')";
  }

  @Test
  public void iterativeWorkflowConfirmationHandlingTest() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }

    console()
        .respondYes()
        .respondNo();
    RecordsProcessCallDestination programmableDestination = new RecordsProcessCallDestination(
        WriterResult.OK, WriterResult.PROMPT_TO_CONTINUE, WriterResult.PROMPT_TO_CONTINUE);

    options.testingOptions.destination = programmableDestination;

    Workflow workflow = iterativeWorkflow(/*previousRef=*/"2");

    try {
      workflow.run(workdir, /*sourceRef=*/"9");
      fail("Should throw ChangeRejectedException");
    } catch (ChangeRejectedException expected) {
      assertThat(expected.getMessage())
          .contains("Iterative workflow aborted by user after: Change 3 of 7 (5)");
    }
    assertThat(programmableDestination.processed).hasSize(3);
  }

  @Test
  public void iterativeWorkflowNoPreviousRef() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    Workflow workflow = iterativeWorkflow(/*previousRef=*/null);
    thrown.expect(CannotResolveRevisionException.class);
    thrown.expectMessage("Previous revision label DummyOrigin-RevId could not be found");
    workflow.run(workdir, /*sourceRef=*/"0");
  }

  @Test
  public void iterativeWorkflowEmptyChanges() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"0");
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("No new changes to import for resolved ref: 0");
    workflow.run(workdir, /*sourceRef=*/"0");
  }

  @Test
  public void iterativeSkipCommits() throws Exception {
    origin.singleFileChange(0, "one", "file.txt", "a");
    origin.singleFileChange(1, "two", "file.txt", "b");
    origin.singleFileChange(2, "three", "file.txt", "b");
    origin.singleFileChange(3, "four", "file.txt", "c");
    transformations = ImmutableList.of();
    destination.failOnEmptyChange = true;
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"0");
    workflow.run(workdir, /*sourceRef=*/"3");
    assertThat(destination.processed.get(1).getContent("file.txt")).isEqualTo("c");
  }

  @Test
  public void iterativeOnlyRunForMatchingOriginFiles() throws Exception {
    origin.singleFileChange(0, "base", "file.txt", "a");
    origin.singleFileChange(1, "one", "file.txt", "b");
    origin.addChange(2, "two", ImmutableMap.of("excluded/two", "b",
                                               "file.txt", "b"));
    origin.addChange(3, "three", ImmutableMap.of("excluded/two", "b",
                                                 "file.txt", "b",
                                                 "copy.bara.sky", ""));
    origin.addChange(4, "four", ImmutableMap.of("excluded/two", "b",
                                                "file.txt", "c",
                                                "copy.bara.sky", ""));
    transformations = ImmutableList.of();
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"0");
    workflow.run(workdir, /*sourceRef=*/HEAD);
    for (ProcessedChange change : destination.processed) {
      System.err.println(change.getChangesSummary());
    }
    assertThat(destination.processed.get(0).getChangesSummary()).contains("one");
    assertThat(destination.processed.get(1).getChangesSummary()).contains("three");
  }

  @Test
  public void iterativeWithGroup() throws Exception {
    transformations = ImmutableList.of();
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2", "file.txt", "b");
    iterativeWorkflow(/*previousRef=*/"0").run(workdir, /*sourceRef=*/"1");
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(3, "pending2", "file.txt", "d");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(4, "other_pending", "file.txt", "f");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending2");
    origin.singleFileChange(5, "pending3", "file.txt", "e");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    iterativeWorkflow(/*previousRef=*/null).run(workdir, /*sourceRef=*/"3");

    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "2", "3"));

    iterativeWorkflow(/*previousRef=*/null).run(workdir, /*sourceRef=*/"5");

    // We migrate everything from pending1
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "2", "3", "2", "3", "5"));
  }

  @Test
  public void changeRequesthWithGroup() throws Exception {
    transformations = ImmutableList.of("metadata.squash_notes()");
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2\n\n" + destination.getLabelNameWhenOrigin() + ": 1\n",
                            "file.txt", "b");
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    skylarkWorkflow("default", CHANGE_REQUEST).run(workdir, /*sourceRef=*/"2");
    origin.singleFileChange(3, "base3\n\n" + destination.getLabelNameWhenOrigin() + ": 3\n",
                            "file.txt", "d");

    assertThat(destination.pending.asMap()).hasSize(1);
    assertThat(Iterables.getLast(destination.pending.values()).getBaseline()).isEqualTo("1");

    origin.singleFileChange(4, "pending2", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    skylarkWorkflow("default", CHANGE_REQUEST).run(workdir, /*sourceRef=*/"4");

    assertThat(destination.pending.asMap()).hasSize(1);
    assertThat(Iterables.getLast(destination.pending.values()).getBaseline()).isEqualTo("3");
  }

  @Test
  public void squashWithGroup() throws Exception {
    transformations = ImmutableList.of("metadata.squash_notes()");
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2", "file.txt", "b");
    options.workflowOptions.lastRevision = "0";
    skylarkWorkflow("default", SQUASH).run(workdir, /*sourceRef=*/"1");
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(3, "pending2", "file.txt", "d");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(4, "other_pending", "file.txt", "f");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending2");
    origin.singleFileChange(5, "pending3", "file.txt", "e");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    options.workflowOptions.lastRevision = null;
    skylarkWorkflow("default", SQUASH).run(workdir, /*sourceRef=*/"3");

    assertThat(destination.pending.asMap()).hasSize(1);
    assertThat(Iterables.getLast(destination.pending.values()).getChangesSummary()).isEqualTo(
        "Copybara import of the project:\n"
            + "\n"
            + "  - 3 pending2 by Copybara <no-reply@google.com>\n"
            + "  - 2 pending1 by Copybara <no-reply@google.com>\n");
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "3"));

    skylarkWorkflow("default", SQUASH).run(workdir, /*sourceRef=*/"5");

    assertThat(destination.pending.asMap()).hasSize(1);
    assertThat(Iterables.getLast(destination.pending.values()).getChangesSummary()).isEqualTo(
        "Copybara import of the project:\n"
            + "\n"
            + "  - 5 pending3 by Copybara <no-reply@google.com>\n"
            + "  - 3 pending2 by Copybara <no-reply@google.com>\n"
            + "  - 2 pending1 by Copybara <no-reply@google.com>\n");
    // We migrate everything from pending1
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "3", "5"));
  }

  @Test
  public void emptyTransformList() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    workflow.run(workdir, /*sourceRef=*/"0");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getContent("file.txt")).isEqualTo("0");
  }

  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    Workflow workflow = workflow();
    workflow.run(workdir, HEAD);

    ZonedDateTime timestamp = destination.processed.get(0).getTimestamp();
    assertThat(timestamp.toInstant().getEpochSecond()).isEqualTo(42);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    Workflow workflow = workflow();
    String head = resolveHead();
    workflow.run(workdir, HEAD);
    assertThat(Files.readAllLines(workdir.resolve("checkout/file.txt"), UTF_8))
        .contains(PREFIX + head);
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    Workflow workflow = workflow();
    origin.addSimpleChange(/*timestamp*/ 42918273);
    workflow.run(workdir, HEAD);
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getTimestamp().toInstant().getEpochSecond())
        .isEqualTo(42918273);
  }

  @Test
  public void usesDefaultAuthorForSquash() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    Workflow workflow = workflow();

    workflow.run(workdir, HEAD);
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).contains(DEFAULT_AUTHOR.toString());
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void migrationIdentityConstant() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    String before = workflow().getMigrationIdentity(origin.resolve(HEAD));
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    String after = workflow().getMigrationIdentity(origin.resolve(HEAD));

    // If we use 'HEAD' as reference it is constant
    assertThat(before).isEqualTo(after);

    String nonConstant = workflow().getMigrationIdentity(origin.resolve("3"));

    // But if we use direct reference (3 instead of 'HEAD') it changes since we cannot
    // find the context reference.
    assertThat(after).isNotEqualTo(nonConstant);
  }

  @Test
  public void testSquashAlreadyMigrated() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    String oldRef = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    options.setForce(true);
    skylarkWorkflow("default", SQUASH).run(workdir, HEAD);
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("'0' has been already migrated");
    options.setForce(false); // Disable force so that we get an error
    skylarkWorkflow("default", SQUASH).run(workdir, oldRef);
  }

  @Test
  public void testSquashAlreadyMigratedSameChange() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    skylarkWorkflow("default", SQUASH).run(workdir, HEAD);
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("'0' has been already migrated");
    options.setForce(false); // Disable force so that we get an error
    skylarkWorkflow("default", SQUASH).run(workdir, HEAD);
  }

  @Test
  public void testSquashLastRevDoesntExist() throws Exception {
    options.setForce(false); // Disable force so that we get an error
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = "42";

    Workflow workflow = workflow();

    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find last imported revision."
        + " Use --force if you really want to proceed with the migration");
    workflow.run(workdir, HEAD);
  }

  @Test
  public void testSquashAlreadyMigratedWithForce() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    String oldRef = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    options.setForce(true);
    workflow().run(workdir, HEAD);

    assertThat(destination.newWriter(Glob.ALL_FILES, /*dryRun=*/false, /*oldWriter=*/null)
        .getDestinationStatus(origin.getLabelName(), null).getBaseline())
        .isEqualTo("3");
    workflow().run(workdir, oldRef);
    assertThat(destination.newWriter(Glob.ALL_FILES, /*dryRun=*/false, /*oldWriter=*/null)
        .getDestinationStatus(origin.getLabelName(), null).getBaseline())
        .isEqualTo("0");
  }

  @Test
  public void runsTransformations() throws Exception {
    workflow().run(workdir, HEAD);
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).numFiles()).isEqualTo(1);
    assertThat(destination.processed.get(0).getContent("file.txt")).isEqualTo(PREFIX + "0");
  }

  @Test
  public void invalidExcludedOriginPath() throws Exception {
    prepareOriginExcludes("a");
    String outsideFolder = "../../file";
    Path file = workdir.resolve(outsideFolder);
    Files.createDirectories(file.getParent());
    Files.write(file, new byte[]{});

    originFiles = "glob(['" + outsideFolder + "'])";

    try {
      workflow().run(workdir, HEAD);
      fail("should have thrown");
    } catch (ValidationException e) {
      console().assertThat()
          .onceInLog(MessageType.ERROR,
              "(\n|.)*path has unexpected [.] or [.][.] components(\n|.)*");
    }
    assertThatPath(workdir)
        .containsFiles(outsideFolder);
  }

  @Test
  public void invalidExcludedOriginGlob() throws Exception {
    prepareOriginExcludes("a");
    originFiles = "glob(['{'])";

    try {
      workflow().run(workdir, HEAD);
      fail("should have thrown");
    } catch (ValidationException e) {
      console().assertThat()
          .onceInLog(MessageType.ERROR,
              "(\n|.)*Cannot create a glob from: include='\\[\\{\\]' (\n|.)*");
    }
  }

  @Test
  public void excludedOriginPathDoesntExcludeDirectories() throws Exception {
    // Ignore transforms that have no effect
    options.workflowOptions.ignoreNoop = true;

    originFiles = "glob(['**'], exclude = ['folder'])";
    Workflow workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, HEAD);
    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder/file.txt", "folder2/file.txt");
  }

  @Test
  public void excludedOriginPathRecursive() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, HEAD);

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2")
        .containsNoFiles(
            "folder/file.txt", "folder/subfolder/file.txt", "folder/subfolder/file.java");
  }

  @Test
  public void testNoopGroup() throws Exception {
    options.workflowOptions.ignoreNoop = false;
    transformations = ImmutableList.of(""
        + "        core.transform("
        + "            transformations = ["
        + "                core.replace("
        + "                    before = 'foo',"
        + "                    after = 'bar',"
        + "                )"
        + "            ],"
        + "            ignore_noop = True,"
        + "        )"
    );
    workflow().run(workdir, HEAD);
    console().assertThat().onceInLog(MessageType.WARNING,
        ".*Ignored noop because of 'ignore_noop' field.*");
  }

  @Test
  public void testNoopGroupDefault() throws Exception {
    options.workflowOptions.ignoreNoop = false;
    transformations = ImmutableList.of(""
        + "        core.transform("
        + "            transformations = ["
        + "                core.replace("
        + "                    before = 'foo',"
        + "                    after = 'bar',"
        + "                )"
        + "            ],"
        + "        )"
    );
    try {
      workflow().run(workdir, HEAD);
      fail();
    } catch (VoidOperationException ignored) {
    }
  }

  @Test
  public void excludedOriginRecursiveByType() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, HEAD);

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt")
        .containsNoFiles("folder/subfolder/file.java");
  }

  @Test
  public void skipNotesForExcludedFiles() throws Exception {
    originFiles = "glob(['**'], exclude = ['foo'])";
    transformations = ImmutableList.of("metadata.squash_notes()");
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path one = Files.createDirectories(fileSystem.getPath("one"));
    Path two = Files.createDirectories(fileSystem.getPath("two"));
    Path three = Files.createDirectories(fileSystem.getPath("three"));
    Path four = Files.createDirectories(fileSystem.getPath("four"));

    touchFile(one, "foo", "");
    touchFile(one, "bar", "");
    origin.addChange(1, one, "foo and bar", /*matchesGlob=*/true);

    touchFile(two, "foo", "a");
    touchFile(two, "bar", "");
    origin.addChange(2, two, "only foo", /*matchesGlob=*/true);

    touchFile(three, "foo", "a");
    touchFile(three, "bar", "a");
    origin.addChange(3, three, "only bar", /*matchesGlob=*/true);

    DummyRevision bar = origin.resolve(HEAD);

    touchFile(four, "foo", "b");
    touchFile(four, "bar", "a");
    origin.addChange(4, four, "foo again", /*matchesGlob=*/true);

    skylarkWorkflow("default", SQUASH).run(workdir, HEAD);

    // We skip changes that only touch foo.
    assertThat(Iterables.getLast(destination.processed).getChangesSummary())
        .isEqualTo("Copybara import of the project:\n"
            + "\n"
            + "  - 2 only bar by Copybara <no-reply@google.com>\n"
            + "  - 0 foo and bar by Copybara <no-reply@google.com>\n");
    // We use as reference for the destination label the last change that affects
    // origin_files.
    assertThat(Iterables.getLast(destination.processed).getOriginRef().asString())
        .isEqualTo(bar.asString());
  }

  @Test
  public void originFilesNothingRemovedNoopNothingInLog() throws Exception {
    originFiles = "glob(['**'], exclude = ['I_dont_exist'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, HEAD);
    console().assertThat()
        .timesInLog(0, MessageType.INFO, "Removed .* files from workdir");
  }

  @Test
  public void excludeOriginPathIterative() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    prepareOriginExcludes("a");
    Workflow workflow = iterativeWorkflow(resolveHead());
    prepareOriginExcludes("b");
    prepareOriginExcludes("c");
    prepareOriginExcludes("d");
    workflow.run(workdir, HEAD);
    for (ProcessedChange processedChange : destination.processed) {
      for (String path : ImmutableList.of("folder/file.txt",
          "folder2/file.txt",
          "folder2/subfolder/file.java",
          "folder/subfolder/file.txt")) {
        assertThat(processedChange.filePresent(path)).isTrue();
      }
      assertThat(processedChange.filePresent("folder/subfolder/file.java")).isFalse();
    }
  }

  @Test
  public void testOriginExcludesToString() throws Exception {
    originFiles = "glob(['**'], exclude = ['foo/**/bar.htm'])";
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testDestinationExcludesToString() throws Exception {
    destinationFiles = "glob(['**'], exclude = ['foo/**/bar.htm'])";
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testDestinationFilesPassedToDestination_iterative() throws Exception {
    destinationFiles = "glob(['**'], exclude = ['foo', 'bar/**'])";
    origin.addSimpleChange(/*timestamp*/ 42);
    Workflow workflow = iterativeWorkflow(resolveHead());
    origin.addSimpleChange(/*timestamp*/ 4242);
    workflow.run(workdir, HEAD);

    assertThat(destination.processed).hasSize(1);

    PathMatcher matcher = destination.processed.get(0).getDestinationFiles()
        .relativeTo(workdir);
    assertThat(matcher.matches(workdir.resolve("foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("foo/indir"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("bar/indir"))).isFalse();
  }

  private String resolveHead() throws RepoException, CannotResolveRevisionException {
    return origin.resolve(HEAD).asString();
  }

  @Test
  public void testDestinationFilesPassedToDestination_squash() throws Exception {
    destinationFiles = "glob(['**'], exclude = ['foo', 'bar/**'])";
    workflow().run(workdir, HEAD);

    assertThat(destination.processed).hasSize(1);

    PathMatcher matcher = destination.processed.get(0).getDestinationFiles()
        .relativeTo(workdir);
    assertThat(matcher.matches(workdir.resolve("foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("foo/indir"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("bar/indir"))).isFalse();
  }

  @Test
  public void invalidLastRevFlagGivesClearError() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 42);

    Workflow workflow = iterativeWorkflow("deadbeef");
    thrown.expect(CannotResolveRevisionException.class);
    thrown.expectMessage(
        "Could not resolve --last-rev flag. Please make sure it exists in the origin: deadbeef");
    workflow.run(workdir, HEAD);
  }

  @Test
  public void changeRequest_defaultAuthoring() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    Workflow workflow = changeRequestWorkflow(null);
    workflow.run(workdir, "1");
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isEqualTo("42");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequest_passThruAuthoring() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    passThruAuthoring();
    Workflow workflow = changeRequestWorkflow(null);
    workflow.run(workdir, "1");

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
  }

  @Test
  public void changeRequest_whitelistAuthoring() throws Exception {
    origin
        .setAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    whiteListAuthoring();

    changeRequestWorkflow(null).run(workdir, "1");

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void changeRequestManualBaseline() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");
    Workflow workflow = changeRequestWorkflow("24");
    workflow.run(workdir, "1");
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("24");
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequest_findParentBaseline() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Last Change\n" + destination.getLabelNameWhenOrigin() + "=BADBAD");
    Workflow workflow = changeRequestWorkflow(null);
    workflow.run(workdir, "1");
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
  }

  @Test
  public void testNullAuthoring() throws Exception {
    try {
      loadConfig(""
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    origin = testing.origin(),\n"
          + "    destination = testing.destination(),\n"
          + ")\n");
      fail();
    } catch (ValidationException e) {
      console().assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'authoring'.*");
    }
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes()), "copy.bara.sky"),
        options.build());
  }

  @Test
  public void testNullOrigin() throws Exception {
    try {
      loadConfig(""
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    authoring = " + authoring + "\n,"
          + "    destination = testing.destination(),\n"
          + ")\n");
      fail();
    } catch (ValidationException e) {
      for (Message message : console().getMessages()) {
        System.err.println(message);
      }
      console().assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'origin'.*");
    }
  }

  @Test
  public void testMessageTransformerForSquash() throws Exception {
    runWorkflowForMessageTransform(SQUASH, /*thirdTransform=*/null);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: third commit (2) by Foo Baz\n"
            + "CHANGE: second commit (1) by Foo Bar\n"
            + "\n"
            + "BAR = foo\n");
    assertThat(change.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
  }

  @Test
  public void testMessageTransformerForIterative() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.ITERATIVE, /*thirdTransform=*/null);
    ProcessedChange secondCommit = destination.processed.get(0);
    assertThat(secondCommit.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: second commit (1) by Foo Bar\n"
            + "\n"
            + "BAR = foo\n");
    assertThat(secondCommit.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
    ProcessedChange thirdCommit = destination.processed.get(1);
    assertThat(thirdCommit.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: third commit (2) by Foo Baz\n"
            + "\n"
            + "BAR = foo\n");
    assertThat(thirdCommit.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
  }

  @Test
  public void testMessageTransformerForIterativeWithMigrated() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.ITERATIVE, ""
        + "def third(ctx):\n"
        + "  msg = ''\n"
        + "  for c in ctx.changes.migrated:\n"
        + "    msg+='PREV: %s (%s) by %s\\n' %  (c.message, c.ref, c.author.name)\n"
        + "  ctx.set_message(ctx.message + '\\nPREVIOUS CHANGES:\\n' + msg)\n");
    ProcessedChange secondCommit = destination.processed.get(0);
    assertThat(secondCommit.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: second commit (1) by Foo Bar\n"
            + "\n"
            + "BAR = foo\n"
            + "\n"
            + "PREVIOUS CHANGES:\n");
    assertThat(secondCommit.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
    ProcessedChange thirdCommit = destination.processed.get(1);
    assertThat(thirdCommit.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: third commit (2) by Foo Baz\n"
            + "\n"
            + "BAR = foo\n"
            + "\n"
            + "PREVIOUS CHANGES:\n"
            + "PREV: second commit (1) by Foo Bar\n");
    assertThat(thirdCommit.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
  }

  @Test
  public void testMessageTransformerForChangeRequest() throws Exception {
    options.workflowOptions.changeBaseline = "1";
    runWorkflowForMessageTransform(WorkflowMode.CHANGE_REQUEST, /*thirdTransform=*/null);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: third commit (2) by Foo Baz\n"
            + "\n"
            + "BAR = foo\n");
    assertThat(change.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
  }

  private void runWorkflowForMessageTransform(WorkflowMode mode, @Nullable String thirdTransform)
      throws IOException, RepoException, ValidationException {
    origin.addSimpleChange(0, "first commit")
        .setAuthor(new Author("Foo Bar", "foo@bar.com"))
        .addSimpleChange(1, "second commit")
        .setAuthor(new Author("Foo Baz", "foo@baz.com"))
        .addSimpleChange(2, "third commit");

    options.workflowOptions.lastRevision = "0";
    passThruAuthoring();

    Config config = loadConfig(""
        + "def first(ctx):\n"
        + "  msg =''\n"
        + "  for c in ctx.changes.current:\n"
        + "    msg+='CHANGE: %s (%s) by %s\\n' %  (c.message, c.ref, c.author.name)\n"
        + "  ctx.set_message(msg)\n"
        + "def second(ctx):\n"
        + "  ctx.set_message(ctx.message +'\\nBAR = foo\\n')\n"
        + "  ctx.set_author(new_author('Someone <someone@somewhere.com>'))\n"
        + "\n"
        + (thirdTransform == null ? "" : thirdTransform)
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin =  testing.origin(),\n"
        + "    authoring = " + authoring + "\n,"
        + "    destination = testing.destination(),\n"
        + "    mode = '" + mode + "',\n"
        + "    transformations = [\n"
        + "      first, second" + (thirdTransform == null ? "" : ", third") + "]\n"
        + ")\n");
    config.getMigration("default").run(workdir, "2");
  }

  @Test
  public void testNullDestination() throws Exception {
    try {
      loadConfig(""
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    authoring = " + authoring + "\n,"
          + "    origin = testing.origin(),\n"
          + ")\n");
      fail();
    } catch (ValidationException e) {
      console().assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'destination'.*");
    }
  }

  @Test
  public void testNoNestedSequenceProgressMessage() throws Exception {
    Transformation transformation = ((Workflow<?, ?>) loadConfig(""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    authoring = " + authoring + "\n,"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = ["
        + "        core.transform("
        + "             ["
        + "                 core.transform("
        + "                     ["
        + "                         core.move('foo', 'bar'),"
        + "                         core.move('bar', 'foo')"
        + "                     ],"
        + "                     reversal = [],"
        + "                 )"
        + "             ],"
        + "             reversal = []"
        + "        )\n"
        + "    ],"
        + ")\n").getMigration("default")).getTransformation();

    Files.write(workdir.resolve("foo"), new byte[0]);
    transformation.transform(TransformWorks.of(workdir, "message", console()));

    // Check that we don't nest sequence progress messages
    console().assertThat().onceInLog(MessageType.PROGRESS, "^\\[ 1/2\\] Transform Moving foo");
    console().assertThat().onceInLog(MessageType.PROGRESS, "^\\[ 2/2\\] Transform Moving bar");
  }

  @Test
  public void nonReversibleButCheckReverseSet() throws Exception {
    origin
        .singleFileChange(0, "one commit", "foo.txt", "1")
        .singleFileChange(1, "one commit", "test.txt", "1\nTRANSFORMED42");
    Workflow workflow = changeRequestWorkflow("0");
    try {
      workflow.run(workdir, "1");
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessage("Workflow 'default' is not reversible");
    }
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, "Checking that the transformations can be reverted");
  }

  @Test
  public void testReverseMetadata() throws IOException, ValidationException, RepoException {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def forward(ctx):\n"
        + "  ctx.set_message('modified message')\n"
        + "def reverse(ctx):\n"
        + "  if ctx.message != 'modified message':\n"
        + "    ctx.console.error('Expecting \"modified message\": '+ ctx.message)\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [core.transform([forward], reversal=[reverse])],\n"
        + "    authoring = " + authoring + ",\n"
        + "    reversible_check = True,\n"
        + ")\n";
    loadConfig(config).getMigration("default").run(workdir, /*sourceRef=*/null);
  }

  @Test
  public void errorWritingFileThatDoesNotMatchDestinationFiles() throws Exception {
    destinationFiles = "glob(['foo*'], exclude = ['foo42'])";
    transformations = ImmutableList.of();

    origin.singleFileChange(/*timestamp=*/44, "one commit", "bar.txt", "1");
    Workflow workflow = skylarkWorkflow("default", SQUASH);

    thrown.expect(NotADestinationFileException.class);
    thrown.expectMessage("[bar.txt]");
    workflow.run(workdir, HEAD);
  }

  @Test
  public void errorWritingMultipleFilesThatDoNotMatchDestinationFiles() throws Exception {
    // 'foo42' is like a file that is expected to be in the destination but not
    // originating from the origin repo (e.g. a metadata file specific to one repo type).
    // In this example, though, the file is copied from the origin, hence an error.
    destinationFiles = "glob(['foo*'], exclude = ['foo42'])";
    transformations = ImmutableList.of();

    Path originDir = Files.createTempDirectory("change1");
    Files.write(originDir.resolve("bar"), new byte[] {});
    Files.write(originDir.resolve("foo_included"), new byte[] {});
    Files.write(originDir.resolve("foo42"), new byte[] {});
    origin.addChange(/*timestamp=*/42, originDir, "change1", /*matchesGlob=*/true);
    Workflow workflow = skylarkWorkflow("default", SQUASH);

    thrown.expect(NotADestinationFileException.class);
    thrown.expectMessage("[bar, foo42]");
    workflow.run(workdir, HEAD);
  }

  @Test
  public void checkForNonMatchingDestinationFilesAfterTransformations() throws Exception {
    destinationFiles = "glob(['foo*'])";
    options.workflowOptions.ignoreNoop = true;
    transformations = ImmutableList.of("core.move('bar.txt', 'foo53')");

    origin.singleFileChange(/*timestamp=*/44, "commit 1", "bar.txt", "1");
    origin.singleFileChange(/*timestamp=*/45, "commit 2", "foo42", "1");
    Workflow workflow = skylarkWorkflow("default", SQUASH);

    workflow.run(workdir, HEAD);
  }

  @Test
  public void changeRequestWithFolderDestinationError()
      throws IOException, ValidationException, RepoException {
    origin.singleFileChange(/*timestamp=*/44, "commit 1", "bar.txt", "1");

    thrown.expect(ValidationException.class);
    thrown.expectMessage("'CHANGE_REQUEST' is incompatible with destinations that don't support"
        + " history");

    loadConfig("core.workflow(\n"
        + "    name = 'foo',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = folder.destination(),\n"
        + "    authoring = " + authoring + ",\n"
        + "    mode = 'CHANGE_REQUEST',\n"
        + ")\n")
        .getMigration("foo")
        .run(workdir, null);
  }

  @Test
  public void checkLastRevStatus_squash() throws Exception {
    checkLastRevStatus(WorkflowMode.SQUASH);
  }

  @Test
  public void checkLastRevStatus_iterative() throws Exception {
    checkLastRevStatus(WorkflowMode.ITERATIVE);
  }

  @Test
  public void checkLastRevStatus_change_request() throws Exception {
    try {
      checkLastRevStatus(WorkflowMode.CHANGE_REQUEST);
      fail();
    } catch (ValidationException e) {
      console().assertThat().onceInLog(MessageType.ERROR,
          ".*check_last_rev_state is not compatible with CHANGE_REQUEST.*");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void givenLastRevFlagInfoCommandUsesIt()
          throws IOException, RepoException, ValidationException {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository origin = initScratchRepo( /*verbose=*/true, originPath, getGitEnv());
    GitRepository destination = initScratchRepo( /*verbose=*/true, destinationPath, getGitEnv());

    String config = "core.workflow("
            + "    name = '" + "default" + "',"
            + "    origin = git.origin( url = 'file://"
            + origin.getWorkTree() + "', ref = 'master' ),\n"
            + "    destination = git.destination( url = 'file://"
            + destination.getWorkTree() + "'),"
            + "    authoring = " + authoring + ","
            + "    mode = '" + WorkflowMode.ITERATIVE + "',"
            + ")\n";

    Files.write(originPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "not important");
    String firstCommit = origin.parseRef("HEAD");

    Files.write(destinationPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    destination.add().files("foo.txt").run();
    destination.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "not important");

    Files.write(originPath.resolve("foo.txt"), "foo".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "change1");

    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.checkLastRevState = true;
    options.setLastRevision(firstCommit);

    final Info<Revision> info = (Info<Revision>) loadConfig(config).getMigration("default")
      .getInfo();
    final List<String> commitMessages =
            StreamSupport.stream(info.migrationReferences().spliterator(), false)
                    .flatMap(revisionMigrationReference ->
                               revisionMigrationReference.getAvailableToMigrate().stream())
                    .map(Change::getMessage)
                    .collect(Collectors.toList());
    assertThat(commitMessages).containsExactly("change1\n");
  }

  private void checkLastRevStatus(WorkflowMode mode)
      throws IOException, RepoException, ValidationException {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationWorkdir = Files.createTempDirectory("destination_workdir");
    GitRepository origin = initScratchRepo( /*verbose=*/true, originPath, getGitEnv());
    GitRepository destinationBare = bareRepo(Files.createTempDirectory("destination"), getGitEnv(),
                                             /*verbose=*/true);
    destinationBare.initGitDir();
    GitRepository destination = destinationBare.withWorkTree(destinationWorkdir);

    String config = "core.workflow("
        + "    name = '" + "default" + "',"
        + "    origin = git.origin( url = 'file://" + origin.getWorkTree() + "'),\n"
        + "    destination = git.destination( url = 'file://" + destinationBare.getGitDir() + "'),"
        + "    authoring = " + authoring + ","
        + "    mode = '" + mode + "',"
        + ")\n";

    Files.write(originPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "not important");
    String firstCommit = origin.parseRef("HEAD");

    Files.write(originPath.resolve("foo.txt"), "foo".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "change1");

    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.checkLastRevState = true;
    options.setLastRevision(firstCommit);

    loadConfig(config).getMigration("default").run(workdir, /*sourceRef=*/"HEAD");

    // Modify destination last commit
    Files.write(destinationWorkdir.resolve("foo.txt"), "foo_changed".getBytes(UTF_8));
    destination.add().files("foo.txt").run();
    destination.simpleCommand("commit", "--amend", "-a", "-C", "HEAD");

    Files.write(originPath.resolve("foo.txt"), "foo_origin_changed".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(), "change2");

    options.setForce(false);
    options.setLastRevision(null);
    thrown.expect(ValidationException.class);
    thrown.expectMessage("didn't result in an empty change. This means that the result change of"
        + " that migration was modified ouside of Copybara");
    loadConfig(config).getMigration("default").run(workdir, /*sourceRef=*/"HEAD");
  }

  private void prepareOriginExcludes(String content) throws IOException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("excludesTest");
    Path folder = workdir.resolve("folder");
    Files.createDirectories(folder);
    touchFile(base, "folder/file.txt", content);
    touchFile(base, "folder/subfolder/file.txt", content);
    touchFile(base, "folder/subfolder/file.java", content);
    touchFile(base, "folder2/file.txt", content);
    touchFile(base, "folder2/subfolder/file.txt", content);
    touchFile(base, "folder2/subfolder/file.java", content);
    origin.addChange(1, base, "excludes", /*matchesGlob=*/true);
  }

  private Path touchFile(Path base, String path, String content) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), content.getBytes(UTF_8));
  }
}
