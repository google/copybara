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
import static com.google.copybara.WorkflowMode.SQUASH;
import static com.google.copybara.testing.DummyOrigin.HEAD;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static org.junit.Assert.fail;

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
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.transform.metadata.MetadataModule;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
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
  private String excludedInOrigin;
  private String excludedInDestination;
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
    excludedInOrigin = "None";
    excludedInDestination = "None";
    originFiles = "None";
    destinationFiles = "None";
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
    skylark = new SkylarkParser(ImmutableSet.of(TestingModule.class, MetadataModule.class));
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
        + "    exclude_in_origin = " + excludedInOrigin + ",\n"
        + "    exclude_in_destination = " + excludedInDestination + ",\n"
        + "    origin_files = " + originFiles + ",\n"
        + "    destination_files = " + destinationFiles + ",\n"
        + "    transformations = " + transformations + ",\n"
        + "    authoring = " + authoring + ",\n"
        + "    mode = '" + mode + "',\n"
        + ")\n";
    System.err.println(config);
    return (Workflow<?, ?>)loadConfig(config).getMigration(name);
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
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    // Override destination with one that always throws EmptyChangeException.
    options.testingOptions.destination = new RecordsProcessCallDestination() {
      @Override
      public Writer newWriter(Glob destinationFiles) {
        return new RecordsProcessCallDestination.WriterImpl(destinationFiles) {
          @Override
          public WriterResult write(TransformResult transformResult, Console console)
              throws ValidationException, RepoException, IOException {
            throw new EmptyChangeException("This was an empty change!");
          }
        };
      }
    };
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"1");

    try {
      workflow.run(workdir, /*sourceRef=*/"3");
      fail();
    } catch (EmptyChangeException expected) {
      assertThat(expected).hasMessage(
          "Iterative workflow produced no changes in the destination for resolved ref: 3");
    }
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

    Instant timestamp = destination.processed.get(0).getTimestamp();
    assertThat(timestamp.getEpochSecond()).isEqualTo(42);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    Workflow workflow = workflow();
    String head = resolveHead();
    workflow.run(workdir, HEAD);
    assertThat(Files.readAllLines(workdir.resolve("checkout/file.txt"), StandardCharsets.UTF_8))
        .contains(PREFIX + head);
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    Workflow workflow = workflow();
    origin.addSimpleChange(/*timestamp*/ 42918273);
    workflow.run(workdir, HEAD);
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getTimestamp().getEpochSecond())
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

    assertThat(destination.newWriter(Glob.ALL_FILES).getPreviousRef(origin.getLabelName()))
        .isEqualTo("3");
    workflow().run(workdir, oldRef);
    assertThat(destination.newWriter(Glob.ALL_FILES).getPreviousRef(origin.getLabelName()))
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
    prepareOriginExcludes();
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
    prepareOriginExcludes();
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
    prepareOriginExcludes();
    workflow.run(workdir, HEAD);
    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder/file.txt", "folder2/file.txt");
  }

  @Test
  public void excludedOriginPathRecursive() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, HEAD);

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2")
        .containsNoFiles(
            "folder/file.txt", "folder/subfolder/file.txt", "folder/subfolder/file.java");
  }

  @Test
  public void excludedOriginRecursiveByType() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, HEAD);

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt")
        .containsNoFiles("folder/subfolder/file.java");
  }

  @Test
  public void originFilesNothingRemovedNoopNothingInLog() throws Exception {
    originFiles = "glob(['**'], exclude = ['I_dont_exist'])";
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, HEAD);
    console().assertThat()
        .timesInLog(0, MessageType.INFO, "Removed .* files from workdir");
  }

  @Test
  public void excludeOriginPathIterative() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    prepareOriginExcludes();
    Workflow workflow = iterativeWorkflow(resolveHead());
    prepareOriginExcludes();
    prepareOriginExcludes();
    prepareOriginExcludes();
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
  public void testExcludedDestinationPathsPassedToDestination_iterative() throws Exception {
    excludedInDestination = "glob(['foo', 'bar/**'])";
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

  @Test
  public void testExcludedDestinationPathsPassedToDestination_squash() throws Exception {
    excludedInDestination = "glob(['foo', 'bar/**'])";
    workflow().run(workdir, HEAD);

    assertThat(destination.processed).hasSize(1);

    PathMatcher matcher = destination.processed.get(0).getDestinationFiles()
        .relativeTo(workdir);
    assertThat(matcher.matches(workdir.resolve("foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("foo/indir"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("bar/indir"))).isFalse();

    console().assertThat()
        .onceInLog(MessageType.WARNING, ".*exclude_in_destination.*arg is deprecated.*");
  }

  @Test
  public void testExcludedOriginPaths_showDeprecationWarning() throws Exception {
    excludedInOrigin = "glob(['foo', 'bar/**'])";
    options.workflowOptions.ignoreNoop = true;
    workflow().run(workdir, HEAD);

    assertThat(destination.processed).hasSize(1);

    console().assertThat()
        .onceInLog(MessageType.WARNING, ".*exclude_in_origin.*arg is deprecated.*");
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

    changeRequestWorkflow(null).run(workdir, "0");

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
    System.out.println(secondCommit.getChangesSummary());
    assertThat(secondCommit.getChangesSummary())
        .isEqualTo(""
            + "CHANGE: second commit (1) by Foo Bar\n"
            + "\n"
            + "BAR = foo\n"
            + "\n"
            + "PREVIOUS CHANGES:\n");
    assertThat(secondCommit.getAuthor().toString()).isEqualTo("Someone <someone@somewhere.com>");
    ProcessedChange thirdCommit = destination.processed.get(1);
    System.out.println(thirdCommit.getChangesSummary());
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
    Transformation transformation = ((Workflow<?, ?>)loadConfig(""
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

  private void prepareOriginExcludes() throws IOException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base = fileSystem.getPath("excludesTest");
    Path folder = workdir.resolve("folder");
    Files.createDirectories(folder);
    touchFile(base, "folder/file.txt");
    touchFile(base, "folder/subfolder/file.txt");
    touchFile(base, "folder/subfolder/file.java");
    touchFile(base, "folder2/file.txt");
    touchFile(base, "folder2/subfolder/file.txt");
    touchFile(base, "folder2/subfolder/file.java");
    origin.addChange(1, base, "excludes", /*matchesGlob=*/true);
  }

  private Path touchFile(Path base, String path) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), new byte[]{});
  }
}
