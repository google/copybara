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

package com.google.copybara.transform.metadata;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.Author;
import com.google.copybara.Config;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.WorkflowMode;
import com.google.copybara.config.SkylarkParser;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.MapConfigFile;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.RecordsProcessCallDestination.ProcessedChange;
import com.google.copybara.testing.TestingModule;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetadataModuleTest {

  private static final Author ORIGINAL_AUTHOR = new Author("Foo Bar", "foo@bar.com");
  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  private DummyOrigin origin;
  private RecordsProcessCallDestination destination;
  private OptionsBuilder options;
  private String authoring;

  private SkylarkParser skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws IOException, ConfigValidationException {
    options = new OptionsBuilder();
    authoring = "authoring.overwrite('" + DEFAULT_AUTHOR + "')";
    workdir = Files.createTempDirectory("workdir");
    Files.createDirectories(workdir);
    origin = new DummyOrigin().setAuthor(ORIGINAL_AUTHOR);
    destination = new RecordsProcessCallDestination();
    options.setConsole(new TestingConsole());
    options.testingOptions.origin = origin;
    options.testingOptions.destination = destination;
    skylark = new SkylarkParser(
        ImmutableSet.of(TestingModule.class, MetadataModule.class));
  }

  private void passThruAuthoring() {
    authoring = "authoring.pass_thru('" + DEFAULT_AUTHOR + "')";
  }

  private Config loadConfig(String content)
      throws IOException, ConfigValidationException {
    return skylark.loadConfig(
        new MapConfigFile(ImmutableMap.of("copy.bara.sky", content.getBytes()), "copy.bara.sky"),
        options.build());
  }

  @Test
  public void testMessageTransformerForSquashCompact() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n',"
        + "  oldest_first = True,"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - 1 second commit by Foo Bar <foo@bar.com>\n"
            + "  - 2 third commit by Foo Baz <foo@baz.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashReverse() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n\\n'"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "\n"
            + "  - 2 third commit by Foo Baz <foo@baz.com>\n"
            + "  - 1 second commit by Foo Bar <foo@bar.com>\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testMessageTransformerForSquashExtended() throws Exception {
    runWorkflowForMessageTransform(WorkflowMode.SQUASH, ""
        + "metadata.squash_notes("
        + "  prefix = 'Importing foo project:\\n',"
        + "  compact = False\n"
        + ")");
    ProcessedChange change = Iterables.getOnlyElement(destination.processed);
    assertThat(change.getChangesSummary())
        .isEqualTo(""
            + "Importing foo project:\n"
            + "--\n"
            + "2 by Foo Baz <foo@baz.com>:\n"
            + "\n"
            + "third commit\n"
            + "\n"
            + "Extended text\n"
            + "--\n"
            + "1 by Foo Bar <foo@bar.com>:\n"
            + "\n"
            + "second commit\n"
            + "\n"
            + "Extended text\n");
    assertThat(change.getAuthor()).isEqualTo(DEFAULT_AUTHOR);
  }

  private void runWorkflowForMessageTransform(WorkflowMode mode, String... transforms)
      throws IOException, RepoException, ValidationException {
    origin.addSimpleChange(0, "first commit\n\nExtended text")
        .setAuthor(new Author("Foo Bar", "foo@bar.com"))
        .addSimpleChange(1, "second commit\n\nExtended text")
        .setAuthor(new Author("Foo Baz", "foo@baz.com"))
        .addSimpleChange(2, "third commit\n\nExtended text");

    options.setLastRevision("0");
    passThruAuthoring();

    Config config = loadConfig(""
        + "core.project( name = 'copybara_project')\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin =  testing.origin(),\n"
        + "    authoring = " + authoring + "\n,"
        + "    destination = testing.destination(),\n"
        + "    mode = '" + mode + "',\n"
        + "    transformations = [" + Joiner.on(", ").join(transforms) + "]\n"
        + ")\n");
    config.getActiveWorkflow().run(workdir, "2");
  }
}
