// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.Workflow.Yaml;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.Transformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class WorkflowTest {

  private static final String CONFIG_NAME = "copybara_project";
  private static final String PREFIX = "TRANSFORMED";
  private static final Author CONTRIBUTOR = new Author("Foo Bar", "foo@bar.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private Yaml yaml;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private Replace.Yaml replace = new Replace.Yaml();
  private Author.Yaml defaultAuthor = new Author.Yaml();
  private Authoring.Yaml authoring = new Authoring.Yaml();

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private ImmutableList<Transformation.Yaml> transformations =
      ImmutableList.<Transformation.Yaml>of(replace);
  private Path workdir;

  @Before
  public void setup() throws IOException, ConfigValidationException {
    yaml = new Yaml();
    options = new OptionsBuilder();
    workdir = options.general.getFileSystem().getPath("workdir");
    Files.createDirectories(workdir);
    origin = new DummyOrigin()
        .setAuthor(CONTRIBUTOR);
    destination = new RecordsProcessCallDestination();
    replace.setBefore("${line}");
    replace.setAfter(PREFIX + "${line}");
    replace.setRegexGroups(ImmutableMap.of("line", ".+"));
    defaultAuthor.setName("Copybara");
    defaultAuthor.setEmail("no-reply@google.com");
    authoring.setDefaultAuthor(defaultAuthor);
    authoring.setMode(AuthoringMappingMode.PASS_THRU);
    authoring.setWhitelist(ImmutableList.<String>of());
  }

  private Workflow workflow() throws ConfigValidationException, IOException, EnvironmentException {
    yaml.setOrigin(origin);
    yaml.setDestination(destination);
    yaml.setTransformations(transformations);
    yaml.setAuthoring(authoring);
    origin.addSimpleChange(/*timestamp*/ 42);
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private Workflow iterativeWorkflow(@Nullable String previousRef)
      throws ConfigValidationException, EnvironmentException {
    yaml.setOrigin(origin);
    yaml.setDestination(destination);
    yaml.setMode(WorkflowMode.ITERATIVE);
    yaml.setTransformations(transformations);
    yaml.setAuthoring(authoring);
    options.general = new GeneralOptions(options.general.getFileSystem(),
        options.general.isVerbose(), previousRef, options.general.console());
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  @Test
  public void defaultNameIsDefault() throws Exception {
    assertThat(workflow().getName()).isEqualTo("default");
  }

  @Test
  public void toStringIncludesName() throws Exception {
    yaml.setName("toStringIncludesName");
    assertThat(workflow().toString()).contains("toStringIncludesName");
  }

  @Test
  public void iterativeWorkflowTest() throws Exception {
    for (int timestamp = 0; timestamp < 61; timestamp++) {
      origin.addSimpleChange(timestamp);
    }
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"42");

    workflow.run(workdir, /*sourceRef=*/"50");
    Truth.assertThat(destination.processed).hasSize(8);
    int nextChange = 43;
    for (ProcessedChange change : destination.processed) {
      assertThat(change.getChangesSummary()).isEqualTo(nextChange + " change");
      String asString = Integer.toString(nextChange);
      assertThat(change.getOriginRef().asString()).isEqualTo(asString);
      assertThat(change.numFiles()).isEqualTo(1);
      assertThat(change.getContent("file.txt")).isEqualTo(PREFIX + asString);
      assertThat(change.getAuthor()).isEqualTo(CONTRIBUTOR);
      nextChange++;
    }

    workflow = iterativeWorkflow(null);
    workflow.run(workdir, /*sourceRef=*/"60");
    Truth.assertThat(destination.processed).hasSize(18);
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
    transformations = ImmutableList.of();
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
    assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
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
    Workflow workflow = workflow();
    origin.addSimpleChange(/*timestamp*/ 1);
    workflow.run(workdir, origin.getHead());
    assertThat(destination.processed).hasSize(1);
    assertThat(Iterables.getOnlyElement(destination.processed).getAuthor())
        .isEqualTo(DEFAULT_AUTHOR);
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
    String outsideFolder = "../../../file";
    Path file = workdir.resolve(outsideFolder);
    Files.createDirectories(file.getParent());
    Files.write(file, new byte[]{});

    yaml.setExcludedOriginPaths(ImmutableList.of(outsideFolder));
    try {
      workflow().run(workdir, origin.getHead());
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("is not relative to");
    }
    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles(outsideFolder);
  }

  @Test
  public void excludedOriginPathDoesntExcludeDirectories() throws Exception {
    yaml.setExcludedOriginPaths(ImmutableList.of("folder"));
    try {
      Workflow workflow = workflow();
      prepareOriginExcludes();
      workflow.run(workdir, origin.getHead());
      fail("Should fail because it could not delete anything.");
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("Nothing was deleted");
    }
    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles("folder/file.txt", "folder2/file.txt");
  }

  @Test
  public void excludedOriginPathRecursive() throws Exception {
    yaml.setExcludedOriginPaths(ImmutableList.of("folder/**"));
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, origin.getHead());

    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles("folder", "folder2")
        .containsNoFiles(
            "folder/file.txt", "folder/subfolder/file.txt", "folder/subfolder/file.java");
  }

  @Test
  public void excludedOriginRecursiveByType() throws Exception {
    yaml.setExcludedOriginPaths(ImmutableList.of("folder/**/*.java"));
    transformations = ImmutableList.of();
    Workflow workflow = workflow();
    prepareOriginExcludes();
    workflow.run(workdir, origin.getHead());

    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt")
        .containsNoFiles("folder/subfolder/file.java");
  }

  @Test
  public void excludeOriginPathIterative() throws Exception {
    yaml.setExcludedOriginPaths(ImmutableList.of("folder/**/*.java"));
    transformations = ImmutableList.of();
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
    yaml.setExcludedOriginPaths(ImmutableList.of("foo/**/bar.htm"));
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testDestinationExcludesToString() throws Exception {
    yaml.setExcludedDestinationPaths(ImmutableList.of("foo/**/bar.htm"));
    String string = workflow().toString();
    assertThat(string).contains("foo/**/bar.htm");
  }

  @Test
  public void testExcludedDestinationPathsPassedToDestination_iterative() throws Exception {
    yaml.setExcludedDestinationPaths(ImmutableList.of("foo", "bar/**"));
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
    yaml.setExcludedDestinationPaths(ImmutableList.of("foo", "bar/**"));
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
