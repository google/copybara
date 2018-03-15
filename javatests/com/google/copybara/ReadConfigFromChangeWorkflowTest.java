/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.Message;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ReadConfigFromChangeWorkflowTest {

  private SkylarkParser skylark;
  private OptionsBuilder options;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;

  @Before
  public void setup() {
    options = new OptionsBuilder();
    origin = new DummyOrigin();
    destination = new RecordsProcessCallDestination();
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkParser(ImmutableSet.of(
        Core.class,
        Authoring.Module.class,
        TestingModule.class));
  }

  /**
   * Validates that when running Copybara in ITERATIVE mode with read config from change,
   * we pass the old writer to the newWriter method so that writers can maintain state
   * despite new instances being requested.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testWriterStateMaintained() throws Exception {
    options.workflowOptions.lastRevision = "0";
    String configCode = "core.workflow("
        + "    name = 'default',"
        + "    origin = testing.origin(),"
        + "    mode = 'ITERATIVE',"
        + "    destination = testing.destination(),"
        + "    authoring = authoring.pass_thru('foo <foo@foo.com>')"
        + ")";
    Config cfg = loadConfig(configCode);
    ConfigLoader constantConfigLoader =
        new ConfigLoader<>(
            new ModuleSupplier() {
              @Override
              public ImmutableSet<Class<?>> getModules() {
                return ImmutableSet.of(Core.class, Authoring.Module.class, TestingModule.class);
              }
            },
            getConfig(configCode));
    ReadConfigFromChangeWorkflow<?, ?> wf = new ReadConfigFromChangeWorkflow<>(
        (Workflow) cfg.getMigration("default"),
        options.build(),
        revision -> constantConfigLoader, new ConfigValidator() {
      @Override
      public List<Message> validate(Config config, String migrationName) {
        return ImmutableList.of();
      }
    });

    origin.addSimpleChange(0);
    origin.addSimpleChange(1);
    origin.addSimpleChange(2);
    origin.addSimpleChange(3);

    wf.run(Files.createTempDirectory("workdir"), "3");
    // We are able to maintain state between invocations despite asking for new
    // writers.
    Truth.assertThat(destination.processed.stream()
                         .map(ProcessedChange::getState)
                         .collect(Collectors.toSet()))
        .containsExactly(0, 1, 2);

  }

  private Config loadConfig(String content) throws IOException, ValidationException {
    return skylark.loadConfig(getConfig(content), options.build(), options.general.console());
  }

  private static MapConfigFile getConfig(String content) {
    return new MapConfigFile(ImmutableMap.of(
        "copy.bara.sky", content.getBytes(StandardCharsets.UTF_8)), "copy.bara.sky");
  }
}
