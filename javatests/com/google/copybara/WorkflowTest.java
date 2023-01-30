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
import static com.google.copybara.TransformWork.COPYBARA_CONTEXT_REFERENCE_LABEL;
import static com.google.copybara.WorkflowMode.CHANGE_REQUEST;
import static com.google.copybara.WorkflowMode.ITERATIVE;
import static com.google.copybara.WorkflowMode.SQUASH;
import static com.google.copybara.git.GitRepository.GIT_DESCRIBE_ABBREV;
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.DummyOrigin.HEAD;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static com.google.copybara.util.DiffUtil.DiffFile.Operation.ADD;
import static com.google.copybara.util.DiffUtil.DiffFile.Operation.DELETE;
import static com.google.copybara.util.DiffUtil.DiffFile.Operation.MODIFIED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Info.MigrationReference;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.config.Config;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.Migration;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.Type;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.ChangeRejectedException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.NotADestinationFileException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.exception.VoidOperationException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRevision;
import com.google.copybara.hg.HgRepository;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.revision.Change;
import com.google.copybara.revision.Revision;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WorkflowTest {

  private static final String PREFIX = "TRANSFORMED";
  private static final Author ORIGINAL_AUTHOR =
      new Author("Foo Bar", "foo@bar.com");
  private static final Author NOT_ALLOWED_ORIGINAL_AUTHOR =
      new Author("Secret Coder", "secret@coder.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");
  private static final String FORCED_MESSAGE = "Test forced message";
  private static final Author FORCED_AUTHOR =
      new Author("Forced Author", "<forcedauthor@google.com>");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;

  private SkylarkTestExecutor skylark;

  private ImmutableList<String> transformations;
  private Path workdir;
  private boolean includeReleaseNotes;
  private String originFiles;
  private String destinationFiles;
  private TestingEventMonitor eventMonitor;
  private TransformWork transformWork;
  private boolean setRevId;
  private boolean smartPrune;
  private boolean mergeImport;
  private String autoGeneratePatchPrefix;
  private String autoPatchFileDirectoryPrefix;
  private String autoPatchfileContentsPrefix;
  private String autoPatchfileDirectory;
  private String autoPatchfileSuffix;
  private boolean autoPatchfileStripFilenamesAndLineNumbers;
  private boolean migrateNoopChangesField;
  private ImmutableList<String> extraWorkflowFields = ImmutableList.of();

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    options.setOutputRootToTmpDir();
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
    TestingConsole console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    options.setForce(true); // Force by default unless we are testing the flag.
    skylark = new SkylarkTestExecutor(options);
    eventMonitor = new TestingEventMonitor();
    options.general.enableEventMonitor("test", eventMonitor);
    transformWork = TransformWorks.of(workdir, "example", console);
    setRevId = true;
    smartPrune = false;
    mergeImport = false;
    autoPatchFileDirectoryPrefix = "";
    autoPatchfileContentsPrefix = "";
    autoPatchfileSuffix = "";
    autoPatchfileDirectory = "";
    autoPatchfileStripFilenamesAndLineNumbers = false;
    migrateNoopChangesField = false;
    extraWorkflowFields = ImmutableList.of();
  }

  private TestingConsole console() {
    return (TestingConsole) options.general.console();
  }

  private Workflow<?, ?> workflow() throws ValidationException, IOException {
    origin.addSimpleChange(/*timestamp*/ 42);
    return skylarkWorkflow("default", SQUASH);
  }

  private Workflow<?, ?> skylarkWorkflowInDirectory(String name, WorkflowMode mode, String dir)
      throws IOException, ValidationException {
    String config = getConfigString(name, mode);
    System.err.println(config);
    return (Workflow<?, ?>) loadConfigInDirectory(config, dir).getMigration(name);
  }

  private Workflow<?, ?> skylarkWorkflow(String name, WorkflowMode mode)
      throws IOException, ValidationException {
    String config = getConfigString(name, mode);
    System.err.println(config);
    return (Workflow<?, ?>) loadConfig(config).getMigration(name);
  }

  private String getConfigString(String name, WorkflowMode mode) {
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
        + "    set_rev_id = " + (setRevId ? "True" : "False") + ",\n"
        + "    smart_prune = " + (smartPrune ? "True" : "False") + ",\n"
        + "    merge_import = " + (mergeImport ? "True" : "False") + ",\n"
        + "::autopatch_placeholder::"
        + "    migrate_noop_changes = " + (migrateNoopChangesField ? "True" : "False") + ",\n"
        + "    mode = '" + mode + "',\n"
        + (extraWorkflowFields.isEmpty()
        ? ""
        : "    " + Joiner.on(",\n    ").join(extraWorkflowFields) + ",\n"
    )
        + ")\n";
    if (!Strings.isNullOrEmpty(autoPatchfileContentsPrefix)) {
      config =
          config.replace(
              "::autopatch_placeholder::",
              "    autopatch_config = core.autopatch_config(\n"
                  + "        header = "
                  + autoPatchfileContentsPrefix
                  + ",\n"
                  + "        suffix = "
                  + autoPatchfileSuffix
                  + ",\n"
                  + "        directory_prefix = "
                  + autoPatchFileDirectoryPrefix
                  + ",\n"
                  + "        directory = "
                  + autoPatchfileDirectory
                  + ",\n"
                  + "        strip_file_names_and_line_numbers = "
                  + (autoPatchfileStripFilenamesAndLineNumbers ? "True" : "False")
                  + "\n"
                  + "    ),\n");
    } else {
      config = config.replace("::autopatch_placeholder::", "");
    }
    return config;
  }

  private Workflow<?, ?> iterativeWorkflow(String workflowName, @Nullable String previousRef)
      throws ValidationException, IOException {
    options.workflowOptions.lastRevision = previousRef;
    options.general.enableEventMonitor("test", eventMonitor);
    options.general.setConsoleForTest(console());
    return skylarkWorkflow(workflowName, WorkflowMode.ITERATIVE);
  }

  private Workflow<?, ?> iterativeWorkflow(@Nullable String previousRef)
      throws ValidationException, IOException {
    return iterativeWorkflow("default", previousRef);
  }

  private Workflow<?, ?> changeRequestWorkflow(@Nullable String baseline)
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
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getRequestedRevision().contextReference()).isEqualTo("HEAD");
  }

  @Test
  public void squashWorkflowPublishesEvents() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of("HEAD"));
    assertThat(eventMonitor.changeMigrationStartedEventCount()).isEqualTo(1);
    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);
  }

  @Test
  public void contextReferenceAsLabel() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of(
        "metadata.add_header('Import of ${" + COPYBARA_CONTEXT_REFERENCE_LABEL + "}\\n')",
        "metadata.expose_label('" + COPYBARA_CONTEXT_REFERENCE_LABEL + "')"
    );
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).isEqualTo(""
        + "Import of HEAD\n"
        + "\n"
        + "Project import generated by Copybara.\n"
        + "\n"
        + "COPYBARA_CONTEXT_REFERENCE=HEAD\n");
  }

  @Test
  public void squashReadsLatestAffectedChangeInRoot() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of("HEAD"));
    origin.addSimpleChange(/*timestamp*/ 2);
    DummyRevision expected = origin.resolve("HEAD");
    origin.addChange(/*timestamp*/ 3, Paths.get("not important"), "message",
        /*matchesGlob=*/false);

    options.setForce(false);
    workflow = skylarkWorkflow("default", SQUASH);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getOriginRef().asString()).isEqualTo(expected.asString());
  }

  @Test
  public void testDisableCheckout() throws Exception {
    transformations = ImmutableList.of();
    extraWorkflowFields = ImmutableList.of("checkout = False");
    Workflow<?, ?> workflow = workflow();
    DummyRevision head = origin.resolve("HEAD");
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = Iterables.getLast(destination.processed);
    assertThat(change.getOriginRef()).isEqualTo(head);
    assertThatPath(workdir).containsNoMoreFiles();
  }

  @Test
  public void testEmptyDescriptionForFolderDestination() throws Exception {
    origin.singleFileChange(/*timestamp=*/44, "commit 1", "bar.txt", "1");
    options
        .setWorkdirToRealTempDir()
        .setHomeDir(StandardSystemProperty.USER_HOME.value());
    new SkylarkTestExecutor(options).loadConfig("core.workflow(\n"
        + "    name = 'foo',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = folder.destination(),\n"
        + "    authoring = " + authoring + ",\n"
        + "    transformations = [metadata.replace_message(''),],\n"
        + ")\n")
        .getMigration("foo")
        .run(workdir, ImmutableList.of());
  }

  @Test
  public void testTestWorkflowWithDiffInOrigin() throws Exception {
    GitRepository remote = GitRepository.newBareRepo(
        Files.createTempDirectory("gitdir"), getGitEnv(), /*verbose=*/true,
        DEFAULT_TIMEOUT, /*noVerify=*/ false).withWorkTree(workdir);
    remote.init();
    String primaryBranch = remote.getPrimaryBranch();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    remote.add().files("foo.txt").run();
    remote.simpleCommand("commit", "foo.txt", "-m", "message_a");
    GitRevision lastRev = remote.resolveReference(primaryBranch);

    Files.write(workdir.resolve("bar.txt"), "change content".getBytes(UTF_8));
    remote.add().files("bar.txt").run();
    remote.simpleCommand("commit", "bar.txt", "-m", "message_s");

    TestingConsole testingConsole = new TestingConsole().respondYes();
    options.workflowOptions.lastRevision = lastRev.getSha1();
    options
        .setWorkdirToRealTempDir()
        .setConsole(testingConsole)
        .setHomeDir(StandardSystemProperty.USER_HOME.value());

    Workflow<?, ?> workflow =
        (Workflow<?, ?>) new SkylarkTestExecutor(options).loadConfig(
            "core.workflow(\n"
                + "    name = 'foo',\n"
                + "    origin = git.origin(url='" + remote.getGitDir() + "',\n"
                + "                        ref = '" + primaryBranch + "'\n"
                + "    ),\n"
                + "    destination = folder.destination(),\n"
                + "    mode = 'ITERATIVE',\n"
                + "    authoring = " + authoring + ",\n"
                + "    transformations = [metadata.replace_message(''),],\n"
                + ")\n")
            .getMigration("foo");
    workflow.getWorkflowOptions().diffInOrigin = true;
    workflow.run(workdir, ImmutableList.of(primaryBranch));
    testingConsole.assertThat()
        .onceInLog(MessageType.WARNING, "Change 1 of 1 \\(.*\\)\\: Continue to migrate with '"
            + workflow.getMode() + "'" + " to " + workflow.getDestination().getType() + "\\?");
  }

  @Test
  public void testTestWorkflowWithDiffInOriginAndRespondNo() throws Exception {
    GitRepository remote = GitRepository.newBareRepo(
        Files.createTempDirectory("gitdir"), getGitEnv(), /*verbose=*/true,
        DEFAULT_TIMEOUT, /*noVerify=*/ false).withWorkTree(workdir);
    remote.init();
    String primaryBranch = remote.getPrimaryBranch();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    remote.add().files("foo.txt").run();
    remote.simpleCommand("commit", "foo.txt", "-m", "message_a");
    GitRevision lastRev = remote.resolveReference(primaryBranch);

    Files.write(workdir.resolve("bar.txt"), "change content".getBytes(UTF_8));
    remote.add().files("bar.txt").run();
    remote.simpleCommand("commit", "bar.txt", "-m", "message_s");

    TestingConsole testingConsole = new TestingConsole().respondNo();
    options.workflowOptions.lastRevision = lastRev.getSha1();
    options
        .setWorkdirToRealTempDir()
        .setConsole(testingConsole)
        .setHomeDir(StandardSystemProperty.USER_HOME.value());

    Workflow<?, ?> workflow =
        (Workflow<?, ?>) new SkylarkTestExecutor(options).loadConfig(
            "core.workflow(\n"
                + "    name = 'foo',\n"
                + "    origin = git.origin(url='" + remote.getGitDir() + "'),\n"
                + "    destination = folder.destination(),\n"
                + "    mode = 'ITERATIVE',\n"
                + "    authoring = " + authoring + ",\n"
                + "    transformations = [metadata.replace_message(''),],\n"
                + ")\n")
            .getMigration("foo");
    workflow.getWorkflowOptions().diffInOrigin = true;
    ChangeRejectedException e =
        assertThrows(ChangeRejectedException.class,
            () -> workflow.run(workdir, ImmutableList.of(primaryBranch)));
    assertThat(e.getMessage())
        .contains("User aborted execution: did not confirm diff in origin changes.");
  }

  @Test
  public void iterativeWorkflowTestRecordContextReference() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow<?, ?> workflow = iterativeWorkflow("0");

    // First change is migrated without context reference. Then we run again with
    // context ("HEAD").
    workflow.run(workdir, ImmutableList.of("1"));
    workflow = iterativeWorkflow(/*previousRef=*/ null);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    for (ProcessedChange change : destination.processed) {
      assertThat(change.getRequestedRevision().contextReference())
          .isEqualTo(change.getOriginRef().asString().equals("1") ? null : "HEAD");
    }
  }

  @Test
  public void iterativeWorkflowWithSameOriginContext() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChangeWithContextReference(timestamp, "HEAD");
    }
    Workflow<?, ?> workflow = iterativeWorkflow("0");

    // First change is migrated without context reference. Then we run again with
    // context ("HEAD").
    workflow.run(workdir, ImmutableList.of("1"));
    workflow = iterativeWorkflow(/*previousRef=*/ null);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    Set<String> identities = new HashSet<>();
    for (ProcessedChange change : destination.processed) {
      identities.add(change.getChangeIdentity());
    }
    // All change identities are different
    assertThat(identities).hasSize(destination.processed.size());
  }

  @Test
  public void iterativeWorkflowPublishesEvents() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }

    Workflow<?, ?> workflow = iterativeWorkflow("0");
    workflow.run(workdir, ImmutableList.of("HEAD"));

    assertThat(eventMonitor.changeMigrationStartedEventCount()).isEqualTo(9);
    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(9);
  }

  @Test
  public void iterativeWorkflowTestRecordMigrationKey() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    String name = "notDefaultWorkflow";
    Workflow<?, ?> workflow = iterativeWorkflow(name, "0");
    workflow.run(workdir, ImmutableList.of("1"));
    workflow = iterativeWorkflow(name, null);
    workflow.run(workdir, ImmutableList.of("HEAD"));

    for (ProcessedChange change : destination.processed) {
      assertThat(change.getWorkflowName()).isEqualTo(name);
    }
  }

  @Test
  public void changeRequestWorkflowTestRecordContextReference() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isEqualTo("42");
    assertThat(change.getRequestedRevision().contextReference()).isEqualTo("HEAD");
  }

  @Test
  public void changeRequestWorkflowPublishesEvents() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("HEAD"));

    assertThat(eventMonitor.changeMigrationStartedEventCount()).isEqualTo(1);
    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);
  }

  @Test
  public void iterativeWorkflowTest_defaultAuthoring() throws Exception {
    for (int timestamp = 0; timestamp < 61; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/ "42");

    workflow.run(workdir, ImmutableList.of("50"));
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
    workflow.run(workdir, ImmutableList.of("60"));
    assertThat(destination.processed).hasSize(18);
  }

  @Test
  public void testIterativeModeWithLimit() throws Exception {
    for (int timestamp = 0; timestamp < 51; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    // First change so that iterative can find last imported revisions
    iterativeWorkflow(/*previousRef=*/"40").run(workdir, ImmutableList.of("41"));
    options.workflowOptions.iterativeLimitChanges = 1;
    int numClsBefore = destination.processed.size();
    for (int i = 42; i <= 50; i++) {
      iterativeWorkflow(/*previousRef=*/null).run(workdir, ImmutableList.of("50"));
      assertThat(destination.processed).hasSize(numClsBefore + 1);
      numClsBefore++;
      assertThat(Iterables.getLast(destination.processed).getChangesSummary())
          .isEqualTo(i + " change");
    }

    // Check that we don't import anything else after we have migrated all pending changes.
    assertThrows(
        EmptyChangeException.class,
        () -> iterativeWorkflow(/*previousRef=*/ null).run(workdir, ImmutableList.of()));
  }

  @Test
  public void testIterativeModeProducesNoop() throws Exception {
    assertThat(checkIterativeModeWithError(new EmptyChangeException("This was an empty change!")))
        .hasMessageThat()
        .isEqualTo("Iterative workflow produced no changes in the destination for resolved ref: 3");
    console()
        .assertThat()
        .onceInLog(
            MessageType.WARNING, "Migration of origin revision '2' resulted in an empty change.*")
        .onceInLog(
            MessageType.WARNING, "Migration of origin revision '3' resulted in an empty change.*");
  }

  @Test
  public void testIterativeValidationException() throws Exception {
    assertThat(checkIterativeModeWithError(new ValidationException("Your change is wrong!")))
        .hasMessageThat()
        .isEqualTo("Your change is wrong!");
    console()
        .assertThat()
        .onceInLog(
            MessageType.ERROR,
            "Migration of origin revision '2' failed with error: Your change is wrong.*");
  }

  @Test
  public void testIterativeRepoException() throws Exception {
    assertThat(checkIterativeModeWithError(new RepoException("Your change is wrong!")))
        .hasMessageThat()
        .isEqualTo("Your change is wrong!");
    console()
        .assertThat()
        .onceInLog(
            MessageType.ERROR,
            "Migration of origin revision '2' failed with error: Your change is wrong.*");
  }

  @SuppressWarnings("unchecked")
  private <T extends Exception> T checkIterativeModeWithError(T exception)
      throws IOException, ValidationException {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    // Override destination with one that always throws EmptyChangeException.
    options.testingOptions.destination =
        new RecordsProcessCallDestination() {
          @Override
          public Writer<Revision> newWriter(WriterContext writerContext) {
            return new WriterImpl(writerContext.isDryRun()) {
              @Override
              public ImmutableList<DestinationEffect> write(
                  TransformResult transformResult, Glob destinationFiles, Console console)
                  throws ValidationException, RepoException {
                assert exception != null;
                Throwables.propagateIfPossible(
                    exception, ValidationException.class, RepoException.class);
                throw new RuntimeException(exception);
              }
            };
          }
        };
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"1");

    try {
      workflow.run(workdir, ImmutableList.of("3"));
      fail();
    } catch (Exception expected) {
      assertThat(expected).isInstanceOf(expected.getClass());
      return (T) expected;
    }
    return exception;
  }

  @Test
  public void iterativeWorkflowTest_allowlistAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setAuthor(NOT_ALLOWED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    allowAuthoring();

    Workflow<?, ?> workflow = iterativeWorkflow("0");

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testDefaultAuthorFlag() throws Exception {
    origin
        .addSimpleChange(0)
        .setAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setAuthor(NOT_ALLOWED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    options.workflowOptions.defaultAuthor = "From Flag <fromflag@google.com>";
    allowAuthoring();

    Workflow<?, ?> workflow = iterativeWorkflow("0");

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(
        AuthorParser.parse("From Flag <fromflag@google.com>"));
  }

  @Test
  public void testDescription() throws Exception {
    extraWorkflowFields = ImmutableList.of("description = \"Do foo with bar\"");
    assertThat(workflow().getDescription()).isEqualTo("Do foo with bar");
  }

  @Test
  public void testForcedChangeMessageAndAuthorFlags_squash() throws Exception {
    options.workflowOptions.forcedChangeMessage = FORCED_MESSAGE;
    options.workflowOptions.forcedAuthor = FORCED_AUTHOR;
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);

    Workflow<?, ?> workflow = workflow();

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).isEqualTo(FORCED_MESSAGE);
    assertThat(change.getAuthor()).isEqualTo(FORCED_AUTHOR);
  }

  @Test
  public void testSquashCustomLabel() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 0);
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);

    extraWorkflowFields = ImmutableList.of("experimental_custom_rev_id = \"CUSTOM_REV_ID\"");

    Workflow<?, ?> workflow = skylarkWorkflow("default", SQUASH);

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getRevIdLabel()).isEqualTo("CUSTOM_REV_ID");
    assertThat(change.getOriginRef().asString()).isEqualTo("3");

    options.workflowOptions.lastRevision = null;

    workflow = skylarkWorkflow("default", SQUASH);

    assertThat(
        Iterables.getOnlyElement(
            workflow.getInfo().migrationReferences()).getLastMigrated().asString())
        .isEqualTo("3");
    assertThat(
        Iterables.getOnlyElement(
            workflow.getInfo().migrationReferences()).getLastMigratedChange().
            getRevision().asString())
        .isEqualTo("3");
    origin.addSimpleChange(/*timestamp*/ 4);
    origin.addSimpleChange(/*timestamp*/ 5);

    workflow.run(workdir, ImmutableList.of(HEAD));
    ProcessedChange last = Iterables.getLast(destination.processed);
    assertThat(last.getOriginChanges()).hasSize(2);
    assertThat(last.getOriginChanges().get(0).getRevision().asString()).isEqualTo("5");
    assertThat(last.getOriginChanges().get(1).getRevision().asString()).isEqualTo("4");
  }

  @Test
  public void testForcedChangeMessageAndAuthorFlags_iterative() throws Exception {
    origin
        .addSimpleChange(0)
        .addSimpleChange(1)
        .addSimpleChange(2);

    options.workflowOptions.forcedChangeMessage = FORCED_MESSAGE;
    options.workflowOptions.forcedAuthor = FORCED_AUTHOR;
    allowAuthoring();

    Workflow<?, ?> workflow = iterativeWorkflow("0");

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(FORCED_MESSAGE);
    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(FORCED_AUTHOR);
    assertThat(destination.processed.get(1).getChangesSummary()).isEqualTo(FORCED_MESSAGE);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(FORCED_AUTHOR);
  }

  @Test
  public void testForcedChangeMessageAndAuthorFlags_changeRequest() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    options.workflowOptions.forcedChangeMessage = FORCED_MESSAGE;
    options.workflowOptions.forcedAuthor = FORCED_AUTHOR;

    Workflow<?, ?> workflow = changeRequestWorkflow("0");
    workflow.run(workdir, ImmutableList.of("1"));
    assertThat(destination.processed).hasSize(1);

    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(FORCED_MESSAGE);
    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(FORCED_AUTHOR);
  }

  private void allowAuthoring() {
    authoring = ""
        + "authoring.allowed(\n"
        + "   default = '" + DEFAULT_AUTHOR + "',\n"
        + "   allowlist = ['" + ORIGINAL_AUTHOR.getEmail() + "'],\n"
        + ")";
  }

  @Test
  public void iterativeWorkflowTest_passThruAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setAuthor(NOT_ALLOWED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    passThruAuthoring();

    iterativeWorkflow("0").run(workdir, ImmutableList.of(HEAD));

    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(NOT_ALLOWED_ORIGINAL_AUTHOR);
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
        ImmutableList.of(
            ImmutableList.of(), ImmutableList.of("some error"), ImmutableList.of("Another error")
        )
    );

    options.testingOptions.destination = programmableDestination;

    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"2");

    ChangeRejectedException expected =
        assertThrows(
            ChangeRejectedException.class, () -> workflow.run(workdir, ImmutableList.of("9")));
    assertThat(expected.getMessage())
        .contains("Iterative workflow aborted by user after: Change 3 of 7 (5)");
    assertThat(programmableDestination.processed).hasSize(3);
  }

  @Test
  public void iterativeWorkflowNoPreviousRef() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/null);
    CannotResolveRevisionException thrown =
        assertThrows(
            CannotResolveRevisionException.class,
            () -> workflow.run(workdir, ImmutableList.of("0")));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Previous revision label DummyOrigin-RevId could not be found");
  }

  @Test
  public void iterativeWorkflowEmptyChanges() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"0");
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class, () -> workflow.run(workdir, ImmutableList.of("0")));
    assertThat(thrown).hasMessageThat().contains("No new changes to import for resolved ref: 0");
  }

  @Test
  public void iterativeSkipCommits() throws Exception {
    origin.singleFileChange(0, "one", "file.txt", "a");
    origin.singleFileChange(1, "two", "file.txt", "b");
    origin.singleFileChange(2, "three", "file.txt", "b");
    origin.singleFileChange(3, "four", "file.txt", "c");
    transformations = ImmutableList.of();
    destination.failOnEmptyChange = true;
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"0");
    workflow.run(workdir, ImmutableList.of("3"));
    assertThat(destination.processed.get(1).getContent("file.txt")).isEqualTo("c");
  }

  @Test
  public void iterativeOnlyRunForMatchingOriginFiles() throws Exception {
    checkItereativeOnlyRUnForMatchingOriginFiles("one", "three");
  }

  @Test
  public void iterativeOnlyRunForMatchingOriginFiles_importNoopFlag() throws Exception {
    options.workflowOptions.migrateNoopChanges = true;
    checkItereativeOnlyRUnForMatchingOriginFiles("one", "two", "three", "four");
  }

  @Test
  public void iterativeOnlyRunForMatchingOriginFiles_importNoopField() throws Exception {
    migrateNoopChangesField = true;
    checkItereativeOnlyRUnForMatchingOriginFiles("one", "two", "three", "four");
  }

  private void checkItereativeOnlyRUnForMatchingOriginFiles(String... changes)
      throws IOException, ValidationException, RepoException {
    origin.singleFileChange(0, "base", "file.txt", "a");
    origin.singleFileChange(1, "one", "file.txt", "b");
    origin.singleFileChange(2, "two", "excluded/two", "b");
    origin.singleFileChange(3, "three", "copy.bara.sky", "");
    origin.singleFileChange(4, "four", "copy.bara.sky", "");
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"0");
    workflow.run(workdir, ImmutableList.of(HEAD));

    assertThat(destination.processed).hasSize(changes.length);
    for (int i = 0; i < changes.length; i++) {
      assertThat(destination.processed.get(i).getChangesSummary()).contains(changes[i]);
    }
  }

  @Test
  public void iterativeWithGroup() throws Exception {
    transformations = ImmutableList.of();
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2", "file.txt", "b");
    iterativeWorkflow(/*previousRef=*/"0").run(workdir, ImmutableList.of("1"));
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(3, "pending2", "file.txt", "d");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(4, "other_pending", "file.txt", "f");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending2");
    origin.singleFileChange(5, "pending3", "file.txt", "e");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    iterativeWorkflow(/*previousRef=*/null).run(workdir, ImmutableList.of("3"));

    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "2", "3"));

    // Mark last two changes as pending.
    destination.processed.get(1).pending = true;
    destination.processed.get(2).pending = true;

    iterativeWorkflow(/*previousRef=*/null).run(workdir, ImmutableList.of("5"));

    // We migrate everything from pending1
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "2", "3", "2", "3", "5"));
  }

  @Test
  public void testSquashFlagOverridesIterative() throws Exception {
    origin.singleFileChange(0, "base", "file.txt", "a");
    origin.singleFileChange(1, "one", "file.txt", "b");
    origin.singleFileChange(2, "two", "excluded/two", "b");
    origin.singleFileChange(3, "three", "copy.bara.sky", "");
    origin.singleFileChange(4, "four", "copy.bara.sky", "");
    transformations = ImmutableList.of();

    // Run with --squash
    options.general.squash = true;
    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef=*/"0");
    workflow.run(workdir, ImmutableList.of("2"));
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).contains("Project import generated by Copybara.\n");

    // Regular run afterwards
    workflow = iterativeWorkflow(/*previousRef=*/ null);
    options.general.squash = false;
    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(2);
  }

  @Test
  public void changeRequestWithGroup() throws Exception {
    transformations = ImmutableList.of("metadata.squash_notes()");
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2\n\n" + destination.getLabelNameWhenOrigin() + ": 1\n",
        "file.txt", "b");
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    skylarkWorkflow("default", CHANGE_REQUEST).run(workdir, ImmutableList.of("2"));
    origin.singleFileChange(3, "base3\n\n" + destination.getLabelNameWhenOrigin() + ": 3\n",
        "file.txt", "d");

    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("1");

    origin.singleFileChange(4, "pending2", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    skylarkWorkflow("default", CHANGE_REQUEST).run(workdir, ImmutableList.of("4"));

    assertThat(destination.processed).hasSize(2);
    assertThat(destination.processed.get(1).getBaseline()).isEqualTo("3");
  }

  @Test
  public void squashWithGroup() throws Exception {
    transformations = ImmutableList.of("metadata.squash_notes()");
    origin.singleFileChange(0, "base1", "file.txt", "a");
    origin.singleFileChange(1, "base2", "file.txt", "b");
    options.workflowOptions.lastRevision = "0";
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("1"));
    origin.singleFileChange(2, "pending1", "file.txt", "c");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(3, "pending2", "file.txt", "d");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    origin.singleFileChange(4, "other_pending", "file.txt", "f");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending2");
    origin.singleFileChange(5, "pending3", "file.txt", "e");
    origin.addRevisionToGroup(origin.resolve("HEAD"), "pending1");
    options.workflowOptions.lastRevision = null;
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("3"));

    assertThat(Iterables.getLast(destination.processed).getChangesSummary()).isEqualTo(
        "Copybara import of the project:\n"
            + "\n"
            + "  - 3 pending2 by Copybara <no-reply@google.com>\n"
            + "  - 2 pending1 by Copybara <no-reply@google.com>\n");
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("1", "3"));

    // Mark last change as pending.
    Iterables.getLast(destination.processed).pending = true;

    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("5"));

    assertThat(Iterables.getLast(destination.processed).getChangesSummary()).isEqualTo(
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
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of("0"));
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getContent("file.txt")).isEqualTo("0");
  }

  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    Workflow<?, ?> workflow = workflow();
    workflow.run(workdir, ImmutableList.of(HEAD));

    ZonedDateTime timestamp = destination.processed.get(0).getTimestamp();
    assertThat(timestamp.toInstant()).isEqualTo(Instant.ofEpochSecond(42));
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    Workflow<?, ?> workflow = workflow();
    String head = resolveHead();
    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(Files.readAllLines(workdir.resolve("checkout/file.txt"), UTF_8))
        .contains(PREFIX + head);
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    Workflow<?, ?> workflow = workflow();
    origin.addSimpleChange(/*timestamp*/ 42918273);
    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getTimestamp().toInstant())
        .isEqualTo(Instant.ofEpochSecond(42918273));
  }

  @Test
  public void usesDefaultAuthorForSquash() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    Workflow<?, ?> workflow = workflow();

    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).contains(DEFAULT_AUTHOR.toString());
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void migrationIdentityConstant() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    String before = workflow().getMigrationIdentity(origin.resolve(HEAD), transformWork);
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    String after = workflow().getMigrationIdentity(origin.resolve(HEAD), transformWork);

    // If we use 'HEAD' as reference it is constant
    assertThat(before).isEqualTo(after);

    String nonConstant = workflow().getMigrationIdentity(origin.resolve("3"), transformWork);

    // But if we use direct reference (3 instead of 'HEAD') it changes since we cannot
    // find the context reference.
    assertThat(after).isNotEqualTo(nonConstant);
  }

  @Test
  public void migrationIdentityWithUser() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    String withUser = workflow().getMigrationIdentity(origin.resolve(HEAD), transformWork);

    options.workflowOptions.workflowIdentityUser = StandardSystemProperty.USER_NAME.value();

    assertThat(withUser).isEqualTo(workflow().getMigrationIdentity(origin.resolve(HEAD),
        transformWork));

    options.workflowOptions.workflowIdentityUser = "TEST";

    String withOtherUser = workflow().getMigrationIdentity(origin.resolve(HEAD), transformWork);

    assertThat(withOtherUser).isNotEqualTo(withUser);
  }

  @Test
  public void testSquashAlreadyMigrated() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    String oldRef = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    options.setForce(true);
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of(HEAD));
    options.setForce(false); // Disable force so that we get an error
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () -> skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of(oldRef)));
    assertThat(thrown).hasMessageThat().contains("'0' has been already migrated");
  }

  @Test
  public void testSquashAlreadyMigratedSameChange() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of(HEAD));
    options.setForce(false); // Disable force so that we get an error
    EmptyChangeException thrown =
        assertThrows(
            EmptyChangeException.class,
            () -> skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of(HEAD)));
    assertThat(thrown).hasMessageThat().contains("'0' has been already migrated");
  }

  @Test
  public void testSquashLastRevDoesntExist() throws Exception {
    options.setForce(false); // Disable force so that we get an error
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = "42";

    Workflow<?, ?> workflow = workflow();

    ValidationException thrown =
        assertThrows(
            ValidationException.class, () -> workflow.run(workdir, ImmutableList.of(HEAD)));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Cannot find last imported revision."
                + " Use --force if you really want to proceed with the migration");
  }

  @Test
  public void testSquashAlreadyMigratedWithForce() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    String oldRef = resolveHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    options.setForce(true);
    workflow().run(workdir, ImmutableList.of(HEAD));
    WriterContext writerContext = new WriterContext("piper_to_github", "TEST", false,
        new DummyRevision("test"), Glob.ALL_FILES.roots());
    assertThat(
        destination
            .newWriter(writerContext)
            .getDestinationStatus(Glob.ALL_FILES, origin.getLabelName())
            .getBaseline())
        .isEqualTo("3");
    workflow().run(workdir, ImmutableList.of(oldRef));
    assertThat(
        destination
            .newWriter(writerContext)
            .getDestinationStatus(Glob.ALL_FILES, origin.getLabelName())
            .getBaseline())
        .isEqualTo("0");
  }

  @Test
  public void testWorkflowWithEmptyDiffInOrigin() throws Exception {
    GitRepository remote = GitRepository.newBareRepo(
        Files.createTempDirectory("gitdir"), getGitEnv(), /*verbose=*/true,
        DEFAULT_TIMEOUT, /*noVerify=*/ false).withWorkTree(workdir);
    remote.init();
    String primaryBranch = remote.getPrimaryBranch();

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    remote.add().files("foo.txt").run();
    remote.simpleCommand("commit", "foo.txt", "-m", "message_a");
    GitRevision lastRev = remote.resolveReference(primaryBranch);

    Files.write(workdir.resolve("foo.txt"), "change content".getBytes(UTF_8));
    remote.add().files("foo.txt").run();
    remote.simpleCommand("commit", "foo.txt", "-m", "message_a");

    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    remote.add().files("foo.txt").run();
    remote.simpleCommand("commit", "foo.txt", "-m", "message_a");

    TestingConsole testingConsole = new TestingConsole().respondYes();
    options.workflowOptions.lastRevision = lastRev.getSha1();
    options.general.force = false;
    options
        .setWorkdirToRealTempDir()
        .setConsole(testingConsole)
        .setHomeDir(StandardSystemProperty.USER_HOME.value());

    Workflow<?, ?> workflow =
        (Workflow<?, ?>) new SkylarkTestExecutor(options).loadConfig(
            "core.workflow(\n"
                + "    name = 'foo',\n"
                + String.format("    origin = git.origin(url='%s', ref='%s'),\n",
                    remote.getGitDir(), primaryBranch)
                + "    destination = folder.destination(),\n"
                + "    mode = 'ITERATIVE',\n"
                + "    authoring = " + authoring + ",\n"
                + "    transformations = [metadata.replace_message(''),],\n"
                + ")\n")
            .getMigration("foo");
    workflow.getWorkflowOptions().diffInOrigin = true;
    workflow.run(workdir, ImmutableList.of(primaryBranch));

    testingConsole.assertThat()
        .onceInLog(MessageType.WARNING, ".*No difference at diff_in_origin.*");
  }

  @Test
  public void testShowDiffInOriginFail() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    options.workflowOptions.lastRevision = "1";
    options.workflowOptions.diffInOrigin = true;

    Workflow<?, ?> workflow =
        (Workflow<?, ?>) new SkylarkTestExecutor(options).loadConfig(
            "core.workflow(\n"
                + "    name = 'foo',\n"
                + "    origin = testing.origin(),\n"
                + "    destination = testing.destination(),\n"
                + "    mode = 'ITERATIVE',\n"
                + "    authoring = " + authoring + ",\n"
                + "    transformations = [metadata.replace_message(''),],\n"
                + ")\n")
            .getMigration("foo");
    ValidationException e = assertThrows(ValidationException.class,
        () ->  workflow.run(workdir, ImmutableList.of("HEAD")));

    assertThat(e).hasMessageThat().contains(
        "diff_in_origin is not supported by origin " + origin.getType());
  }

  @Test
  public void runsTransformations() throws Exception {
    workflow().run(workdir, ImmutableList.of(HEAD));
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

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> workflow().run(workdir, ImmutableList.of(HEAD)));
    console()
        .assertThat()
        .onceInLog(MessageType.ERROR, "(\n|.)*path has unexpected [.] or [.][.] components(\n|.)*");
    assertThatPath(workdir).containsFiles(outsideFolder);
  }

  @Test
  public void invalidExcludedOriginGlob() throws Exception {
    prepareOriginExcludes("a");
    originFiles = "glob(['{'])";

    ValidationException e =
        assertThrows(
            ValidationException.class, () -> workflow().run(workdir, ImmutableList.of(HEAD)));
    console()
        .assertThat()
        .onceInLog(
            MessageType.ERROR, "(\n|.)*Cannot create a glob from: include='\\[\\{\\]' (\n|.)*");
  }

  @Test
  public void excludedOriginPathDoesntExcludeDirectories() throws Exception {
    // Ignore transforms that have no effect
    options.workflowOptions.ignoreNoop = true;

    originFiles = "glob(['**'], exclude = ['folder'])";
    Workflow<?, ?> workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, ImmutableList.of(HEAD));
    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder/file.txt", "folder2/file.txt");
  }

  @Test
  public void excludedOriginPathRecursive() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**'])";
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, ImmutableList.of(HEAD));

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
    workflow().run(workdir, ImmutableList.of(HEAD));
    console().assertThat().onceInLog(MessageType.WARNING,
        ".*NOOP: Transformation.*");
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
    VoidOperationException e =
        assertThrows(
            VoidOperationException.class, () -> workflow().run(workdir, ImmutableList.of(HEAD)));
    assertThat(e.getMessage().contains("Use --ignore-noop if you want to ignore this error"))
        .isTrue();
  }

  @Test
  public void excludedOriginRecursiveByType() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, ImmutableList.of(HEAD));

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

    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of(HEAD));

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
    Workflow<?, ?> workflow = workflow();
    prepareOriginExcludes("a");
    workflow.run(workdir, ImmutableList.of(HEAD));
    console().assertThat()
        .timesInLog(0, MessageType.INFO, "Removed .* files from workdir");
  }

  @Test
  public void excludeOriginPathIterative() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**/*.java'])";
    transformations = ImmutableList.of();
    prepareOriginExcludes("a");
    Workflow<?, ?> workflow = iterativeWorkflow(resolveHead());
    prepareOriginExcludes("b");
    prepareOriginExcludes("c");
    prepareOriginExcludes("d");
    workflow.run(workdir, ImmutableList.of(HEAD));
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
    Workflow<?, ?> workflow = iterativeWorkflow(resolveHead());
    origin.addSimpleChange(/*timestamp*/ 4242);
    workflow.run(workdir, ImmutableList.of(HEAD));

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
    workflow().run(workdir, ImmutableList.of(HEAD));

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

    Workflow<?, ?> workflow = iterativeWorkflow("deadbeef");
    CannotResolveRevisionException thrown =
        assertThrows(
            CannotResolveRevisionException.class,
            () -> workflow.run(workdir, ImmutableList.of(HEAD)));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Could not resolve --last-rev flag. Please make sure it exists in the origin:"
                + " deadbeef");
  }

  @Test
  public void changeRequest_defaultAuthoring() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("1"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isEqualTo("42");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequest_customRevIdLabel() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n\nCUSTOM_REV_ID=42")
        .addSimpleChange(1, "Second Change");

    extraWorkflowFields = ImmutableList.of("experimental_custom_rev_id = \"CUSTOM_REV_ID\"");
    setRevId = false;
    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("1"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isEqualTo("42");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");

  }

  @Test
  public void changeRequest_customRevIdLabelWithSetRevId() throws Exception {
    extraWorkflowFields = ImmutableList.of("experimental_custom_rev_id = \"CUSTOM_REV_ID\"");
    setRevId = true;
    ValidationException e =
        assertThrows(ValidationException.class, () -> changeRequestWorkflow(null));
    assertThat(e.getMessage())
        .containsMatch(
            "experimental_custom_rev_id is not allowed to be used in CHANGE_REQUEST mode if "
                + "set_rev_id is set to true. experimental_custom_rev_id is used");
  }

  @Test
  public void changeRequest_sot() throws Exception {
    origin
        .addSimpleChange(0, "Base Change")
        .addSimpleChange(1, "First Change")
        .addSimpleChange(2, "Second Change")
        .addSimpleChange(3, "Third Change");

    Workflow<?, ?> workflow = iterativeWorkflow("0");
    workflow.run(workdir, ImmutableList.of("HEAD"));
    ProcessedChange change = destination.processed.get(2);

    assertThat(change.getBaseline()).isNull();
    assertThat(change.getChangesSummary()).isEqualTo("Third Change");

    Workflow<?, ?> w = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST_FROM_SOT);

    w.run(workdir, ImmutableList.of("2"));

    change = destination.processed.get(destination.processed.size() - 1);
    assertThat(change.getChangesSummary()).isEqualTo("Second Change");
    assertThat(change.getBaseline()).isEqualTo("1");
  }

  @Test
  public void changeRequest_sot_ahead_sot() throws Exception {
    checkChangeRequest_sot_ahead_sot();
  }

  @Test
  public void changeRequest_sot_ahead_sot_retries() throws Exception {
    options.workflowOptions.changeRequestFromSotRetry = Lists.newArrayList(1, 1, 1, 1, 1);
    checkChangeRequest_sot_ahead_sot();
    console().assertThat().timesInLog(5, MessageType.WARNING,
        ".*Couldn't find a change in the destination.*Retrying in.*");
  }

  @Test
  public void changeRequest_sot_no_origin_files_match() throws Exception {
    options.workflowOptions.changeRequestFromSotLimit = 1;
    origin
        .addSimpleChange(0, "Base Change")
        .addSimpleChange(1, "First Change")
        .addSimpleChange(2, "Second Change");

    Workflow<?, ?> workflow = iterativeWorkflow("0");
    workflow.run(workdir, ImmutableList.of("1"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isNull();
    assertThat(change.getChangesSummary()).isEqualTo("First Change");

    origin.singleFileChange(3, "pending", "I_dont_exist/file.txt", "content");
    originFiles = "glob(['I_dont_exist/**'])";
    Workflow<?, ?> w = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST_FROM_SOT);

    ValidationException e =
        assertThrows(ValidationException.class, () -> w.run(workdir, ImmutableList.of("3")));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Couldn't find any parent change for 3"
                + " and origin_files = glob(include = [\"I_dont_exist/**\"])");
  }

  /**
   * Regression test that checks that we reuse the same writer in dry-run mode for multiple
   * invocations inside the same migration so that state is kept.
   */
  @Test
  public void testDryRunWithLocalGitPath() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    GitRepository destination = GitRepository.newBareRepo(destinationPath, getGitEnv(),
        /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow("
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "        url = 'file://" + origin.getWorkTree() + "',\n"
        + "        ref = '" + primaryBranch + "'\n"
        + "    ),\n"
        + "    destination = git.destination("
        + "        url = 'file://" + destination.getGitDir() + "',\n"
        + "        push = '" + primaryBranch + "',\n"
        + "        fetch = '" + primaryBranch + "'\n"
        + "    ),\n"
        + "    authoring = " + authoring + ",\n"
        + "    mode = 'SQUASH',\n"
        + ")\n";

    addGitFile(originPath, origin, "foo.txt", "not important");
    commit(origin, "baseline\n\nOrigin-Label: 1234567");

    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.initHistory = true;

    loadConfig(config).getMigration("default")
        .run(Files.createTempDirectory("workdir"), ImmutableList.of());

    // Now run again with force and no changes so that it uses the default migrator (The affected
    // path

    options.gitDestination.localRepoPath = Files.createTempDirectory("temp").toString();
    options.workflowOptions.initHistory = false;
    options.general.dryRunMode = true;
    options.setForce(true);

    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () ->
                loadConfig(config)
                    .getMigration("default")
                    .run(Files.createTempDirectory("workdir"), ImmutableList.of()));
    assertThat(e)
        .hasMessageThat()
        .contains("Migration of the revision resulted in an empty change");
    assertThat(e)
        .hasMessageThat()
        .contains(destination.parseRef("HEAD"));
  }

  private void checkChangeRequest_sot_ahead_sot()
      throws IOException, ValidationException, RepoException {
    options.workflowOptions.changeRequestFromSotLimit = 1;
    origin
        .addSimpleChange(0, "Base Change")
        .addSimpleChange(1, "First Change")
        .addSimpleChange(2, "Second Change")
        .addSimpleChange(3, "Third Change");

    Workflow<?, ?> workflow = iterativeWorkflow("0");
    workflow.run(workdir, ImmutableList.of("1"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isNull();
    assertThat(change.getChangesSummary()).isEqualTo("First Change");

    Workflow<?, ?> w = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST_FROM_SOT);

    try {
      w.run(workdir, ImmutableList.of("3"));
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("Make sure to sync the submitted changes");
    }
  }

  @Test
  public void changeRequest_sot_old_baseline() throws Exception {
    options.workflowOptions.changeRequestFromSotLimit = 2;
    origin
        .addSimpleChange(0, "Base Change")
        .addSimpleChange(1, "First Change")
        .addSimpleChange(2, "Second Change")
        .addSimpleChange(3, "Third Change");

    Workflow<?, ?> workflow = iterativeWorkflow("0");
    workflow.run(workdir, ImmutableList.of("1"));
    ProcessedChange change = destination.processed.get(0);

    assertThat(change.getBaseline()).isNull();
    assertThat(change.getChangesSummary()).isEqualTo("First Change");

    Workflow<?, ?> w = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST_FROM_SOT);

    w.run(workdir, ImmutableList.of("3"));
    change = destination.processed.get(destination.processed.size() - 1);
    assertThat(change.getChangesSummary()).isEqualTo("Third Change");
    assertThat(change.getBaseline()).isEqualTo("1");
  }

  @Test
  public void changeRequest_passThruAuthoring() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    passThruAuthoring();
    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("1"));

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(ORIGINAL_AUTHOR);
  }

  @Test
  public void changeRequest_allowlistAuthoring() throws Exception {
    origin
        .setAuthor(NOT_ALLOWED_ORIGINAL_AUTHOR)
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");

    allowAuthoring();

    changeRequestWorkflow(null).run(workdir, ImmutableList.of("1"));

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void changeRequestManualBaseline() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change");
    Workflow<?, ?> workflow = changeRequestWorkflow("24");
    workflow.run(workdir, ImmutableList.of("1"));
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("24");
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequestChanges() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change")
        .addSimpleChange(2, "Third Change");
    includeReleaseNotes = true;
    Workflow<?, ?> workflow = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo(""
        + "Copybara import of the project:\n"
        + "\n"
        + "  - 2 Third Change by Copybara <no-reply@google.com>\n"
        + "  - 1 Second Change by Copybara <no-reply@google.com>\n");
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequestSetRevIdDisabled() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change")
        .addSimpleChange(2, "Third Change");
    setRevId = false;
    Workflow<?, ?> workflow = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
    assertThat(destination.processed.get(0).isSetRevId()).isFalse();
    console().assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void changeRequestChanges_lastChange() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Second Change")
        .addSimpleChange(2, "Third Change");
    transformations = ImmutableList.of("metadata.use_last_change()");
    Workflow<?, ?> workflow = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
    assertThat(destination.processed.get(0).getChangesSummary()).isEqualTo("Third Change");
  }

  @Test
  public void changeRequestSmartPrune() throws Exception {
    smartPrune = true;
    ImmutableList<DiffFile> diffFiles = checkChangeRequestSmartPrune();
    ImmutableMap<String, DiffFile> byName = Maps.uniqueIndex(diffFiles, DiffFile::getName);
    assertThat(byName.size()).isEqualTo(3);
    assertThat(byName.get("folder/deleted.txt").getOperation()).isEqualTo(DELETE);
    assertThat(byName.get("folder/modified.txt").getOperation()).isEqualTo(MODIFIED);
    assertThat(byName.get("folder/added.txt").getOperation()).isEqualTo(ADD);
    assertThat(byName.get("folder/unmodified.txt")).isNull();
  }

  @Test
  public void changeRequestSmartPrune_disabledFlag() throws Exception {
    smartPrune = true;
    // This flag wins
    options.workflowOptions.noSmartPrune = true;
    ImmutableList<DiffFile> diffFiles = checkChangeRequestSmartPrune();
    assertThat(diffFiles).isNull();
  }

  @Test
  public void changeRequestSmartPrune_disabled() throws Exception {
    ImmutableList<DiffFile> diffFiles = checkChangeRequestSmartPrune();
    assertThat(diffFiles).isNull();
  }

  @Test
  public void smartPruneForDifferentWorkflowMode() throws Exception {
    smartPrune = true;
    ValidationException e =
        assertThrows(
            ValidationException.class, () -> skylarkWorkflow("default", WorkflowMode.SQUASH));
    console()
        .assertThat()
        .onceInLog(
            MessageType.ERROR,
            ".*'smart_prune = True' is only supported for CHANGE_REQUEST mode.*");
  }

  @Test
  public void changeRequestSmartPrune_manualBaseline() throws Exception {
    smartPrune = true;
    // For now we don't support smart_prune with the flag since the flag refers to the destination
    // baseline and for smart_prune we need the origin revision. We might revisit this if it
    // if requested by users
    options.workflowOptions.changeBaseline = "42";
    ValidationException e =
        assertThrows(ValidationException.class, () -> checkChangeRequestSmartPrune());
    assertThat(e)
        .hasMessageThat()
        .contains("smart_prune is not compatible with --change-request-parent");
  }

  private ImmutableList<DiffFile> checkChangeRequestSmartPrune()
      throws IOException, ValidationException, RepoException {
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base1 = Files.createTempDirectory(fileSystem.getPath("/"), "base");

    writeFile(base1, "excluded/file.txt", "EXCLUDED FOO: Shouldn't be seen");
    writeFile(base1, "folder/deleted.txt", "");
    writeFile(base1, "folder/unmodified.txt", "");
    writeFile(base1, "folder/modified.txt", "foo");
    origin.addChange(0, base1,
        String.format("One Change\n\n%s=42", destination.getLabelNameWhenOrigin()),
        /*matchesGlob=*/ true);
    Path base2 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    FileUtil.copyFilesRecursively(base1, base2, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);

    Files.delete(base2.resolve("folder/deleted.txt"));
    origin.addChange(1, base2, "change 1", /*matchesGlob=*/ true);
    Path base3 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    FileUtil.copyFilesRecursively(base2, base3, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);

    writeFile(base3, "excluded/file.txt", "EXCLUDED bAR: Shouldn't be seen");
    writeFile(base3, "folder/modified.txt", "bar");
    writeFile(base3, "folder/added.txt", "only_in_change");
    origin.addChange(2, base3, "change 2", /*matchesGlob=*/ true);

    options.workflowOptions.ignoreNoop = false;
    transformations = ImmutableList.of(""
            + "        core.replace(\n"
            + "             before = 'only_in_change',\n"
            + "             after = 'foo',\n"
            + "        )",
        "          core.verify_match('EXCLUDED', verify_no_match=True)");
    Workflow<?, ?> workflow = skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
    workflow.run(workdir, ImmutableList.of("HEAD"));
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
    return destination.processed.get(0).getAffectedFilesForSmartPrune();
  }

  @Test
  public void mergeImport_mergeSuccess_fileDeleteSuccess_squash_autoPatchGenerated()
      throws Exception {
    mergeImport = true;
    autoPatchFileDirectoryPrefix = "'dir'";
    autoPatchfileContentsPrefix = "'This patch file was generated by Copybara!\\n'";
    autoPatchfileSuffix = "'.patch'";
    autoPatchfileDirectory = "'GOIMPORT/AUTOPATCHES/'";
    autoPatchfileStripFilenamesAndLineNumbers = true;
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base1 = Files.createTempDirectory(fileSystem.getPath("/"), "base");

    // populate the baseline
    writeFile(base1, "dir/foo.txt", "a\nb\nc\n");
    writeFile(base1, "dir/to_delete.txt", "I will be deleted");
    origin.addChange(
        0,
        base1,
        String.format("One Change\n\n%s=42", destination.getLabelNameWhenOrigin()),
        /*matchesGlob=*/ true);

    // run the workflow
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = skylarkWorkflowInDirectory("default", SQUASH, "dir/");
    workflow.run(workdir, ImmutableList.of("HEAD"));

    // a second change that isn't baseline
    Path base2 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    FileUtil.copyFilesRecursively(base1, base2, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
    writeFile(base2, "dir/bar.txt", "Another file");
    origin.addChange(1, base2, "change 1", /*matchesGlob=*/ true);

    // fake a destination-only change
    Path base3 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    writeFile(base3, "dir/foo.txt", "a\nb\nc\nbar");
    destination.processed.add(
        new ProcessedChange(
            TransformResults.of(base3, new DummyRevision("1")),
            ImmutableMap.of(
                "/dir/foo.txt", "a\nb\nc\nbar", "/dir/to_delete.txt", "I will be deleted"),
            "1",
            Glob.createGlob(ImmutableList.of(destinationFiles)),
            false));

    // new origin change and run workflow again
    writeFile(base2, "dir/foo.txt", "foo\na\nb\nc\n");
    Files.delete(base2.resolve("dir/to_delete.txt"));
    // set last revision and reload workflow for it to take effect
    origin.addChange(2, base2, "change 2", true);
    options.setLastRevision(origin.changes.get(0).asString());
    // options reset
    workflow = skylarkWorkflowInDirectory("default", SQUASH, "dir/");
    workflow.run(workdir, ImmutableList.of("HEAD"));

    assertThat(
            destination
                .processed
                .get(destination.processed.size() - 1)
                .getWorkdir()
                .get("dir/foo.txt"))
        .isEqualTo("foo\na\nb\nc\nbar");
    assertThat(
            destination
                .processed
                .get(destination.processed.size() - 1)
                .getWorkdir()
                .get("dir/GOIMPORT/AUTOPATCHES/foo.txt.patch"))
        .isEqualTo(
            "This patch file was generated by Copybara!\n"
                + "@@\n"
                + " a\n"
                + " b\n"
                + " c\n"
                + "+bar\n"
                + "\\ No newline at end of file\n");
    assertThat(destination.processed.get(1).getWorkdir().get("/dir/to_delete.txt"))
        .isEqualTo("I will be deleted");
    assertThat(
            destination
                .processed
                .get(destination.processed.size() - 1)
                .getWorkdir()
                .containsKey("/dir/to_delete.txt"))
        .isFalse();
  }

  @Test
  public void mergeImport_mergeSuccess_fileDeleteSuccess_changeRequest() throws Exception {
    mergeImport = true;
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path base1 = Files.createTempDirectory(fileSystem.getPath("/"), "base");

    // populate the baseline
    writeFile(base1, "foo.txt", "a\nb\nc\n");
    writeFile(base1, "to_delete.txt", "I will be deleted");
    origin.addChange(
        0,
        base1,
        String.format("One Change\n\n%s=42", destination.getLabelNameWhenOrigin()),
        /*matchesGlob=*/ true);

    // a second change that isn't baseline
    Path base2 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    FileUtil.copyFilesRecursively(base1, base2, CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
    writeFile(base2, "bar.txt", "Another file");
    origin.addChange(1, base2, "change 1", /*matchesGlob=*/ true);

    // run the workflow
    transformations = ImmutableList.of();
    Workflow<?, ?> workflow = skylarkWorkflow("default", CHANGE_REQUEST);
    workflow.run(workdir, ImmutableList.of("HEAD"));

    // fake a destination-only change
    Path base3 = Files.createTempDirectory(fileSystem.getPath("/"), "base");
    writeFile(base3, "foo.txt", "a\nb\nc\nbar");
    destination.processed.add(
        new ProcessedChange(
            TransformResults.of(base3, new DummyRevision("1")),
            ImmutableMap.of("/foo.txt", "a\nb\nc\nbar", "/to_delete.txt", "I will be deleted"),
            "1",
            Glob.createGlob(ImmutableList.of(destinationFiles)),
            false));

    // new origin change and run workflow again
    writeFile(base2, "foo.txt", "foo\na\nb\nc\n");
    Files.delete(base2.resolve("to_delete.txt"));
    origin.addChange(2, base2, "change 2", true);
    workflow.run(workdir, ImmutableList.of("HEAD"));

    assertThat(
            destination.processed.get(destination.processed.size() - 1).getWorkdir().get("foo.txt"))
        .isEqualTo("foo\na\nb\nc\nbar");
    assertThat(destination.processed.get(1).getWorkdir().get("/to_delete.txt"))
        .isEqualTo("I will be deleted");
    assertThat(
            destination
                .processed
                .get(destination.processed.size() - 1)
                .getWorkdir()
                .containsKey("/to_delete.txt"))
        .isFalse();
  }

  @Test
  public void changeRequestEmptyChanges() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config =
        "def after_all(ctx):\n"
        + "  ctx.destination.message('after_all '"
        + " + str([e.type + ' '+ e.summary for e in ctx.effects]))\n"
        + "\n"
        + "core.workflow("
        + "    name = 'default',"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
            origin.getWorkTree(), primaryBranch)
        + "    destination = testing.destination(),\n"
        + "    authoring = " + authoring + ","
        + "    origin_files = glob(['included/**']),"
        + "    mode = '" + WorkflowMode.CHANGE_REQUEST + "',"
        + "    after_workflow = [after_all]"
        + ")\n";

    Migration workflow = loadConfig(config).getMigration("default");

    Files.createDirectory(originPath.resolve("included"));
    Files.write(originPath.resolve("included/foo.txt"), "a".getBytes(UTF_8));
    origin.add().files("included/foo.txt").run();
    origin.commit(
        "Foo <foo@bara.com>",
        ZonedDateTime.now(ZoneId.systemDefault()),
        "the baseline\n\n" + destination.getLabelNameWhenOrigin() + "=42");

    Files.createDirectory(originPath.resolve("excluded"));
    Files.write(originPath.resolve("excluded/foo.txt"), "a".getBytes(UTF_8));
    origin.add().files("excluded/foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "head change");

    EmptyChangeException thrown =
        assertThrows(EmptyChangeException.class, () -> workflow.run(workdir, ImmutableList.of()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "doesn't include any change for origin_files = glob(include = [\"included/**\"])");
    assertThat(destination.getEndpoint().messages).hasSize(1);
    assertThat(destination.getEndpoint().messages.get(0))
        .containsMatch(".*didn't affect any destination file.*");
  }

  @Test
  public void reversibleCheckSymlinkError() throws Exception {
    Path someRoot = Files.createTempDirectory("someRoot");
    Path originPath = someRoot.resolve("origin");
    Files.createDirectories(originPath);

    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow(\n"
        + "    name = 'default',\n"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
            origin.getWorkTree(),  primaryBranch)
        + "    destination = testing.destination(),\n"
        + "    authoring = " + authoring + ",\n"
        + "    origin_files = glob(['included/**']),\n"
        + "    reversible_check = True,\n"
        + "    mode = '" + WorkflowMode.SQUASH + "',\n"
        + ")\n";

    Migration workflow = loadConfig(config).getMigration("default");

    Path included = originPath.resolve("included");
    Files.createDirectory(included);
    Files.write(originPath.resolve("included/foo.txt"), "a".getBytes(UTF_8));

    Path fileOutsideCheckout = someRoot.resolve("file_outside_checkout");
    Files.write(fileOutsideCheckout, "THE CONTENT".getBytes(UTF_8));
    Files.createSymbolicLink(included.resolve("symlink"), included.relativize(fileOutsideCheckout));

    origin.add().files("included/foo.txt").run();
    origin.add().files("included/symlink").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "A commit");

    ValidationException expected =
        assertThrows(ValidationException.class, () -> workflow.run(workdir, ImmutableList.of()));
    assertThat(expected.getMessage())
        .matches(
            ""
                + "Failed to perform reversible check of transformations due to symlink '.*' that "
                + "points outside the checkout dir. Consider removing this symlink from your "
                + "origin_files or, alternatively, set reversible_check = False in your "
                + "workflow.");
  }

  @Test
  public void reversibleCheckFiles() throws Exception {
    Path someRoot = Files.createTempDirectory("someRoot");
    Path originPath = someRoot.resolve("origin");
    Files.createDirectories(originPath);

    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow(\n"
        + "    name = 'default',\n"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
            origin.getWorkTree(),  primaryBranch)
        + "    destination = testing.destination(),\n"
        + "    authoring = " + authoring + ",\n"
        + "    reversible_check = True,\n"
        + "    reversible_check_ignore_files = glob([\"to_ignore/**\"],"
        + "                                          exclude = [\"to_ignore/exclude\"]),\n"
        + "    mode = '" + WorkflowMode.SQUASH + "',\n"
        + "    transformations = ["
        + "      core.replace(before = 'aa', after = 'bb')"
        + "    ]"
        + ")\n";

    Migration workflow = loadConfig(config).getMigration("default");

    GitTestUtil.writeFile(originPath, "test", "aabb");
    GitTestUtil.writeFile(originPath, "to_ignore/test", "aabb");
    GitTestUtil.writeFile(originPath, "to_ignore/exclude", "aabb");

    origin.add().all().run();
    origin.simpleCommand("commit", "-m", "change");

    try {
      workflow.run(Files.createDirectory(someRoot.resolve("run1")), ImmutableList.of());
      fail();
    } catch (ValidationException e) {
      assertThat(e).hasMessageThat().contains("is not reversible");
      String msg =
          console().getMessages().stream()
              .filter(
                  m -> m.getType() == MessageType.ERROR && m.getText().contains("non-reversible"))
              .findFirst()
              .get()
              .getText();
      assertThat(msg).contains("--- a/origin/test");
      assertThat(msg).doesNotContain("--- a/origin/to_ignore/test");
      assertThat(msg).contains("--- a/origin/to_ignore/exclude");
    }

    // Now lets fix the only file that we check in reversible check:
    GitTestUtil.writeFile(originPath, "test", "aa");
    GitTestUtil.writeFile(originPath, "to_ignore/exclude", "aa");
    origin.add().all().run();
    origin.simpleCommand("commit", "-m", "change 2");
    workflow.run(Files.createDirectory(someRoot.resolve("run2")), ImmutableList.of());
  }

  @Test
  public void testGitDescribeVersionSemanticsForFilteredChanges_squash() throws Exception {
    runGitDescribeVersionSemanticsForFilteredChanges("SQUASH");

    assertThat(destination.processed).hasSize(1);
    // GIT_DESCRIBE_REQUESTED_VERSION points to the resolved, head revision
    // GIT_CHANGE_DESCRIBE_REVISION points to the most up-to-date change that is being migrated
    assertThat(destination.processed.get(0).getChangesSummary()).matches(
        "Resolved revision is 0.1-3-g.* and change revision is 0.1-2-g(.|\n)*"
    );
  }

  @Test
  public void testGitDescribeVersionSemanticsForFilteredChanges_iterative() throws Exception {
    runGitDescribeVersionSemanticsForFilteredChanges("ITERATIVE");

    assertThat(destination.processed).hasSize(2);

    // GIT_DESCRIBE_REQUESTED_VERSION points to the resolved, head revision
    // GIT_CHANGE_DESCRIBE_REVISION points to the current change that is being migrated
    assertThat(destination.processed.get(0).getChangesSummary()).matches(
        "Resolved revision is 0.1-3-g.* and change revision is 0.1-1-g(.|\n)*"
    );
    assertThat(destination.processed.get(1).getChangesSummary()).matches(
        "Resolved revision is 0.1-3-g.* and change revision is 0.1-2-g(.|\n)*"
    );
  }

  private void runGitDescribeVersionSemanticsForFilteredChanges(String mode)
      throws IOException, RepoException, ValidationException {
    Path someRoot = Files.createTempDirectory("someRoot");
    Path originPath = someRoot.resolve("origin");
    Files.createDirectories(originPath);

    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow(\n"
        + "    name = 'default',\n"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
            origin.getWorkTree(),  primaryBranch)
        + "    destination = testing.destination(),\n"
        + "    authoring = " + authoring + ",\n"
        + "    origin_files = glob(['included/**']),\n"
        + "    mode = '" + mode + "',\n"
        + "    transformations = ["
        + "metadata.add_header('Resolved revision is ${GIT_DESCRIBE_REQUESTED_VERSION}"
        + " and change revision is ${GIT_DESCRIBE_CHANGE_VERSION}')]"
        + ")\n";

    GitTestUtil.writeFile(originPath, "initial.txt", "initial");
    origin.add().files("initial.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "Initial");
    origin.simpleCommand("tag", "-m", "this is a tag!", "0.1");

    options.setLastRevision(origin.parseRef("HEAD"));
    Migration workflow = loadConfig(config).getMigration("default");

    GitTestUtil.writeFile(originPath, "included/foo.txt", "a");
    origin.add().files("included/foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "one");

    GitTestUtil.writeFile(originPath, "included/foo.txt", "b");
    origin.add().files("included/foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "two");

    GitTestUtil.writeFile(originPath, "excluded/foo.txt", "c");
    origin.add().files("excluded/foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "three");

    workflow.run(workdir, ImmutableList.of());
  }

  @Test
  public void customIdentityTest() throws Exception {
    options.workflowOptions.initHistory = true;
    Config config = loadConfig(""
        + "core.workflow(\n"
        + "    name = 'one',\n"
        + "    authoring = " + authoring + "\n,"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [metadata.expose_label('some_label', 'new_label')],\n"
        + "    change_identity = '${copybara_config_path}foo${label:new_label}',\n"
        + "    mode = 'ITERATIVE',\n"
        + ")\n\n"
        + "core.workflow(\n"
        + "    name = 'two',\n"
        + "    authoring = " + authoring + "\n,"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    change_identity = '${copybara_config_path}foo${label:some_label}',\n"
        + "    mode = 'ITERATIVE',\n"
        + ")\n\n"
        + "core.workflow(\n"
        + "    name = 'three',\n"
        + "    authoring = " + authoring + "\n,"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    change_identity = '${copybara_config_path}foo${label:not_found}',\n"
        + "    mode = 'ITERATIVE',\n"
        + ")\n\n"
        + "");

    origin.addSimpleChange(1, "change\n\nsome_label=a");
    origin.addSimpleChange(2, "change\n\nsome_label=b");
    origin.addSimpleChange(3, "change\n\nsome_label=c");

    config.getMigration("one").run(workdir, ImmutableList.of());

    assertThat(destination.processed).hasSize(3);
    ImmutableList<String> oneResult = destination.processed.stream().map(
        ProcessedChange::getChangeIdentity).collect(ImmutableList.toImmutableList());

    // Different identities
    assertThat(ImmutableSet.copyOf(oneResult)).hasSize(3);

    destination.processed.clear();

    config.getMigration("two").run(workdir, ImmutableList.of());

    ImmutableList<String> twoResult = destination.processed.stream().map(
        ProcessedChange::getChangeIdentity).collect(ImmutableList.toImmutableList());

    assertThat(oneResult).isEqualTo(twoResult);

    destination.processed.clear();
    config.getMigration("three").run(workdir, ImmutableList.of());

    ImmutableList<String> threeResult = destination.processed.stream().map(
        ProcessedChange::getChangeIdentity).collect(ImmutableList.toImmutableList());

    assertThat(oneResult).isNotEqualTo(threeResult);
  }

  @Test
  public void customIdentity_customPathIncluded() throws Exception {
    options.workflowOptions.initHistory = true;
    byte[] cfgContent =
        (""
                + "core.workflow(\n"
                + "    name = 'default',\n"
                + "    authoring = "
                + authoring
                + "\n,"
                + "    origin = testing.origin(),\n"
                + "    destination = testing.destination(),\n"
                + "    change_identity = '${copybara_config_path}foo${label:some_label}',\n"
                + "    mode = 'ITERATIVE',\n"
                + ")\n\n"
                + "")
            .getBytes(UTF_8);
    Config config1 = skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("foo/copy.bara.sky", cfgContent), "foo/copy.bara.sky"));

    Config config2 = skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("bar/copy.bara.sky", cfgContent), "bar/copy.bara.sky"));

    origin.addSimpleChange(1, "change\n\nsome_label=a");

    config1.getMigration("default").run(workdir, ImmutableList.of());
    ImmutableList<String> oneResult = destination.processed.stream().map(
        ProcessedChange::getChangeIdentity).collect(ImmutableList.toImmutableList());
    destination.processed.clear();
    config2.getMigration("default").run(workdir, ImmutableList.of());
    ImmutableList<String> twoResult = destination.processed.stream().map(
        ProcessedChange::getChangeIdentity).collect(ImmutableList.toImmutableList());

    assertThat(oneResult).isNotEqualTo(twoResult);
  }

  @Test
  public void changeRequest_findParentBaseline() throws Exception {
    origin
        .addSimpleChange(0, "One Change\n" + destination.getLabelNameWhenOrigin() + "=42")
        .addSimpleChange(1, "Last Change\n" + destination.getLabelNameWhenOrigin() + "=BADBAD");
    Workflow<?, ?> workflow = changeRequestWorkflow(null);
    workflow.run(workdir, ImmutableList.of("1"));
    assertThat(destination.processed.get(0).getBaseline()).isEqualTo("42");
  }

  @Test
  public void testNullAuthoring() throws Exception {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                loadConfig(
                    ""
                        + "core.workflow(\n"
                        + "    name = 'foo',\n"
                        + "    origin = testing.origin(),\n"
                        + "    destination = testing.destination(),\n"
                        + ")\n"));
    console()
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required named argument: authoring.*");
  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of("copy.bara.sky", content.getBytes(UTF_8)), "copy.bara.sky"));
  }

  private Config loadConfigInDirectory(String content, String dir)
      throws IOException, ValidationException {
    return skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of(dir.concat("copy.bara.sky"), content.getBytes(UTF_8)),
            dir.concat("copy.bara.sky")));
  }

  @Test
  public void testNullOrigin() throws Exception {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                loadConfig(
                    ""
                        + "core.workflow(\n"
                        + "    name = 'foo',\n"
                        + "    authoring = "
                        + authoring
                        + "\n,"
                        + "    destination = testing.destination(),\n"
                        + ")\n"));
    for (Message message : console().getMessages()) {
      System.err.println(message);
    }
    console()
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required named argument: origin.*");
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
  public void testDateTimeOffset() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.ITERATIVE, ""
        + "def third(ctx):\n"
        + "  msg = ''\n"
        + "  for c in ctx.changes.current:\n"
        + "    msg += c.date_time_iso_offset + '\\n'\n"
        + "  ctx.set_message(msg)\n");
    ProcessedChange change = destination.processed.get(1);

    TemporalAccessor actual = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(
        change.getChangesSummary().trim());

    // Refers to the same time
    assertThat(actual.getLong(ChronoField.INSTANT_SECONDS))
        .isEqualTo(change.getTimestamp().getLong(ChronoField.INSTANT_SECONDS));

    // With the same time-zone
    assertThat(actual.getLong(ChronoField.OFFSET_SECONDS))
        .isEqualTo(change.getTimestamp().getLong(ChronoField.OFFSET_SECONDS));
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
        .addSimpleChange(2000000000, "third commit");

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
    config.getMigration("default").run(workdir, ImmutableList.of("2"));
  }

  @Test
  public void testNullDestination() throws Exception {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                loadConfig(
                    ""
                        + "core.workflow(\n"
                        + "    name = 'foo',\n"
                        + "    authoring = "
                        + authoring
                        + "\n,"
                        + "    origin = testing.origin(),\n"
                        + ")\n"));
    console()
        .assertThat()
        .onceInLog(MessageType.ERROR, ".*missing 1 required named argument: destination.*");
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
  public void testFailWithNoopFunc() throws Exception {
    Transformation transformation = ((Workflow<?, ?>) loadConfig(""
        + "def fail_test(ctx):\n"
        + "  core.fail_with_noop('Hello, this is empty!')\n"
        + ""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    authoring = " + authoring + "\n,"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [fail_test],"
        + ")\n").getMigration("default")).getTransformation();

    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () -> transformation.transform(TransformWorks.of(workdir, "message", console())));
    assertThat(e).hasMessageThat().contains("Hello, this is empty!");
  }

  @Test
  public void testWorkflowDefinedInParentConfig() throws Exception {
    Workflow<?, ?> wf = ((Workflow<?, ?>) skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of(
                "foo.bara.sky", (""
                    + "def foo_wf(name):\n"
                    + "  core.workflow(\n"
                    + "      name = 'default',\n"
                    + "      authoring = " + authoring + "\n,"
                    + "      origin = testing.origin(),\n"
                    + "      destination = testing.destination(),\n"
                    + ")\n"
                    + "").getBytes(UTF_8),
                "copy.bara.sky", (""
                    + "load('foo', 'foo_wf')\n"
                    + "foo_wf('default')\n"
                    + "").getBytes(UTF_8)), "copy.bara.sky")).getMigration("default"));

    assertThat(wf.getName()).isEqualTo("default");
  }

  @Test
  public void testNonFreezeParentDynamicFunction() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");
    Workflow<?, ?> wf = ((Workflow<?, ?>) skylark.loadConfig(
        new MapConfigFile(
            ImmutableMap.of(
                "foo.bara.sky", (""
                    + "def _dynamic_foo(ctx):\n"
                    + "  for f in ctx.run(glob(['**'])):\n"
                    + "    if f.attr.size > 10:\n"
                    + "      ctx.console.info('Hello! this is function!')\n"
                    + "\n"
                    + "def dynamic_foo():\n"
                    + "  return [_dynamic_foo]\n"
                    + "").getBytes(UTF_8),
                "copy.bara.sky", (""
                    + "load('foo', 'dynamic_foo')\n"
                    + "transformations = dynamic_foo()\n"
                    + "core.workflow(\n"
                    + "    name = 'default',\n"
                    + "    authoring = " + authoring + "\n,"
                    + "    origin = testing.origin(),\n"
                    + "    destination = testing.destination(),\n"
                    + "    transformations = transformations,\n"
                    + ")\n"
                    + "").getBytes(UTF_8)), "copy.bara.sky")).getMigration("default"));

    wf.run(workdir, ImmutableList.of("HEAD"));
  }


  @Test
  public void testDynamicTransformThrowingRepoException() throws Exception {
    SkylarkTestExecutor exec = skylark.withStaticModules(ImmutableSet.of(ThrowingCallable.class));
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    Workflow<?, ?> wf = ((Workflow<?, ?>) exec.loadConfig(
        new MapConfigFile(
            ImmutableMap.of(
                "copy.bara.sky", (""
                    + "def dynamic_foo(ctx):\n"
                    + "  dynamic_test.throw_repo()\n"
                    + ""
                    + "core.workflow(\n"
                    + "    name = 'default',\n"
                    + "    authoring = " + authoring + "\n,"
                    + "    origin = testing.origin(),\n"
                    + "    destination = testing.destination(),\n"
                    + "    transformations = [dynamic_foo],\n"
                    + ")\n"
                    + "").getBytes(UTF_8)), "copy.bara.sky")).getMigration("default"));

    RepoException expected =
        assertThrows(RepoException.class, () -> wf.run(workdir, ImmutableList.of("HEAD")));
    assertThat(expected).hasMessageThat().contains("Oh noes");
  }

  @StarlarkBuiltin(name = "dynamic_test", documented = false, doc = "Just a Test.")
  public static class ThrowingCallable implements StarlarkValue {
    @SuppressWarnings("unused")
    @StarlarkMethod(
        name = "throw_repo",
        documented = false,
        doc = "Throw repo exception.")
    public String throwRepoException() throws RepoException  {
     throw new RepoException("Oh noes");
    }
  }

  @Test
  public void nonReversibleButCheckReverseSet() throws Exception {
    origin
        .singleFileChange(0, "one commit", "foo.txt", "1")
        .singleFileChange(1, "one commit", "test.txt", "1\nTRANSFORMED42");
    Workflow<?, ?> workflow = changeRequestWorkflow("0");
    ValidationException e =
        assertThrows(ValidationException.class, () -> workflow.run(workdir, ImmutableList.of("1")));
    assertThat(e).hasMessageThat().isEqualTo("Workflow 'default' is not reversible");
    console()
        .assertThat()
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
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
  }

  @Test
  public void testSkylarkTransformParams() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def _test_impl(ctx):\n"
        + "  ctx.set_message("
        + "    ctx.message + ctx.params['name'] + str(ctx.params['number']) + '\\n')\n"
        + "\n"
        + "def test(name, number = 2):\n"
        + "  return core.dynamic_transform(impl = _test_impl,\n"
        + "                           params = { 'name': name, 'number': number})\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [\n"
        + "    metadata.replace_message(''),\n"
        + "    test('example'),\n"
        + "    test('other', 42),\n"
        + "  ],\n"
        + "  authoring = " + authoring + ",\n"
        + ")\n";
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
    assertThat(Iterables.getOnlyElement(destination.processed).getChangesSummary())
        .isEqualTo("example2\n"
            + "other42\n");

  }

  @Test
  public void testOnFinishHook() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def _test_impl(ctx):\n"
        + "  for effect in ctx.effects:\n"
        + "    ctx.origin.message(effect.origin_refs[0].ref + ' created ' "
        + "+ effect.destination_ref.id + ' ' + ctx.params['name'] + str(ctx.params['number']))\n"
        + "    ctx.destination.message(effect.origin_refs[0].ref + ' created ' "
        + "+ effect.destination_ref.id + ' ' + ctx.params['name'] + str(ctx.params['number']))\n"
        + "\n"
        + "def other(ctx):\n"
        + "  ctx.origin.message('constant')\n"
        + "  ctx.destination.message('constant')\n"
        + "\n"
        + "def test(name, number = 2):\n"
        + "  return core.action(impl = _test_impl,\n"
        + "                               params = { 'name': name, 'number': number})\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [test('example'), test('other', 42), other]"
        + ")\n";
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
    assertThat(origin.getEndpoint().getMessages())
        .containsExactly("0 created destination/1 example2",
            "0 created destination/1 other42",
            "constant");
    assertThat(destination.getEndpoint().getMessages())
        .containsExactly("0 created destination/1 example2",
            "0 created destination/1 other42",
            "constant");
  }

  @Test
  public void testCliLabelsInAfterMigration() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");
    options.setLabels(ImmutableMap.of("foo", "label_bar"));

    String config = ""
        + "def _test_impl(ctx):\n"
        + "  for effect in ctx.effects:\n"
        + "    ctx.origin.message(ctx.cli_labels['foo'])\n"
        + "\n"
        + "def test():\n"
        + "  return core.action(impl = _test_impl,\n"
        + "                               params = {})\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [test()]"
        + ")\n";
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
    assertThat(origin.getEndpoint().getMessages())
        .containsExactly("label_bar");
  }

  @Test
  public void testAfterAllMigrations1() throws Exception {
    checkAfterAllMigrations("1", ITERATIVE);
    assertThat(destination.getEndpoint().messages).containsExactly(
        "after [\"CREATED destination/1\"]",
        "after_all [\"CREATED destination/1\"]");
  }

  @Test
  public void testAfterAllMigrations2() throws Exception {
    checkAfterAllMigrations("3", ITERATIVE);
    assertThat(destination.getEndpoint().messages).containsExactly(
        "after [\"CREATED destination/1\"]",
        "after [\"CREATED destination/2\"]",
        "after [\"CREATED destination/3\"]",
        "after_all [\"CREATED destination/1\","
            + " \"CREATED destination/2\","
            + " \"CREATED destination/3\"]");
  }

  @Test
  public void testAfterAllMigrations3() throws Exception {
    checkAfterAllMigrations("3", SQUASH);
    assertThat(destination.getEndpoint().messages).containsExactly(
        "after [\"CREATED destination/1\"]",
        "after_all [\"CREATED destination/1\"]");
  }

  private void checkAfterAllMigrations(String rev, WorkflowMode mode) throws Exception {
    origin.singleFileChange(0, "base commit", "foo.txt", "0");
    DummyRevision base = origin.resolve("HEAD");
    origin.singleFileChange(1, "first commit", "foo.txt", "1");
    origin.singleFileChange(2, "second commit", "foo.txt", "2");
    origin.singleFileChange(3, "third commit", "foo.txt", "3");

    String config = ""
        + "def after_all(ctx):\n"
        + "  ctx.destination.message('after_all '"
        + " + str([e.type + ' ' + e.destination_ref.id for e in ctx.effects]))\n"
        + "def after(ctx):\n"
        + "  ctx.destination.message('after '"
        + " + str([e.type + ' ' + e.destination_ref.id for e in ctx.effects]))\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  mode = \"" + mode.name() + "\",\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [after],\n"
        + "  after_workflow = [after_all]"
        + ")\n";
    options.setLastRevision(base.asString());
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of(rev));
  }

  @Test
  public void testOnFinishHookCreatesEffects() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def test(ctx):\n"
        + "  origin_refs = [ctx.origin.new_origin_ref('1111')]\n"
        + "  dest_ref = ctx.destination.new_destination_ref(ref = '9999', type = 'some_type')\n"
        + "  ctx.record_effect('New effect', origin_refs, dest_ref)\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [test]"
        + ")\n";
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());

    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);
    ChangeMigrationFinishedEvent event =
        Iterables.getOnlyElement(eventMonitor.changeMigrationFinishedEvents);
    assertThat(event.getDestinationEffects()).hasSize(2);
    DestinationEffect firstEffect = event.getDestinationEffects().get(0);
    assertThat(firstEffect.getSummary()).isEqualTo("Change created");
    assertThat(firstEffect.getOriginRefs().get(0).getRef()).isEqualTo("0");
    assertThat(firstEffect.getDestinationRef().getId()).isEqualTo("destination/1");
    DestinationEffect secondEffect = event.getDestinationEffects().get(1);
    assertThat(secondEffect.getSummary()).isEqualTo("New effect");
    assertThat(secondEffect.getType()).isEqualTo(Type.UPDATED);
    assertThat(secondEffect.getOriginRefs().get(0).getRef()).isEqualTo("1111");
    assertThat(secondEffect.getDestinationRef().getId()).isEqualTo("9999");
  }

  // Validates that the hook is executed when the workflow throws ValidationException, and that
  // the correct effect is populated
  @Test
  public void testOnFinishHook_error() throws Exception {
    options.testingOptions.destination = new RecordsProcessCallDestination() {
      @Override
      public Writer<Revision> newWriter(WriterContext writerContext) {
        return new RecordsProcessCallDestination.WriterImpl(false) {
          @Override
          public ImmutableList<DestinationEffect> write(TransformResult transformResult,
              Glob destinationFiles, Console console) throws ValidationException {
            throw new ValidationException("Validation exception!");
          }
        };
      }
    };
    verifyHookForException(ValidationException.class, Type.ERROR, "Validation exception!");
  }

  // Validates that the hook is executed when the workflow throws an exception != VE, and that
  // the correct effect is populated
  @Test
  public void testOnFinishHook_temporaryError() throws Exception {
    options.testingOptions.destination = new RecordsProcessCallDestination() {
      @Override
      public Writer<Revision> newWriter(WriterContext writerContext) {
        return new RecordsProcessCallDestination.WriterImpl(false) {
          @Override
          public ImmutableList<DestinationEffect> write(TransformResult transformResult,
              Glob destinationFiles, Console console) throws RepoException {
            throw new RepoException("Repo exception!");
          }
        };
      }
    };
    verifyHookForException(RepoException.class, Type.TEMPORARY_ERROR, "Repo exception!");
  }

  private <T extends Exception> void verifyHookForException(Class<T> expectedExceptionType,
      Type expectedEffectType, String expectedErrorMsg)
      throws IOException, ValidationException, RepoException {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def test(ctx):\n"
        + "  origin_refs = [ctx.origin.new_origin_ref('1111')]\n"
        + "  dest_ref = ctx.destination.new_destination_ref(ref = '9999', type = 'some_type')\n"
        + "  ctx.record_effect('New effect', origin_refs, dest_ref)\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [test]"
        + ")\n";
    try {
      loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
      fail();
    } catch (Exception expected) {
      if (!expectedExceptionType.isInstance(expected)) {
        throw expected;
      }
      assertThat(expected).hasMessageThat().contains(expectedErrorMsg);
    }

    assertThat(eventMonitor.changeMigrationFinishedEventCount()).isEqualTo(1);
    ChangeMigrationFinishedEvent event =
        Iterables.getOnlyElement(eventMonitor.changeMigrationFinishedEvents);
    assertThat(event.getDestinationEffects()).hasSize(2);
    DestinationEffect firstEffect = event.getDestinationEffects().get(0);
    assertThat(firstEffect.getType()).isEqualTo(expectedEffectType);
    assertThat(firstEffect.getSummary()).isEqualTo("Errors happened during the migration");
    assertThat(firstEffect.getErrors()).contains(expectedErrorMsg);
    // Effect from the hook is also created
    DestinationEffect secondEffect = event.getDestinationEffects().get(1);
    assertThat(secondEffect.getSummary()).isEqualTo("New effect");
    assertThat(secondEffect.getType()).isEqualTo(Type.UPDATED);
    assertThat(secondEffect.getOriginRefs().get(0).getRef()).isEqualTo("1111");
    assertThat(secondEffect.getDestinationRef().getId()).isEqualTo("9999");
  }

  @Test
  public void testOnFinishHookDoesNotReturnResult() throws Exception {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");

    String config = ""
        + "def _test_impl(ctx):\n"
        + "  return 'foo'\n"
        + "\n"
        + "def test(name, number = 2):\n"
        + "  return core.action(impl = _test_impl,\n"
        + "                               params = { 'name': name, 'number': number})\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [test('example'), test('other', 42), other]"
        + ")\n";
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () -> loadConfig(config).getMigration("default").run(workdir, ImmutableList.of()));
    assertThat(expected.getMessage()).contains("Error loading config file");
  }

  @Test
  public void testDryRun() throws Exception {
    options.general.dryRunMode = true;
    checkDryRun(ImmutableList.of());
  }

  @Test
  public void testDryRun_false() throws Exception {
    options.general.dryRunMode = false;
    ValidationException ignored =
        assertThrows(ValidationException.class, () -> checkDryRun(ImmutableList.of()));
    assertThat(ignored)
        .hasMessageThat()
        .contains("Error while executing the skylark transformation other");
  }

  @Test
  public void testDryRun_no_changes() throws Exception {
    options.general.dryRunMode = true;
    options.setForce(true);
    options.setLastRevision("0");
    checkDryRun(ImmutableList.of("0"));
  }

  private void checkDryRun(ImmutableList<String> refs)
      throws IOException, RepoException, ValidationException {
    origin.singleFileChange(0, "one commit", "foo.txt", "1");
    String config = ""
        + "def other(ctx):\n"
        + "  a = 3 + 'cannot add int to string!'\n"
        + "\n"
        + "core.workflow(\n"
        + "  name = 'default',\n"
        + "  origin = testing.origin(),\n"
        + "  destination = testing.destination(),\n"
        + "  transformations = [],\n"
        + "  authoring = " + authoring + ",\n"
        + "  after_migration = [other]"
        + ")\n";

    try {
      loadConfig(config).getMigration("default").run(workdir, refs);
    } finally {
      assertThat(destination.processed.get(0).isDryRun()).isEqualTo(options.general.dryRunMode);
    }
  }

  @Test
  public void testNonReversibleInsideGit() throws IOException, ValidationException, RepoException {
    origin.singleFileChange(0, "one commit", "foo.txt", "foo\nbar\n");

    GitRepository.newRepo(/*verbose*/ true, workdir, getGitEnv()).init();
    Path subdir = Files.createDirectory(workdir.resolve("subdir"));
    String config = ""
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    transformations = [core.replace('foo', 'bar')],\n"
        + "    authoring = " + authoring + ",\n"
        + "    reversible_check = True,\n"
        + ")\n";
    Migration workflow = loadConfig(config).getMigration("default");
    ValidationException e =
        assertThrows(ValidationException.class, () -> workflow.run(subdir, ImmutableList.of()));
    assertThat(e)
        .hasMessageThat()
        .contains("Cannot use 'reversible_check = True' because Copybara temporary directory");
  }

  @Test
  public void errorWritingFileThatDoesNotMatchDestinationFiles() throws Exception {
    destinationFiles = "glob(['foo*'], exclude = ['foo42'])";
    transformations = ImmutableList.of();

    origin.singleFileChange(/*timestamp=*/44, "one commit", "bar.txt", "1");
    Workflow<?, ?> workflow = skylarkWorkflow("default", SQUASH);

    NotADestinationFileException thrown =
        assertThrows(
            NotADestinationFileException.class,
            () -> workflow.run(workdir, ImmutableList.of(HEAD)));
    assertThat(thrown).hasMessageThat().contains("[bar.txt]");
  }

  @Test
  public void errorWritingMultipleFilesThatDoNotMatchDestinationFiles() throws Exception {
    // 'foo42' is like a file that is expected to be in the destination but not
    // originating from the origin repo (e.g. a metadata file specific to one repo type).
    // In this example, though, the file is copied from the origin, hence an error.
    destinationFiles = "glob(['foo*'], exclude = ['foo42'])";
    transformations = ImmutableList.of();

    Path originDir = Files.createTempDirectory("change1");
    Files.write(originDir.resolve("bar"), new byte[]{});
    Files.write(originDir.resolve("foo_included"), new byte[]{});
    Files.write(originDir.resolve("foo42"), new byte[]{});
    origin.addChange(/*timestamp=*/42, originDir, "change1", /*matchesGlob=*/true);
    Workflow<?, ?> workflow = skylarkWorkflow("default", SQUASH);

    NotADestinationFileException thrown =
        assertThrows(
            NotADestinationFileException.class,
            () -> workflow.run(workdir, ImmutableList.of(HEAD)));
    assertThat(thrown).hasMessageThat().contains("[bar, foo42]");
  }

  @Test
  public void checkForNonMatchingDestinationFilesAfterTransformations() throws Exception {
    destinationFiles = "glob(['foo*'])";
    options.workflowOptions.ignoreNoop = true;
    transformations = ImmutableList.of("core.move('bar.txt', 'foo53')");

    origin.singleFileChange(/*timestamp=*/44, "commit 1", "bar.txt", "1");
    origin.singleFileChange(/*timestamp=*/45, "commit 2", "foo42", "1");
    Workflow<?, ?> workflow = skylarkWorkflow("default", SQUASH);

    workflow.run(workdir, ImmutableList.of(HEAD));
  }

  @Test
  public void changeRequestWithFolderDestinationError()
      throws IOException, ValidationException, RepoException {
    origin.singleFileChange(/*timestamp=*/44, "commit 1", "bar.txt", "1");

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                loadConfig(
                    "core.workflow(\n"
                        + "    name = 'foo',\n"
                        + "    origin = testing.origin(),\n"
                        + "    destination = folder.destination(),\n"
                        + "    authoring = "
                        + authoring
                        + ",\n"
                        + "    mode = 'CHANGE_REQUEST',\n"
                        + ")\n")
                    .getMigration("foo")
                    .run(workdir, ImmutableList.of()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "'CHANGE_REQUEST' is incompatible with destinations that don't support" + " history");
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
    ValidationException e =
        assertThrows(
            ValidationException.class, () -> checkLastRevStatus(WorkflowMode.CHANGE_REQUEST));
    assertThat(e.getMessage())
        .isEqualTo("--check-last-rev-state is not compatible with CHANGE_REQUEST");
  }

  @Test
  public void sameVersionWithDiff() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    GitRepository destination =
        GitRepository.newRepo(/*verbose*/ true, destinationPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config =
        "core.workflow("
            + "    name = '"
            + "default"
            + "',"
            + String.format(
                "    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
                origin.getWorkTree(), primaryBranch)
            + "    destination = git.destination("
            + "        url = 'file://"
            + destination.getWorkTree()
            + "',\n"
            + "        push = '"
            + primaryBranch
            + "',\n"
            + "    ),\n"
            + "    authoring = "
            + authoring
            + ","
            + "    mode = '"
            + WorkflowMode.ITERATIVE
            + "',"
            + ")\n";

    Files.write(originPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
    String firstCommit = origin.parseRef("HEAD");

    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.initHistory = true;
    options.setForce(true);
    destination.simpleCommand("config", "receive.denyCurrentBranch", "ignore");
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of(firstCommit));

    destination.simpleCommand("checkout", destination.getPrimaryBranch());

    Files.write(destinationPath.resolve("bar.txt"), "a bar".getBytes(UTF_8));
    destination.add().files("bar.txt").run();
    destination.simpleCommand("commit", "-a", "-m", "something different");

    // Fails with empty change exception when importSaveVersion is false
    options.workflowOptions.importSameVersion = true;
    options.setForce(false);
    options.workflowOptions.initHistory = false;
    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of());
    // bar.txt present in because it was reverted
    assertThat(
            destination
                .log("HEAD")
                .withLimit(1)
                .includeFiles(true)
                .run()
                .iterator()
                .next()
                .getFiles())
        .contains("bar.txt");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void givenLastRevFlagInfoCommandUsesIt() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    GitRepository destination =
        GitRepository.newRepo(/*verbose*/ true, destinationPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow("
        + "    name = '" + "default" + "',"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
            origin.getWorkTree(),  primaryBranch)
        + "    destination = git.destination("
        + "        url = 'file://" + destination.getWorkTree() + "',\n"
        + "    ),\n"
        + "    authoring = " + authoring + ","
        + "    mode = '" + WorkflowMode.ITERATIVE + "',"
        + ")\n";

    Files.write(originPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
    origin.tag("1_0_0").run();
    String firstCommit = origin.parseRef("HEAD");

    Files.write(destinationPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    destination.add().files("foo.txt").run();
    destination.commit(
        "Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");

    Files.write(originPath.resolve("foo.txt"), "foo".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "change1");
    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.checkLastRevState = true;
    options.setLastRevision(firstCommit);

    Info<Revision> info = (Info<Revision>) loadConfig(config).getMigration("default").getInfo();
    verifyInfo(info, "change1\n");
    assertThat(info.originDescription().get("url"))
        .containsExactly("file://" + origin.getWorkTree());
    assertThat(info.destinationDescription().get("url"))
        .containsExactly("file://" + destination.getWorkTree());
    Iterable<MigrationReference<Revision>> refs = info.migrationReferences();
    assertThat(Iterables.getFirst(refs, null).getLastMigrated()
        .associatedLabel(GIT_DESCRIBE_ABBREV)).containsExactly("1_0_0");
  }

  @Test
  
  public void testToFolderFlag() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    GitRepository destination =
        GitRepository.newRepo(/*verbose*/ true, destinationPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    String config = "core.workflow("
        + "    name = '" + "default" + "',"
        + String.format("    origin = git.origin( url = 'file://%s', ref = '%s'),\n",
           origin.getWorkTree(),  primaryBranch)
        + "    destination = git.destination("
        + "        url = 'file://" + destination.getWorkTree() + "',\n"
        + "    ),\n"
        + "    authoring = " + authoring + ","
        + "    mode = '" + WorkflowMode.ITERATIVE + "',"
        + ")\n";

    Files.write(originPath.resolve("foo.txt"), "change".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
    String firstCommit = origin.parseRef("HEAD");

    options.workflowOptions.toFolder = true;
    options.general.squash = true;

    Path localFolder = Files.createTempDirectory("local_folder");
    options.folderDestination.localFolder = localFolder.toString();

    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.checkLastRevState = true;

    loadConfig(config).getMigration("default").
        run(Files.createTempDirectory("checkout"), ImmutableList.of());

    FileSubjects.assertThatPath(localFolder)
        .containsFile("foo.txt", "change")
        .containsNoMoreFiles();

    assertThrows(CannotResolveRevisionException.class, () -> destination.resolveReference("HEAD"));
  }


  @Test
  public void testHgOriginNoFlags() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    HgRepository origin = new HgRepository(originPath, true, DEFAULT_TIMEOUT).init();

    Path destinationPath = Files.createTempDirectory("destination");
    GitRepository destRepo = GitRepository
        .newBareRepo(destinationPath, getGitEnv(), true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .init();
    String primaryBranch = destRepo.getPrimaryBranch();

    String config = "core.workflow("
        + "    name = 'default',"
        + "    origin = hg.origin( url = 'file://" + origin.getHgDir() + "', ref = 'default'),\n"
        + "    destination = git.destination("
        + "                                  url = 'file://" + destRepo.getGitDir() + "',\n"
        + "                                  fetch = '" + primaryBranch + "',\n"
        + "                                  push = '" + primaryBranch + "',\n"
        + "    ),\n"
        + "    authoring = " + authoring + ","
        + "    mode = '" + WorkflowMode.ITERATIVE + "',"
        + ")\n";

    Files.write(originPath.resolve("foo.txt"), "testing foo".getBytes(UTF_8));
    origin.hg(originPath, "add", "foo.txt");
    origin.hg(originPath, "commit", "-m", "add foo");

    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.setWorkdirToRealTempDir();
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.workflowOptions.initHistory = true;
    Migration workflow = loadConfig(config).getMigration("default");
    workflow.run(workdir, ImmutableList.of());

    ImmutableList<GitLogEntry> destCommits = destRepo.log("HEAD").run();
    assertThat(destCommits).hasSize(1);
    assertThat(destCommits.get(0).getBody()).contains("add foo");

    Files.write(originPath.resolve("bar.txt"), "testing bar".getBytes(UTF_8));
    origin.hg(originPath, "add", "bar.txt");
    origin.hg(originPath, "commit", "-m", "add bar");

    options.workflowOptions.initHistory = false;
    workflow.run(workdir, ImmutableList.of());

    destCommits = destRepo.log("HEAD").run();
    assertThat(destCommits).hasSize(2);
    assertThat(destCommits.get(0).getBody()).contains("add bar");
    assertThat(destCommits.get(1).getBody()).contains("add foo");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testInfoSkipsChangesThatDontAffectOriginPaths() throws Exception {
    originFiles = "glob(['**'], exclude = ['folder/**'])";
    transformations = ImmutableList.of();

    Workflow<?, ?> workflow = iterativeWorkflow(/*previousRef*/ "0");

    origin.singleFileChange(0, "change1", "file.txt", "aaa");
    origin.singleFileChange(1, "change2", "file.txt", "bbb");
    origin.singleFileChange(2, "change3", "folder/foo.txt", "bar");
    origin.singleFileChange(3, "change4", "file.txt", "ccc");

    workflow.run(workdir, ImmutableList.of("1"));

    workflow = iterativeWorkflow(/*previousRef*/ null);

    verifyInfo((Info<Revision>) workflow.getInfo(), "change4");

    workflow.run(workdir, ImmutableList.of("3"));

    // Empty list of changes
    verifyInfo((Info<Revision>) workflow.getInfo());
  }

  @Test
  public void iterativeInitHistory() throws Exception {
    transformations = ImmutableList.of();
    origin.singleFileChange(0, "change 1", "file.txt", "a");
    origin.singleFileChange(1, "change 2", "file.txt", "b");
    origin.singleFileChange(2, "change 3", "file.txt", "c");
    origin.singleFileChange(3, "change 4", "file.txt", "d");
    origin.singleFileChange(4, "change 5", "file.txt", "e");
    options.workflowOptions.initHistory = true;
    skylarkWorkflow("default", ITERATIVE).run(workdir, ImmutableList.of("3"));
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("0", "1", "2", "3"));
  }

  @Test
  public void iterativeInitHistoryIgnored() throws Exception {
    options.workflowOptions.initHistory = true;

    transformations = ImmutableList.of();
    origin.singleFileChange(0, "change 1", "file.txt", "a");
    origin.singleFileChange(1, "change 2", "file.txt", "b");
    origin.singleFileChange(2, "change 3", "file.txt", "c");
    origin.singleFileChange(3, "change 4", "file.txt", "d");

    skylarkWorkflow("default", ITERATIVE).run(workdir, ImmutableList.of("3"));
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("0", "1", "2", "3"));

    origin.singleFileChange(4, "change 5", "file.txt", "e");
    origin.singleFileChange(5, "change 6", "file.txt", "f");

    skylarkWorkflow("default", ITERATIVE).run(workdir, ImmutableList.of("5"));
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("0", "1", "2", "3", "4", "5"));
    console().assertThat().onceInLog(MessageType.WARNING,
        ".*Ignoring --init-history because a previous imported revision '3' was found in "
            + "the destination.*");
  }

  @Test
  public void squashInitHistory() throws Exception {
    transformations = ImmutableList.of();
    origin.singleFileChange(0, "change 1", "file.txt", "a");
    origin.singleFileChange(1, "change 2", "file.txt", "b");
    origin.singleFileChange(2, "change 3", "file.txt", "c");
    origin.singleFileChange(3, "change 4", "file.txt", "d");
    origin.singleFileChange(4, "change 5", "file.txt", "e");
    options.workflowOptions.initHistory = true;
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("3"));
    assertThat(Lists.transform(destination.processed, input -> input.getOriginRef().asString()))
        .isEqualTo(Lists.newArrayList("3"));
  }

  @Test
  public void testSquashEmptyChangeWithForceTrue() throws Exception {
    originFiles = "glob(['foo/**'], exclude = ['copy.bara.sky', 'excluded/**'])";
    transformations = ImmutableList.of();
    origin.singleFileChange(0, "change 1", "bar/file.txt", "a");
    options.workflowOptions.initHistory = true;
    skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("0"));
    console().assertThat()
        .onceInLog(MessageType.WARNING,
            "No changes up to 0 match any origin_files. Migrating anyway because of --force");

  }

  @Test
  public void testSquashEmptyChangeWithForceFalse() throws Exception {
    originFiles = "glob(['foo/**'], exclude = ['copy.bara.sky', 'excluded/**'])";
    transformations = ImmutableList.of();
    options.workflowOptions.initHistory = true;
    origin.singleFileChange(0, "change 1", "bar/file.txt", "a");
    options.setForce(false);
    EmptyChangeException e =
        assertThrows(
            EmptyChangeException.class,
            () -> skylarkWorkflow("default", SQUASH).run(workdir, ImmutableList.of("0")));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "No changes up to 0 match any origin_files. "
                + "Use --force if you really want to run the migration anyway.");
  }

  @Test
  public void changeRequestInitHistory() throws Exception {
    options.workflowOptions.initHistory = true;
    options.general.enableEventMonitor("test", eventMonitor);
    options.general.setConsoleForTest(console());
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST)
                    .run(workdir, ImmutableList.of("")));
    assertThat(e.getMessage()).isEqualTo("--init-history is not compatible with CHANGE_REQUEST");
  }

  /**
   * Regression that test that when using git.origin with first_parent = False, if the first parent
   * of a merge is already imported and the merge is a no-op, we detect the import as
   * EmptyChangeException instead of detecting the non-first parent as the change to import
   */
  @Test
  public void testFirstParentAlreadyImportedInNoFirstParent() throws Exception {
    Path originPath = Files.createTempDirectory("origin");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();
    String primaryBranch = origin.getPrimaryBranch();

    options.setForce(false);
    options.workflowOptions.initHistory = true;

    String config = "core.workflow("
        + "    name = 'default',"
        + "    origin = git.origin( url = 'file://" + origin.getWorkTree() + "',\n"
        + "                         ref = '" + primaryBranch + "',\n"
        + "                         first_parent = False),\n"
        + "    destination = testing.destination(),\n"
        + "    authoring = " + authoring + ","
        + "    origin_files = glob(['included/**']),"
        + "    mode = '" + WorkflowMode.SQUASH + "',"
        + ")\n";

    Migration workflow = loadConfig(config).getMigration("default");

    addGitFile(originPath, origin, "included/foo.txt", "");
    GitRevision lastRev = commit(origin, "last_rev");
    workflow.run(workdir, ImmutableList.of());
    assertThat(destination.processed.get(0).getOriginRef()).isEqualTo(lastRev);

    origin.simpleCommand("checkout", "-b", "feature");
    addGitFile(originPath, origin, "included/foo.txt", "SHOULD NOT BE IN PRIMARY");
    commit(origin, "feature commit");
    origin.simpleCommand("revert", "HEAD");
    addGitFile(originPath, origin, "excluded/bar.txt", "don't migrate!");
    commit(origin, "excluded commit");
    origin.simpleCommand("checkout", primaryBranch);
    origin.simpleCommand("merge", "-s", "ours", "feature");
    EmptyChangeException e =
        assertThrows(EmptyChangeException.class, () -> workflow.run(workdir, ImmutableList.of()));
    assertThat(e)
        .hasMessageThat()
        .matches("No changes from " + lastRev.getSha1() + " up to .* match any origin_files.*");
  }

  private GitRevision commit(GitRepository origin, String msg)
      throws RepoException, ValidationException {
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), msg);
    return origin.resolveReference("HEAD");
  }

  private void addGitFile(Path originPath, GitRepository origin, String path, String content)
      throws IOException, RepoException {
    Files.createDirectories(originPath.resolve(path).getParent());
    Files.write(originPath.resolve(path), content.getBytes(UTF_8));
    origin.add().files(path).run();
  }

  private void checkLastRevStatus(WorkflowMode mode)
      throws IOException, RepoException, ValidationException {
    Path originPath = Files.createTempDirectory("origin");
    Path destinationWorkdir = Files.createTempDirectory("destination_workdir");
    GitRepository origin = GitRepository.newRepo(/*verbose*/ true, originPath, getGitEnv()).init();

    GitRepository destinationBare =
        newBareRepo(
            Files.createTempDirectory("destination"),
            getGitEnv(),
            /*verbose=*/ true,
            DEFAULT_TIMEOUT,
            /*noVerify=*/ false);
    destinationBare.init();
    GitRepository destination = destinationBare.withWorkTree(destinationWorkdir);
    String primaryBranch = destination.getPrimaryBranch();

    String config = "core.workflow("
        + "    name = '" + "default" + "',"
        + "    origin = git.origin("
        + "                        url = 'file://" + origin.getWorkTree() + "'\n,"
        + "                        ref = '" + primaryBranch + "'\n,"
        + "                        ),\n"
        + "    destination = git.destination("
        + "        url = 'file://" + destinationBare.getGitDir() + "',\n"
        + "        push = '" + primaryBranch + "',\n"
        + "        fetch = '" + primaryBranch + "'\n"
        + "    ),"
        + "    authoring = " + authoring + ","
        + "    mode = '" + mode + "',"
        + ")\n";

    Files.write(originPath.resolve("foo.txt"), "not important".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
    String firstCommit = origin.parseRef("HEAD");

    Files.write(originPath.resolve("foo.txt"), "foo".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "change1");

    options.setWorkdirToRealTempDir();
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    options.setHomeDir(Files.createTempDirectory("home").toString());
    options.gitDestination.committerName = "Foo";
    options.gitDestination.committerEmail = "foo@foo.com";
    options.workflowOptions.checkLastRevState = true;
    options.setLastRevision(firstCommit);

    loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD"));

    // Modify destination last commit
    Files.write(destinationWorkdir.resolve("foo.txt"), "foo_changed".getBytes(UTF_8));
    destination.add().files("foo.txt").run();
    destination.simpleCommand("commit", "--amend", "-a", "-C", "HEAD");

    Files.write(originPath.resolve("foo.txt"), "foo_origin_changed".getBytes(UTF_8));
    origin.add().files("foo.txt").run();
    origin.commit("Foo <foo@bara.com>", ZonedDateTime.now(ZoneId.systemDefault()), "change2");

    options.setForce(false);
    options.setLastRevision(null);
    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                loadConfig(config).getMigration("default").run(workdir, ImmutableList.of("HEAD")));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "didn't result in an empty change. This means that the result change of"
                + " that migration was modified ouside of Copybara");
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

  private void touchFile(Path base, String path, String content) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    Files.write(base.resolve(path), content.getBytes(UTF_8));
  }

  private Path writeFile(Path base, String path, String content) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), content.getBytes(UTF_8));
  }

  private void verifyInfo(Info<Revision> info, String... expectedChanges) {
    List<String> commitMessages =
        StreamSupport.stream(info.migrationReferences().spliterator(), false)
            .flatMap(
                revisionMigrationReference ->
                    revisionMigrationReference.getAvailableToMigrate().stream())
            .map(Change::getMessage)
            .collect(Collectors.toList());
    assertThat(commitMessages).containsExactly((Object[]) expectedChanges);
  }

  @Test
  public void testInvalidMigrationName() {
    skylark.evalFails(
        ""
            + "core.workflow(\n"
            + "    name = 'foo| bad;name',\n"
            + "    origin = folder.origin(),\n"
            + "    destination = folder.destination(),\n"
            + "    authoring = authoring.overwrite('Foo <foo@example.com>')\n"
            + ")\n",
        ".*Migration name 'foo[|] bad;name' doesn't conform to expected pattern.*");
  }
}
