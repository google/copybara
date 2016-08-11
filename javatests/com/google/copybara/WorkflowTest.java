// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.Origin.OriginalAuthor;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.config.skylark.SkylarkParser;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyOriginalAuthor;
import com.google.copybara.testing.MapConfigFile;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.Message;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(malcon): Migrate this to Skylark
@RunWith(JUnit4.class)
public class WorkflowTest {

  private static final String PREFIX = "TRANSFORMED";
  private static final OriginalAuthor ORIGINAL_AUTHOR =
      new DummyOriginalAuthor("Foo Bar", "foo@bar.com");
  private static final Author CONTRIBUTOR = ORIGINAL_AUTHOR.resolve();
  private static final DummyOriginalAuthor NOT_WHITELISTED_ORIGINAL_AUTHOR =
      new DummyOriginalAuthor("Secret Coder", "secret@coder.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;
  private TestingConsole console;
  private SkylarkParser skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private String transformations;
  private Path workdir;
  private boolean includeReleaseNotes;
  private String excludedInOrigin;
  private String excludedInDestination;

  @Before
  public void setup() throws IOException, ConfigValidationException {
    options = new OptionsBuilder();
    authoring = "authoring.overwrite('" + DEFAULT_AUTHOR + "')";
    includeReleaseNotes = false;
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    origin = new DummyOrigin().setOriginalAuthor(ORIGINAL_AUTHOR);
    excludedInOrigin = "glob([])";
    excludedInDestination = "glob([])";
    destination = new RecordsProcessCallDestination();
    transformations = "[\n"
        + "        core.replace(\n"
        + "             before = '${linestart}${number}',\n"
        + "             after = '${linestart}" + PREFIX + "${number}',\n"
        + "             regex_groups = {\n"
        + "                 'number'    : '[0-9]+',\n"
        + "                 'linestart' : '^',\n"
        + "             },\n"
        + "             multiline = True,"
        + "        ),\n"
        + "    ]";
    console = new TestingConsole();
    options.setConsole(console);
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkParser(ImmutableSet.<Class<?>>of(TestingModule.class));
  }

  private Workflow workflow() throws ConfigValidationException, IOException, EnvironmentException {
    origin.addSimpleChange(/*timestamp*/ 42);
    return skylarkWorkflow("default", WorkflowMode.SQUASH);
  }

  private Workflow skylarkWorkflow(String name, WorkflowMode mode)
      throws IOException, ConfigValidationException, EnvironmentException {
    String config = ""
        + "core.project( name = 'copybara_project')\n"
        + "core.workflow(\n"
        + "    name = '" + name + "',\n"
        + "    origin = testing.origin(),\n"
        + "    destination = testing.destination(),\n"
        + "    exclude_in_origin = " + excludedInOrigin + ",\n"
        + "    exclude_in_destination = " + excludedInDestination + ",\n"
        + "    transformations = " + transformations + ",\n"
        + "    authoring = " + authoring + ",\n"
        + "    include_changelist_notes = " + (includeReleaseNotes ? "True" : "False") + ",\n"
        + "    mode = '" + mode + "',\n"
        + ")\n";
    System.err.println(config);
    return loadConfig(config).getActiveWorkflow();
  }

  private Workflow iterativeWorkflow(@Nullable String previousRef)
      throws ConfigValidationException, EnvironmentException, IOException {
    return iterativeWorkflow(previousRef, options.general.console());
  }

  private Workflow iterativeWorkflow(@Nullable String previousRef, Console console)
      throws ConfigValidationException, EnvironmentException, IOException {
    options.workflowOptions.lastRevision = previousRef;
    options.general = new GeneralOptions(
        options.general.getFileSystem(), options.general.isVerbose(), console);
    return skylarkWorkflow("default", WorkflowMode.ITERATIVE);
  }

  private Workflow changeRequestWorkflow(@Nullable String baseline)
      throws ConfigValidationException, EnvironmentException, IOException {
    options.workflowOptions.changeBaseline = baseline;
    return skylarkWorkflow("default", WorkflowMode.CHANGE_REQUEST);
  }

  @Test
  public void defaultNameIsDefault() throws Exception {
    assertThat(workflow().name()).isEqualTo("default");
  }

  @Test
  public void toStringIncludesName() throws Exception {
    options.workflowOptions.setWorkflowName("toStringIncludesName");
    assertThat(skylarkWorkflow("toStringIncludesName", WorkflowMode.SQUASH).toString())
        .contains("toStringIncludesName");
  }

  @Test
  public void iterativeWorkflowTest_defaultAuthoring() throws Exception {
    for (int timestamp = 0; timestamp < 61; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"42");

    workflow.run(workdir, /*sourceRef=*/"50");
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
    workflow.run(workdir, /*sourceRef=*/"60");
    assertThat(destination.processed).hasSize(18);
  }

  @Test
  public void iterativeWorkflowTest_whitelistAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setOriginalAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setOriginalAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    whiteListAuthoring();

    Workflow workflow = iterativeWorkflow("0");

    workflow.run(workdir, /*sourceRef=*/"0");
    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(CONTRIBUTOR);
    assertThat(destination.processed.get(1).getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  private void whiteListAuthoring() {
    authoring = ""
        + "authoring.whitelisted(\n"
        + "   default = '" + DEFAULT_AUTHOR + "',\n"
        + "   whitelist = ['" + ORIGINAL_AUTHOR.getId() + "'],\n"
        + ")";
  }

  @Test
  public void iterativeWorkflowTest_passThruAuthoring() throws Exception {
    origin
        .addSimpleChange(0)
        .setOriginalAuthor(ORIGINAL_AUTHOR)
        .addSimpleChange(1)
        .setOriginalAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
        .addSimpleChange(2);

    passThruAuthoring();

    iterativeWorkflow("0").run(workdir, /*sourceRef=*/"0");

    assertThat(destination.processed).hasSize(2);

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(CONTRIBUTOR);
    assertThat(destination.processed.get(1).getAuthor())
        .isEqualTo(NOT_WHITELISTED_ORIGINAL_AUTHOR.resolve());
  }

  private void passThruAuthoring() {
    authoring = "authoring.pass_thru('" + DEFAULT_AUTHOR + "')";
  }

  @Test
  public void iterativeWorkflowConfirmationHandlingTest() throws Exception {
    for (int timestamp = 0; timestamp < 10; timestamp++) {
      origin.addSimpleChange(timestamp);
    }

    TestingConsole testConsole = new TestingConsole()
        .respondYes()
        .respondNo();
    RecordsProcessCallDestination programmableDestination = new RecordsProcessCallDestination(
        WriterResult.OK, WriterResult.PROMPT_TO_CONTINUE, WriterResult.PROMPT_TO_CONTINUE);

    options.testingOptions.destination = programmableDestination;

    Workflow workflow = iterativeWorkflow(/*previousRef=*/"2", testConsole);

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
    thrown.expect(RepoException.class);
    thrown.expectMessage("Previous revision label DummyOrigin-RevId could not be found");
    workflow.run(workdir, /*sourceRef=*/"0");
  }

  @Test
  public void emptyTransformList() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 1);
    transformations = "[]";
    Workflow workflow = workflow();
    workflow.run(workdir, /*sourceRef=*/"0");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getContent("file.txt")).isEqualTo("0");
  }

  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    Workflow workflow = workflow();
    workflow.run(workdir, origin.getHead());

    long timestamp = destination.processed.get(0).getTimestamp();
    assertThat(timestamp).isEqualTo(42);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    Workflow workflow = workflow();
    String head = origin.getHead();
    workflow.run(workdir, head);
    assertThat(Files.readAllLines(workdir.resolve("checkout/file.txt"), StandardCharsets.UTF_8))
        .contains(PREFIX + head);
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    Workflow workflow = workflow();
    origin.addSimpleChange(/*timestamp*/ 42918273);
    workflow.run(workdir, origin.getHead());
    assertThat(destination.processed).hasSize(1);
    assertThat(destination.processed.get(0).getTimestamp())
        .isEqualTo(42918273);
  }

  @Test
  public void usesDefaultAuthorForSquash() throws Exception {
    // Squash always sets the default author for the commit but not in the release notes
    origin.addSimpleChange(/*timestamp*/ 1);
    options.workflowOptions.lastRevision = origin.getHead();
    origin.addSimpleChange(/*timestamp*/ 2);
    origin.addSimpleChange(/*timestamp*/ 3);
    includeReleaseNotes = true;

    Workflow workflow = workflow();

    workflow.run(workdir, origin.getHead());
    assertThat(destination.processed).hasSize(1);
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary()).contains(DEFAULT_AUTHOR.toString());
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void runsTransformations() throws Exception {
    workflow().run(workdir, origin.getHead());
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

    excludedInOrigin = "glob(['" + outsideFolder + "'])";

    try {
      workflow().run(workdir, origin.getHead());
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR, "(\n|.)*is not relative to(\n|.)*");
    }
    assertThatPath(workdir)
        .containsFiles(outsideFolder);
  }

  @Test
  public void excludedOriginPathDoesntExcludeDirectories() throws Exception {
    excludedInOrigin = "glob(['folder'])";
    try {
      Workflow workflow = workflow();
      prepareOriginExcludes();
      workflow.run(workdir, origin.getHead());
      fail("Should fail because it could not delete anything.");
    } catch (VoidOperationException e) {
      assertThat(e.getMessage()).contains("Nothing was deleted");
    }
    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder/file.txt", "folder2/file.txt");
  }

  @Test
  public void excludedOriginPathRecursive() throws Exception {
    excludedInOrigin = "glob(['folder/**'])";
    transformations = "[]";
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, origin.getHead());

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2")
        .containsNoFiles(
            "folder/file.txt", "folder/subfolder/file.txt", "folder/subfolder/file.java");
  }

  @Test
  public void excludedOriginRecursiveByType() throws Exception {
    excludedInOrigin = "glob(['folder/**/*.java'])";
    transformations = "[]";
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, origin.getHead());

    assertThatPath(workdir.resolve("checkout"))
        .containsFiles("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt")
        .containsNoFiles("folder/subfolder/file.java");
  }

  @Test
  public void excludedOriginNoopError() throws Exception {
    excludedInOrigin = "glob(['I_dont_exist'])";
    transformations = "[]";
    Workflow workflow = workflow();
    prepareOriginExcludes();
    thrown.expect(VoidOperationException.class);
    workflow.run(workdir, origin.getHead());
  }

  @Test
  public void excludedOriginNoopWarning() throws Exception {
    excludedInOrigin = "glob(['I_dont_exist'])";
    transformations = "[]";
    Workflow workflow = workflow();
    prepareOriginExcludes();
    options.workflowOptions.ignoreNoop = true;
    workflow.run(workdir, origin.getHead());
    console.assertThat().onceInLog(MessageType.WARNING,
        ".*Nothing was deleted in the workdir for exclude_in_origin.*");
  }

  @Test
  public void excludeOriginPathIterative() throws Exception {
    excludedInOrigin = "glob(['folder/**/*.java'])";
    transformations = "[]";
    prepareOriginExcludes();
    Workflow workflow = iterativeWorkflow(origin.getHead());
    prepareOriginExcludes();
    prepareOriginExcludes();
    prepareOriginExcludes();
    workflow.run(workdir, origin.getHead());
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
    excludedInOrigin = "glob(['foo/**/bar.htm'])";
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testDestinationExcludesToString() throws Exception {
    excludedInDestination = "glob(['foo/**/bar.htm'])";
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testExcludedDestinationPathsPassedToDestination_iterative() throws Exception {
    excludedInDestination = "glob(['foo', 'bar/**'])";
    origin.addSimpleChange(/*timestamp*/ 42);
    Workflow workflow = iterativeWorkflow(origin.getHead());
    origin.addSimpleChange(/*timestamp*/ 4242);
    workflow.run(workdir, origin.getHead());

    assertThat(destination.processed).hasSize(1);

    PathMatcher matcher = destination.processed.get(0).getExcludedDestinationPaths()
        .relativeTo(workdir);
    assertThat(matcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("foo/indir"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("bar/indir"))).isTrue();
  }

  @Test
  public void testExcludedDestinationPathsPassedToDestination_squash() throws Exception {
    excludedInDestination = "glob(['foo', 'bar/**'])";
    workflow().run(workdir, origin.getHead());

    assertThat(destination.processed).hasSize(1);

    PathMatcher matcher = destination.processed.get(0).getExcludedDestinationPaths()
        .relativeTo(workdir);
    assertThat(matcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("foo/indir"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("bar/indir"))).isTrue();
  }

  @Test
  public void invalidLastRevFlagGivesClearError() throws Exception {
    origin.addSimpleChange(/*timestamp*/ 42);

    Workflow workflow = iterativeWorkflow("deadbeef");
    thrown.expect(RepoException.class);
    thrown.expectMessage(
        "Could not resolve --last-rev flag. Please make sure it exists in the origin: deadbeef");
    workflow.run(workdir, origin.getHead());
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
    console.assertThat()
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

    assertThat(destination.processed.get(0).getAuthor()).isEqualTo(CONTRIBUTOR);
  }

  @Test
  public void changeRequest_whitelistAuthoring() throws Exception {
    origin
        .setOriginalAuthor(NOT_WHITELISTED_ORIGINAL_AUTHOR)
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
    console.assertThat()
        .onceInLog(MessageType.PROGRESS, ".*Checking that the transformations can be reverted");
  }

  @Test
  public void testNullAuthoring()
      throws ConfigValidationException, EnvironmentException, IOException {
    try {
      loadConfig(""
          + "core.project( name = 'copybara_project')\n"
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    origin = testing.origin(),\n"
          + "    destination = testing.destination(),\n"
          + ")\n");
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'authoring'.*");
    }
  }

  private Config loadConfig(String content)
      throws IOException, ConfigValidationException, EnvironmentException {
    return skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("copy.bara.sky", content.getBytes()), "copy.bara.sky"),
        options.build());
  }

  @Test
  public void testNullOrigin() throws ConfigValidationException, EnvironmentException, IOException {
    try {
      loadConfig(""
          + "core.project( name = 'copybara_project')\n"
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    authoring = " + authoring + "\n,"
          + "    destination = testing.destination(),\n"
          + ")\n");
    } catch (ConfigValidationException e) {
      for (Message message : console.getMessages()) {
        System.err.println(message);
      }
      console.assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'origin'.*");
    }
  }

  @Test
  public void testNullDestination()
      throws ConfigValidationException, EnvironmentException, IOException {
    try {
      loadConfig(""
          + "core.project( name = 'copybara_project')\n"
          + "core.workflow(\n"
          + "    name = 'foo',\n"
          + "    authoring = " + authoring + "\n,"
          + "    origin = testing.origin(),\n"
          + ")\n");
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR,
          ".*missing mandatory positional argument 'destination'.*");
    }
  }

  @Test
  public void nonReversibleButCheckReverseSet()
      throws IOException, EnvironmentException, ValidationException, RepoException {
    origin
        .singleFileChange(0, "one commit", "foo.txt", "1")
        .singleFileChange(1, "one commit", "test.txt", "1\nTRANSFORMED42");
    Workflow workflow = changeRequestWorkflow("0");
    try {
      workflow.run(workdir, "1");
      fail();
    } catch (NonReversibleValidationException e) {
      assertThat(e).hasMessage("Workflow 'default' is not reversible");
    }
    console.assertThat()
        .onceInLog(MessageType.PROGRESS, "Checking that the transformations can be reverted");
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
    origin.addChange(1, base, "excludes");
  }

  private Path touchFile(Path base, String path) throws IOException {
    Files.createDirectories(base.resolve(path).getParent());
    return Files.write(base.resolve(path), new byte[]{});
  }
}
