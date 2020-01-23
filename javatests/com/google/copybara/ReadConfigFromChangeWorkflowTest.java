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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import java.io.IOException;
import java.nio.file.Files;
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
    options.general.starlarkMode = StarlarkMode.STRICT.name();
    skylark = new SkylarkTestExecutor(options);
  }

  /**
   * A test that check that we can mutate the glob in iterative mode
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testWriterStateMaintained() throws Exception {
    options.workflowOptions.lastRevision = "0";
    String configCode = mutatingWorkflow("*");
    Config cfg = skylark.loadConfig(configCode);
    ConfigLoader constantConfigLoader =
        new ConfigLoader(
            skylark.createModuleSet(),
            skylark.createConfigFile("copy.bara.sky", configCode),
            options.general.getStarlarkMode()) {
          @Override
          protected Config doLoadForRevision(Console console, Revision revision)
              throws ValidationException {
            try {
              return skylark.loadConfig(mutatingWorkflow(revision.asString()));
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

    origin.singleFileChange(0, "base", "fileB", "b");
    origin.singleFileChange(1, "one", "file1", "b");
    origin.singleFileChange(2, "two", "file2", "b");
    origin.singleFileChange(3, "three", "file3", "b");

    wf.run(Files.createTempDirectory("workdir"), ImmutableList.of("3"));
    assertThat(destination.processed).hasSize(3);
    assertThat(destination.processed.get(0).getDestinationFiles().toString()).contains("file1");
    assertThat(destination.processed.get(0).getWorkdir()).containsExactly("file1", "b");
    assertThat(destination.processed.get(1).getDestinationFiles().toString()).contains("file2");
    assertThat(destination.processed.get(1).getWorkdir()).containsExactly("file2", "b");
    assertThat(destination.processed.get(2).getDestinationFiles().toString()).contains("file3");
    assertThat(destination.processed.get(2).getWorkdir()).containsExactly("file3", "b");
  }

  private String mutatingWorkflow(String suffix) {
    return "core.workflow("
        + "    name = 'default',"
        + "    origin = testing.origin(),"
        + "    mode = 'ITERATIVE',"
        + "    origin_files = glob(['file" + suffix + "']),"
        + "    destination_files = glob(['file" + suffix + "']),"
        + "    destination = testing.destination(),"
        + "    authoring = authoring.pass_thru('foo <foo@foo.com>')"
        + ")";
  }
}
