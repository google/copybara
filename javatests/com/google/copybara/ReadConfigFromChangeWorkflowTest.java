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
import com.google.common.truth.Truth;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReadConfigFromChangeWorkflowTest {

  private OptionsBuilder options;
  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() {
    options = new OptionsBuilder();
    origin = new DummyOrigin();
    destination = new RecordsProcessCallDestination();
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkTestExecutor(options);
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
    Config cfg = skylark.loadConfig(configCode);
    ConfigLoader constantConfigLoader =
        new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", configCode)) {
          @Override
          public Config loadForRevision(Console console, Revision revision)
              throws ValidationException {
            try {
              return super.load(console);
            } catch (IOException e) {
              throw new AssertionError("Should not fail", e);
            }
          }
        };
    ReadConfigFromChangeWorkflow<?, ?> wf = new ReadConfigFromChangeWorkflow<>(
        (Workflow) cfg.getMigration("default"),
        options.build(),
        constantConfigLoader, new ConfigValidator() {
      @Override
      public ValidationResult validate(Config config, String migrationName) {
        return ValidationResult.EMPTY;
      }
    });

    origin.addSimpleChange(0);
    origin.addSimpleChange(1);
    origin.addSimpleChange(2);
    origin.addSimpleChange(3);

    wf.run(Files.createTempDirectory("workdir"), ImmutableList.of("3"));
    // We are able to maintain state between invocations despite asking for new
    // writers.
    Truth.assertThat(destination.processed.stream()
                         .map(ProcessedChange::getState)
                         .collect(Collectors.toSet()))
        .containsExactly(0, 1, 2);
  }
}
