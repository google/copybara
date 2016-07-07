// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.is;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination;
import com.google.copybara.Workflow;
import com.google.copybara.config.Config.Yaml;
import com.google.copybara.testing.AuthoringYamlBuilder;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@RunWith(JUnit4.class)
public class ConfigTest {

  private Yaml yaml;
  private AuthoringYamlBuilder authoring;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException, ConfigValidationException {
    yaml = new Yaml();
    authoring = new AuthoringYamlBuilder();
  }

  private Workflow.Yaml workflow(String name) throws ConfigValidationException {
    return workflow(name, new RecordsProcessCallDestination());
  }

  private Workflow.Yaml workflow(String name, Destination.Yaml destination)
      throws ConfigValidationException {
    Workflow.Yaml yaml = new Workflow.Yaml();
    if (name != null) {
      yaml.setName(name);
    }
    yaml.setOrigin(new DummyOrigin());
    yaml.setDestination(destination);
    yaml.setAuthoring(authoring.build());
    return yaml;
  }

  @Test
  public void twoMissingNamesCausesDuplicateNameException() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("More than one workflow with name: default. "
        + "Multiple workflows in the same config file require giving a name to the workflow. "
        + "Use 'name: myName' in one of the workflows.");
    yaml.setWorkflows(ImmutableList.of(workflow(/*name=*/null), workflow(/*name=*/null)));
  }

  @Test
  public void duplicateNameThrowsException() throws Exception {
    thrown.expect(ConfigValidationException.class);
    // Use is() to prevent matching a substring.
    thrown.expectMessage(is("More than one workflow with name: foo"));
    yaml.setWorkflows(ImmutableList.of(
        workflow(/*name=*/null), workflow(/*name=*/"foo"), workflow(/*name=*/"foo")));
  }

  @Test
  public void exceptionIfNoDefaultWorkflowPresent() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("No workflow with this name exists: default");
    yaml.setWorkflows(ImmutableList.of(workflow("foo")));
    yaml.setName("ConfigTest");
    yaml.withOptions(new OptionsBuilder().build());
  }

  @Test
  public void chooseWorkflowByName() throws Exception {
    RecordsProcessCallDestination destination = new RecordsProcessCallDestination();
    Workflow.Yaml chosen = workflow("chosen", destination);

    OptionsBuilder options = new OptionsBuilder();
    options.setWorkflowName("chosen");

    yaml.setName("ConfigTest");
    yaml.setWorkflows(ImmutableList.of(workflow("default"), chosen, workflow("other")));
    Config config = yaml.withOptions(options.build());
    assertThat(config.getActiveWorkflow().getDestination())
        .isSameAs(destination);
  }

  @Test
  public void requireProjectName() throws Exception {
    yaml.setWorkflows(ImmutableList.of(workflow("default")));
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("missing required field 'name'");
    yaml.withOptions(new OptionsBuilder().build());
  }

  @Test
  public void projectNameContainsInvalidChar() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("does not match regex [-_0-9a-zA-Z]+");
    yaml.setName("foo+bar");
  }

  @Test
  public void projectNameCanHaveUnderscoreDashOrAlphanumericChars() throws Exception {
    yaml.setName("foo_BAR-09");
    yaml.setWorkflows(ImmutableList.of(workflow("default")));
    Config config = yaml.withOptions(new OptionsBuilder().build());
    assertThat(config.getName()).isEqualTo("foo_BAR-09");
  }
}
