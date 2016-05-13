// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.copybara.Workflow.Yaml;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsInvocationTransformation;
import com.google.copybara.testing.RecordsProcessCallDestination;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class WorkflowTest {

  private static final String CONFIG_NAME = "copybara_project";

  private Yaml yaml;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    yaml = new Yaml();
    origin = new DummyOrigin();
    destination = new RecordsProcessCallDestination();

    options = new OptionsBuilder()
        .setWorkdirToRealTempDir();
  }

  private Workflow workflow() throws ConfigValidationException {
    yaml.setOrigin(origin);
    yaml.setDestination(destination);
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private Workflow iterativeWorkflow(@Nullable String previousRef)
      throws ConfigValidationException {
    yaml.setOrigin(new DummyOrigin());
    yaml.setDestination(destination);
    yaml.setMode(WorkflowMode.ITERATIVE);
    options.general = new GeneralOptions(options.general.getWorkdir(),
        options.general.isVerbose(), previousRef, options.general.console());
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private Path workdir() {
    return options.general.getWorkdir();
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
    Workflow workflow = iterativeWorkflow(/*previousRef=*/"42");
    Path workdir = options.general.getWorkdir();
    workflow.run(workdir, /*sourceRef=*/"50");
    Truth.assertThat(destination.calls).isEqualTo(8);

    destination.calls = 0;

    workflow = iterativeWorkflow(null);
    workflow.run(workdir, /*sourceRef=*/"60");
    Truth.assertThat(destination.calls).isEqualTo(10);
  }

  @Test
  public void iterativeWorkflowNoPreviousRef() throws Exception {
    Workflow workflow = iterativeWorkflow(/*previousRef=*/null);
    Path workdir = options.general.getWorkdir();
    thrown.expect(RepoException.class);
    thrown.expectMessage("Previous revision label Dummy-RevId could not be found");
    workflow.run(workdir, /*sourceRef=*/"50");
  }

  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    long beginTime = System.currentTimeMillis() / 1000;

    workflow().run(workdir(), "some_sha1");

    long timestamp = destination.processTimestamps.get(0);
    assertThat(timestamp).isAtLeast(beginTime);
    assertThat(timestamp).isAtMost(System.currentTimeMillis() / 1000);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    workflow().run(workdir(), "some_sha1");
    assertThat(Files.readAllLines(workdir().resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    origin.referenceToTimestamp.put("refname", (long) 42918273);
    workflow().run(workdir(), "refname");
    assertThat(destination.processTimestamps.get(0))
        .isEqualTo(42918273);
  }

  @Test
  public void runsTransformations() throws Exception {
    RecordsInvocationTransformation transformation = new RecordsInvocationTransformation();
    yaml.setTransformations(ImmutableList.of(transformation));
    workflow().run(workdir(), "some_sha1");
    assertThat(destination.processTimestamps).hasSize(1);
    assertThat(transformation.timesInvoked).isEqualTo(1);
  }
}
