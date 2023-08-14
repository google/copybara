/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.copybara.regenerate;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CommandEnv;
import com.google.copybara.ConfigLoader;
import com.google.copybara.Destination.PatchRegenerator;
import com.google.copybara.DestinationReader;
import com.google.copybara.ModuleSet;
import com.google.copybara.WriterContext;
import com.google.copybara.config.Config;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Revision;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RegenerateCmdTest {
  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;

  DummyOrigin origin;
  RecordsProcessCallDestination destination;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock public PatchRegenerator patchRegenerator;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();
  Path testRoot;
  Path workdir;
  Path destinationRoot;
  Path regenBaseline;
  Path regenTarget;

  @Before
  public void setup() throws IOException {
    options = new OptionsBuilder();
    options.regenerateOptions = new RegenerateOptions();

    testRoot = tempFolder.getRoot().toPath();
    workdir = testRoot.resolve("workdir");
    destinationRoot = testRoot.resolve("destination");

    destination =
        new RecordsProcessCallDestination() {
          @Override
          public Writer<Revision> newWriter(WriterContext writerContext) {
            return new WriterImpl(
                writerContext.isDryRun(), writerContext.getOriginalRevision().contextReference()) {
              @Override
              public Optional<PatchRegenerator> getPatchRegenerator(Console console) {
                return Optional.of(patchRegenerator);
              }

              @Override
              public DestinationReader getDestinationReader(
                  Console console, String baseline, Path workdir) {
                return new DestinationReader() {
                  @Override
                  public String readFile(String path) throws RepoException {
                    throw new RepoException("not implemented");
                  }

                  @Override
                  public void copyDestinationFiles(Glob glob, Object path)
                      throws RepoException, ValidationException {
                    throw new RepoException("not implemented");
                  }

                  @Override
                  public void copyDestinationFilesToDirectory(Glob glob, Path directory) {
                    Path sourceDirectory = destinationRoot.resolve(baseline);
                    try {
                      FileUtil.copyFilesRecursively(
                          sourceDirectory,
                          directory,
                          CopySymlinkStrategy.IGNORE_INVALID_SYMLINKS,
                          glob);
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  }

                  @Override
                  public boolean exists(String path) {
                    return false;
                  }
                };
              }
            };
          }
        };
    options.testingOptions.destination = destination;
    
    origin = new DummyOrigin();
    origin.addSimpleChange(0);
    options.testingOptions.origin = origin;

    skylark = new SkylarkTestExecutor(options);
  }

  private void setupBaseline(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    regenBaseline = destinationRoot.resolve(name);
  }

  private void setupTarget(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    regenTarget = destinationRoot.resolve(name);
  }

  @Test
  public void testCallsUpdateChange() throws Exception {
    setupBaseline("foo");
    setupTarget("bar");
    options.regenerateOptions.setRegenBaseline("foo");
    options.regenerateOptions.setRegenTarget("bar");

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(
                path -> {
                  return true;
                }),
            eq(Glob.ALL_FILES),
            eq("bar"));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testPatchFileIsGenerated() throws Exception {
    setupBaseline("foo");
    setupTarget("bar");
    options.regenerateOptions.setRegenBaseline("foo");
    options.regenerateOptions.setRegenTarget("bar");

    String testfile = "asdf.txt";
    Files.write(regenBaseline.resolve(testfile), "foo".getBytes());
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(path -> Files.exists(path.resolve("AUTOPATCH").resolve(testfile + ".patch"))),
            eq(Glob.ALL_FILES),
            eq("bar"));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testPatchFileNotGenerated() throws Exception {
    setupBaseline("foo");
    setupTarget("bar");
    options.regenerateOptions.setRegenBaseline("foo");
    options.regenerateOptions.setRegenTarget("bar");

    String testfile = "asdf.txt";
    // file contents are the same
    Files.write(regenBaseline.resolve(testfile), "foo".getBytes());
    Files.write(regenTarget.resolve(testfile), "foo".getBytes());

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(path -> !Files.exists(path.resolve("AUTOPATCH").resolve(testfile + ".patch"))),
            eq(Glob.ALL_FILES),
            eq("bar"));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testInferredBaselineused() throws Exception {
    setupBaseline("foo");
    setupTarget("bar");
    options.regenerateOptions.setRegenTarget("bar");

    when(patchRegenerator.inferRegenBaseline()).thenReturn(Optional.of("foo"));

    RegenerateCmd cmd = getCmd(getConfigString());

    // should not throw
    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testInferredTargetused() throws Exception {
    setupBaseline("foo");
    setupTarget("bar");
    options.regenerateOptions.setRegenBaseline("foo");

    when(patchRegenerator.inferRegenTarget()).thenReturn(Optional.of("bar"));

    RegenerateCmd cmd = getCmd(getConfigString());

    // should not throw
    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testRegenImportBaseline_generatesPatch() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    // different contents should generate diff
    origin.singleFileChange(0, "foo description", testfile, "foo");
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    options.regenerateOptions.setRegenTarget("bar");
    options.regenerateOptions.setRegenImportBaseline(true);
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(path -> Files.exists(path.resolve("AUTOPATCH").resolve(testfile + ".patch"))),
            eq(Glob.ALL_FILES),
            eq("bar"));
  }

  @Test
  public void testRegenImportBaseline_noDiff() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    // same contents should generate no diff
    origin.singleFileChange(0, "foo description", testfile, "bar");
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    options.regenerateOptions.setRegenTarget("bar");
    options.regenerateOptions.setRegenImportBaseline(true);
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(path -> !Files.exists(path.resolve("AUTOPATCH").resolve(testfile + ".patch"))),
            eq(Glob.ALL_FILES),
            eq("bar"));
  }

  @Test
  public void testNoLineNumbers_throws() {
    RegenerateCmd cmd = getCmd(getConfigStringWithStripLineNumbers());
    options.regenerateOptions.setRegenBaseline("foo");
    options.regenerateOptions.setRegenTarget("bar");

    assertThrows(
        ValidationException.class,
        () ->
            cmd.run(
                new CommandEnv(
                    workdir,
                    options.build(),
                    ImmutableList.of(testRoot.resolve("copy.bara.sky").toString()))));
  }

  @Test
  public void testNoLineNumbers_noThrowWhenUsingImportingBaseline() {
    options.regenerateOptions.setRegenImportBaseline(true);
    options.regenerateOptions.setRegenTarget("bar");

    RegenerateCmd cmd = getCmd(getConfigStringWithStripLineNumbers());

    assertThrows(
        ValidationException.class,
        () ->
            cmd.run(
                new CommandEnv(
                    workdir,
                    options.build(),
                    ImmutableList.of(testRoot.resolve("copy.bara.sky").toString()))));
  }

  @Test
  public void testMissingAutopatchConfig_throws() {
    options.regenerateOptions.setRegenImportBaseline(true);
    options.regenerateOptions.setRegenTarget("bar");

    RegenerateCmd cmd = getCmd(getConfigStringWithStripLineNumbers());

    assertThrows(
        ValidationException.class,
        () ->
            cmd.run(
                new CommandEnv(
                    workdir,
                    options.build(),
                    ImmutableList.of(testRoot.resolve("copy.bara.sky").toString()))));
  }

  private RegenerateCmd getCmd(String configString) {
    ModuleSet moduleSet = skylark.createModuleSet();
    return new RegenerateCmd(
        ((configPath, sourceRef) ->
            new ConfigLoader(
                moduleSet,
                skylark.createConfigFile("copy.bara.sky", configString),
                options.general.getStarlarkMode()) {
              @Override
              protected Config doLoadForRevision(Console console, Revision revision)
                  throws ValidationException {
                try {
                  return skylark.loadConfig(configPath);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            }));
  }

  private String getConfigString() {
    return "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    origin_files = glob(['**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'SQUASH',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + "    autopatch_config = core.autopatch_config(\n"
        + "      header = '# header',\n"
        + "      directory_prefix = ''\n,"
        + "      directory = 'AUTOPATCH',\n"
        + "      suffix = '.patch'\n"
        + "    ),\n"
        + ")";
  }

  private String getConfigStringWithStripLineNumbers() {
    return "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    origin_files = glob(['**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'SQUASH',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + "    autopatch_config = core.autopatch_config(\n"
        + "      header = '# header',\n"
        + "      directory_prefix = ''\n,"
        + "      directory = 'AUTOPATCH',\n"
        + "      suffix = '.patch',\n"
        + "      strip_file_names_and_line_numbers = True,\n"
        + "    ),\n"
        + ")";
  }

  private String getConfigStringWithMissingAutopatchConfig() {
    return "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    origin_files = glob(['**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'SQUASH',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + ")";
  }
}
