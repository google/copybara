/*
 * Copyright (C) 2023 Google LLC
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
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
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
import com.google.copybara.util.ConsistencyFile;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
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
  Path pristineBaseline;
  Path regenBaseline;
  Path regenTarget;

  public Map<String, String> env = System.getenv();

  private static final String CONSISTENCY_FILE_PATH = "test/test.bara.consistency";

  @Before
  public void setup() throws IOException {
    options = new OptionsBuilder();
    options.regenerateOptions = new RegenerateOptions();

    testRoot = tempFolder.getRoot().toPath();
    workdir = testRoot.resolve("workdir");
    destinationRoot = testRoot.resolve("destination");

    pristineBaseline = null;
    regenBaseline = null;
    regenTarget = null;

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
                  Console console, @Nullable String baseline, Path workdir) {
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
                    return Files.exists(destinationRoot.resolve(baseline).resolve(path));
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

    options.setEnvironment(env);

    skylark = new SkylarkTestExecutor(options);
  }

  // spoof a pristine baseline for generating baseline consistency files
  private void setupPristineBaseline(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    pristineBaseline = destinationRoot.resolve(name);
  }

  private void setupBaselineConsistencyFile() throws IOException, InsideGitDirException {
    ConsistencyFile consistencyFile;

    if (regenBaseline == null) {
      throw new RuntimeException("set up regen baseline before calling");
    }

    if (pristineBaseline != null) {
      consistencyFile =
          ConsistencyFile.generate(pristineBaseline, regenBaseline, Hashing.sha256(), env);
    } else {
      consistencyFile =
          ConsistencyFile.generate(regenBaseline, regenBaseline, Hashing.sha256(), env);
    }

    Files.createDirectories(regenBaseline.resolve(CONSISTENCY_FILE_PATH).getParent());
    Files.write(regenBaseline.resolve(CONSISTENCY_FILE_PATH), consistencyFile.toBytes());
  }

  private void setupBaseline(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    regenBaseline = destinationRoot.resolve(name);
    options.regenerateOptions.setRegenBaseline(name);
  }

  // need something in the origin when using an import baseline
  private void setupFooOriginImport() throws IOException {
    origin.singleFileChange(0, "foo description", "foo.txt", "foo");
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();
  }

  private void setupTarget(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    regenTarget = destinationRoot.resolve(name);
    options.regenerateOptions.setRegenTarget(name);
  }

  @Test
  public void testCallsUpdateChange() throws Exception {
    setupFooOriginImport();
    setupTarget("bar");

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
            any(),
            eq(Glob.ALL_FILES),
            eq("bar"));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testPatchFileIsGenerated() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    origin.singleFileChange(0, "foo description", testfile, "foo");
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();
    Files.write(regenTarget.resolve(testfile), "bar".getBytes(UTF_8));

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
  public void testAutoPatchFileNotGenerated_whenNoDiff() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    // file contents are the same
    origin.singleFileChange(0, "foo description", testfile, "foo");
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();
    Files.write(regenTarget.resolve(testfile), "foo".getBytes(UTF_8));

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
    setupFooOriginImport();
    setupTarget("bar");

    options.regenerateOptions.setRegenBaseline(null);
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
    setupFooOriginImport();
    setupTarget("bar");

    options.regenerateOptions.setRegenTarget(null);
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
  public void testRegenImportBaseline_initHistory() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";

    origin.singleFileChange(0, "foo description", testfile, "bar");
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    options.regenerateOptions.setRegenTarget("bar");
    options.regenerateOptions.setRegenImportBaseline(true);
    options.workflowOptions.initHistory = true;

    RegenerateCmd cmd = getCmd(getConfigString());

    // should not throw
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
            argThat(
                path -> {
                  try {
                    return Files.exists(path.resolve(testfile))
                        && Files.readString(path.resolve(testfile)).equals("bar");
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }),
            eq(Glob.ALL_FILES),
            eq("bar"));
  }

  @Test
  public void testNoLineNumbers_usesImportBaseline() throws Exception {
    RegenerateCmd cmd = getCmd(getConfigStringWithStripLineNumbers());
    setupTarget("bar");

    // set an origin file that contains a diff
    String testfile = "asdf.txt";
    origin.singleFileChange(0, "foo description", testfile, "foo");
    options.workflowOptions.lastRevision = origin.getLatestChange().asString();
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

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
  public void testMissingAutopatchConfig_throws() {
    options.regenerateOptions.setRegenImportBaseline(true);
    RegenerateCmd cmd = getCmd(getConfigStringWithMissingAutopatchConfig());

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
  public void testConsistencyFile_doesNotGenerate_disabled()
      throws IOException, ValidationException, RepoException, InsideGitDirException {
    setupFooOriginImport();
    setupTarget("bar");

    String testfile = "asdf.txt";
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    RegenerateCmd cmd = getCmd(getConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    ArgumentCaptor<Path> pathArg = ArgumentCaptor.forClass(Path.class);
    verify(patchRegenerator)
        .updateChange(
            any(),
            pathArg.capture(),
            eq(Glob.ALL_FILES),
            eq("bar"));
    assertThatPath(pathArg.getValue()).containsNoFiles(CONSISTENCY_FILE_PATH);

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testConsistencyFile_generatesFile()
      throws IOException, ValidationException, RepoException, InsideGitDirException {
    setupBaseline("foo");
    setupTarget("bar");

    String testfile = "asdf.txt";
    Files.write(regenBaseline.resolve(testfile), "foo".getBytes());
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    setupBaselineConsistencyFile();
    RegenerateCmd cmd = getCmd(getConsistencyFileConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    ArgumentCaptor<Path> pathArg = ArgumentCaptor.forClass(Path.class);
    verify(patchRegenerator)
        .updateChange(
            any(),
            pathArg.capture(),
            eq(Glob.ALL_FILES),
            eq("bar"));
    assertThatPath(pathArg.getValue()).containsFiles(CONSISTENCY_FILE_PATH);

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  @Test
  public void testConsistencyFile_capturesDiff()
      throws IOException, ValidationException, RepoException, InsideGitDirException {
    setupBaseline("foo");
    setupTarget("bar");

    String testfile = "asdf.txt";
    Files.write(regenBaseline.resolve(testfile), "foo".getBytes());
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    setupBaselineConsistencyFile();

    RegenerateCmd cmd = getCmd(getConsistencyFileConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    ArgumentCaptor<Path> pathArg = ArgumentCaptor.forClass(Path.class);
    verify(patchRegenerator)
        .updateChange(
            any(),
            pathArg.capture(),
            eq(Glob.ALL_FILES),
            eq("bar"));

    ConsistencyFile consistencyFile =
        ConsistencyFile.fromBytes(
            Files.readAllBytes(pathArg.getValue().resolve(CONSISTENCY_FILE_PATH)));

    assertThat(Files.readString(regenTarget.resolve(testfile))).isEqualTo("bar");
    consistencyFile.reversePatches(regenTarget, env);
    assertThat(Files.readString(regenTarget.resolve(testfile))).isEqualTo("foo");

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  // test existing consistency file is used to generate new patch
  @Test
  public void testRegenerate_consistencyFile_usesConsistencyFileBaseline() throws Exception {
    // generate a ConsistencyFile, make a new edit, use the directory
    // with the generated patch as baseline to regenerate again
    setupPristineBaseline("foofoo");
    setupBaseline("foo");
    setupTarget("bar");

    String testfile = "asdf.txt";
    Files.write(regenBaseline.resolve(testfile), "foo".getBytes());
    Files.write(pristineBaseline.resolve(testfile), "foofoo".getBytes(UTF_8));
    Files.write(regenTarget.resolve(testfile), "bar".getBytes());

    setupBaselineConsistencyFile();

    RegenerateCmd cmd = getCmd(getConsistencyFileConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));
    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    ArgumentCaptor<Path> pathArg = ArgumentCaptor.forClass(Path.class);
    verify(patchRegenerator)
        .updateChange(
            any(),
            pathArg.capture(),
            eq(Glob.ALL_FILES),
            eq("bar"));
    assertThatPath(pathArg.getValue()).containsFile(testfile, "bar");
    assertThatPath(pathArg.getValue()).containsFiles(CONSISTENCY_FILE_PATH);

    // setup second run
    setupBaseline("bar");
    setupTarget("foobar");

    // the directory uploaded from the previous run is the new baseline state
    clearDir(regenBaseline);
    FileUtil.copyFilesRecursively(pathArg.getValue(), regenBaseline,
        CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
    Files.write(regenTarget.resolve(testfile), "foobar".getBytes());

    clearDir(workdir);
    exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));
    verify(patchRegenerator)
        .updateChange(
            any(),
            pathArg.capture(),
            eq(Glob.ALL_FILES),
            eq("foobar"));

    assertThatPath(pathArg.getValue()).containsFile(testfile, "foobar");
    assertThatPath(pathArg.getValue()).containsFiles(CONSISTENCY_FILE_PATH);

    ConsistencyFile consistencyFile =
        ConsistencyFile.fromBytes(
            Files.readAllBytes(pathArg.getValue().resolve(CONSISTENCY_FILE_PATH)));
    consistencyFile.reversePatches(pathArg.getValue(), env);

    // reversing the diff should result in the pristine import
    assertThatPath(pathArg.getValue()).containsFile(testfile, "foofoo");

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
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
        + "    merge_import = core.merge_import_config(\n"
        + "      package_path = \"\"\n,"
        + "    ),\n"
        + "    autopatch_config = core.autopatch_config(\n"
        + "      header = '# header',\n"
        + "      directory_prefix = ''\n,"
        + "      directory = 'AUTOPATCH',\n"
        + "      suffix = '.patch'\n"
        + "    ),\n"
        + ")";
  }

  private String getConsistencyFileConfigString() {
    return "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = testing.origin(),\n"
        + "    origin_files = glob(['**']),\n"
        + "    destination = testing.destination(),\n"
        + "    mode = 'SQUASH',\n"
        + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
        + "    merge_import = core.merge_import_config(\n"
        + "      package_path = \"\"\n,"
        + "      use_consistency_file = True\n,"
        + "    ),\n"
        + "    consistency_file_path = \""
        + CONSISTENCY_FILE_PATH
        + "\",\n"
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
        + "    merge_import = core.merge_import_config(\n"
        + "      package_path = \"\"\n,"
        + "    ),\n"
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
        + "    merge_import = core.merge_import_config(\n"
        + "      package_path = \"\"\n,"
        + "      use_consistency_file = True\n"
        + "    ),\n"
        + "    consistency_file_path = \""
        + CONSISTENCY_FILE_PATH
        + "\",\n"
        + ")";
  }

  private void clearDir(Path dir) throws IOException {
    FileUtil.deleteRecursively(dir);
    Files.createDirectories(dir);
  }
}
