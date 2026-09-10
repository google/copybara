/*
 * Copyright (C) 2026 Google LLC
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

import com.google.copybara.config.Config;
import com.google.copybara.config.Migration;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WorkflowPrinterTest {

  private SkylarkTestExecutor skylark;

  @Before
  public void setUp() {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testPrintWorkflow_formatsSectionsAndUnwrapsTransformations() throws Exception {
    Config config =
        skylark.loadConfig(
            """
            core.workflow(
                name = 'default',
                origin = git.origin(
                    url = 'https://example.com/origin.git',
                    ref = 'main',
                ),
                origin_files = glob(['src/**']),
                destination = git.destination(
                    url = 'https://example.com/dest.git',
                    fetch = 'main',
                    push = 'main',
                ),
                destination_files = glob(['dest/**']),
                authoring = authoring.pass_thru('Default Author <default@example.com>'),
                transformations = [
                    core.replace(
                        before = 'foo',
                        after = 'bar',
                    ),
                    core.move('src', 'dest'),
                ],
            )
            """);
    Migration migration = config.getMigration("default");

    String output = WorkflowPrinter.print(migration);

    assertThat(output).contains("Workflow: default");
    assertThat(output).contains("Mode: SQUASH");
    assertThat(output).contains("Origin:");
    assertThat(output).contains("Origin files: glob(include = [\"src/**\"])");
    assertThat(output).contains("Destination:");
    assertThat(output).contains("Destination files: glob(include = [\"dest/**\"])");
    assertThat(output).contains("Authoring:");
    assertThat(output).contains("Transformations:");
    assertThat(output).contains("1. Replace");
    assertThat(output).contains("2. CopyOrMove");
  }

  @Test
  public void testPrintWorkflow_withAbstraction() throws Exception {
    Config config =
        skylark.loadConfig(
            """
            def my_test_library():
               core.workflow(
                  name = "my_test_workflow",
                  origin = git.origin(url = 'https://example.com/origin.git', ref = 'main'),
                  destination = git.destination(
                      url = 'https://example.com/dest.git',
                      fetch = 'main',
                      push = 'main',
                  ),
                  authoring = authoring.pass_thru('Default Author <default@example.com>'),
               )

            my_test_library()
            """);
    Migration migration = config.getMigration("my_test_workflow");

    String output = WorkflowPrinter.print(migration);

    assertThat(output).contains("Workflow: my_test_workflow");
  }
}
