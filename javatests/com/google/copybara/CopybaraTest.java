// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsInvocationTransformation;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.transform.Transformation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class CopybaraTest {

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private Config.Yaml yaml;
  private OptionsBuilder options;

  @Before
  public void setup() throws Exception {
    origin = new DummyOrigin();
    destination = new RecordsProcessCallDestination();
    yaml = new Config.Yaml();
    yaml.setName("name");

    options = new OptionsBuilder()
        .setWorkdirToRealTempDir();
  }

  private Path workdir() {
    return options.general.getWorkdir();
  }

  private void setWorkflow(Transformation.Yaml... transformations)
      throws ConfigValidationException {
    Workflow.Yaml workflow = new SquashWorkflow.Yaml();
    workflow.setDestination(destination);
    workflow.setOrigin(origin);
    workflow.setTransformations(ImmutableList.copyOf(transformations));
    yaml.setWorkflows(ImmutableList.of(workflow));
  }

  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    setWorkflow();
    long beginTime = System.currentTimeMillis() / 1000;

    createCopybara().runForSourceRef(yaml.withOptions(options.build()), "some_sha1");

    long timestamp = destination.processTimestamps.get(0);
    assertThat(timestamp).isAtLeast(beginTime);
    assertThat(timestamp).isAtMost(System.currentTimeMillis() / 1000);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    setWorkflow();
    createCopybara().runForSourceRef(yaml.withOptions(options.build()), "some_sha1");
    assertThat(Files.readAllLines(workdir().resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    setWorkflow();
    origin.referenceToTimestamp.put("refname", (long) 42918273);
    createCopybara().runForSourceRef(yaml.withOptions(options.build()), "refname");
    assertThat(destination.processTimestamps.get(0))
        .isEqualTo(42918273);
  }

  @Test
  public void runsTransformations() throws Exception {
    RecordsInvocationTransformation transformation = new RecordsInvocationTransformation();
    setWorkflow(transformation);
    createCopybara().runForSourceRef(yaml.withOptions(options.build()), "some_sha1");
    assertThat(destination.processTimestamps).hasSize(1);
    assertThat(transformation.timesInvoked).isEqualTo(1);
  }

  private Copybara createCopybara() {
    return new Copybara(workdir());
  }
}
