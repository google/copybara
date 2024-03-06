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
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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

  public Map<String, String> env = System.getenv();

  private static final String CONSISTENCY_FILE_PATH = "test/test.bara.consistency";

  @Before
  public void setup() throws Exception {
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
  }

  // generate and write the consistency file from the regen baseline and pristine baseline directory
  // contents
  private void setupBaselineConsistencyFile(String pristine, String baseline)
      throws IOException, InsideGitDirException {
    ConsistencyFile consistencyFile;

    if (!Files.exists(destinationRoot.resolve(baseline))) {
      throw new RuntimeException("set up regen baseline before calling");
    }

    consistencyFile =
        ConsistencyFile.generate(
            destinationRoot.resolve(pristine),
            destinationRoot.resolve(baseline),
            Hashing.sha256(),
            env);

    writeDestination(baseline, CONSISTENCY_FILE_PATH, consistencyFile.toBytes());
  }

  private void setupBaseline(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    options.regenerateOptions.setRegenBaseline(name);
  }

  // need something in the origin when using an import baseline
  private void setupFooOriginImport() throws IOException {
    origin.singleFileChange(0, "foo description", "foo.txt", "foo");
  }

  private void setupTarget(String name) throws IOException {
    Files.createDirectories(destinationRoot.resolve(name));
    options.regenerateOptions.setRegenTarget(name);
  }

  @Test
  public void testCallsUpdateChange() throws Exception {
    setupFooOriginImport();
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));
    setupTarget("bar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

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
  public void testGeneratesPatches() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    origin.singleFileChange(0, "foo description", testfile, "foo");
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));
    writeDestination("bar", testfile, "bar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

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
  public void testImportBaseline_noSourceRef_inferredBaseline_usesInferredBaseline()
      throws Exception {
    origin.singleFileChange(0, "foo description", "test.txt", "foo");

    // infer the first change as the baseline
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    origin.singleFileChange(1, "bar description", "test.txt", "bar");

    setupTarget("bar");
    writeDestination("bar", "test.txt", "destinationbar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

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
                    // patch is against the inferred reference
                    assertThatPath(path)
                        .containsFileMatching(
                            "AUTOPATCH/test.txt.patch",
                            Pattern.compile(".*-foo\n.*", Pattern.DOTALL));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return true;
                }),
            any(),
            eq("bar"));
  }

  @Test
  public void testImportBaseline_noSourceRef_noInferredBaseline_usesHead() throws Exception {
    origin.singleFileChange(0, "foo description", "test.txt", "foo");
    origin.singleFileChange(1, "bar description", "test.txt", "bar");

    when(patchRegenerator.inferImportBaseline(any(), any())).thenReturn(Optional.empty());

    setupTarget("bar");
    writeDestination("bar", "test.txt", "destinationbar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

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
                    // patch is against the latest reference
                    assertThatPath(path)
                        .containsFileMatching(
                            "AUTOPATCH/test.txt.patch",
                            Pattern.compile(".*-bar\n.*", Pattern.DOTALL));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return true;
                }),
            any(),
            eq("bar"));
  }

  @Test
  public void testImportBaseline_noSourceRef_noInferredBaseline_emitsWarning() throws Exception {
    origin.singleFileChange(0, "foo description", "test.txt", "foo");

    when(patchRegenerator.inferImportBaseline(any(), any())).thenReturn(Optional.empty());

    setupTarget("bar");
    writeDestination("bar", "test.txt", "destinationbar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    ((TestingConsole) options.general.console())
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "Regenerate was unable to detect the import baseline reference nor was a reference"
                + " passed in.*");
  }

  @Test
  public void testImportBaseline_sourceRef_inferredBaseline_usesSourceRef() throws Exception {
    origin.singleFileChange(0, "foo description", "test.txt", "foo");
    String firstChangeRef = origin.getLatestChange().asString();

    origin.singleFileChange(1, "bar description", "test.txt", "bar");
    // infer the second change as the baseline
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    setupTarget("bar");
    writeDestination("bar", "test.txt", "destinationbar");

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                // pass in the first change as the source ref
                ImmutableList.of(
                    testRoot.resolve("copy.bara.sky").toString(), "default", firstChangeRef)));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(
                path -> {
                  try {
                    // patch is against the source reference
                    assertThatPath(path)
                        .containsFileMatching(
                            "AUTOPATCH/test.txt.patch",
                            Pattern.compile(".*-foo\n.*", Pattern.DOTALL));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                  return true;
                }),
            any(),
            eq("bar"));
  }

  @Test
  public void testRegenerate_noOptionTarget_inferredTarget_usesInferredTarget() throws Exception {
    origin.singleFileChange(1, "foo change", "test.txt", "foo");
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    setupTarget("bar");

    // clear target from options, only pass it in through infer
    options.regenerateOptions.setRegenTarget(null);
    when(patchRegenerator.inferRegenTarget()).thenReturn(Optional.of("bar"));

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    // should not throw
    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
    verify(patchRegenerator).updateChange(any(), any(), any(), eq("bar"));
  }

  @Test
  public void testRegenerate_optionTarget_inferredTarget_usesOptionTarget() throws Exception {
    origin.singleFileChange(1, "foo change", "test.txt", "foo");
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    setupTarget("bar");

    when(patchRegenerator.inferRegenTarget()).thenReturn(Optional.of("infertarget"));

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    // should not throw
    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
    // target from options, not from infer
    verify(patchRegenerator).updateChange(any(), any(), any(), eq("bar"));
  }

  @Test
  public void testRegenerate_noOptionTarget_noInferredTarget_throws() throws Exception {
    origin.singleFileChange(1, "foo change", "test.txt", "foo");
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    setupTarget("bar");
    options.regenerateOptions.setRegenTarget(null);

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    Throwable t =
        assertThrows(
            ValidationException.class,
            () ->
                cmd.run(
                    new CommandEnv(
                        workdir,
                        options.build(),
                        ImmutableList.of(testRoot.resolve("copy.bara.sky").toString()))));
    assertThat(t)
        .hasMessageThat()
        .contains("Regen target was neither supplied nor able to be inferred.");
  }

  @Test
  public void testRegenerate_generatesPatch() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    // different contents should generate diff
    origin.singleFileChange(0, "foo description", testfile, "foo");
    writeDestination("bar", testfile, "bar");

    options.regenerateOptions.setRegenImportBaseline(true);
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

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
  public void testNoDiff_generatesNoPatches() throws Exception {
    setupTarget("bar");

    String testfile = "asdf.txt";
    // same contents should generate no diff
    origin.singleFileChange(0, "foo description", testfile, "bar");
    writeDestination("bar", testfile, "bar");

    options.regenerateOptions.setRegenImportBaseline(true);
    when(patchRegenerator.inferImportBaseline(any(), any()))
        .thenReturn(Optional.of(origin.getLatestChange().asString()));

    RegenerateCmd cmd = getCmd(getImportAutopatchesConfigString());

    ExitCode exitCode =
        cmd.run(
            new CommandEnv(
                workdir,
                options.build(),
                ImmutableList.of(testRoot.resolve("copy.bara.sky").toString())));

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);

    // check that no patches were generated
    verify(patchRegenerator)
        .updateChange(
            any(),
            argThat(path -> !Files.exists(path.resolve("AUTOPATCH").resolve(testfile + ".patch"))),
            eq(Glob.ALL_FILES),
            eq("bar"));
  }

  @Test
  public void testConsistencyFile_generatesFile()
      throws IOException, ValidationException, RepoException, InsideGitDirException {
    setupBaseline("foo");
    setupTarget("bar");

    String testfile = "asdf.txt";
    writeDestination("foo", testfile, "foo");
    writeDestination("bar", testfile, "bar");

    setupBaselineConsistencyFile("foo", "foo");
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
    writeDestination("foo", testfile, "foo");
    writeDestination("bar", testfile, "bar");

    setupBaselineConsistencyFile("foo", "foo");

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

    assertThat(Files.readString(destinationPath("bar").resolve(testfile))).isEqualTo("bar");
    consistencyFile.reversePatches(destinationPath("bar"), env);
    assertThat(Files.readString(destinationPath("bar").resolve(testfile))).isEqualTo("foo");

    assertThat(exitCode).isEqualTo(ExitCode.SUCCESS);
  }

  // test existing consistency file is used to generate new patch
  @Test
  public void testConsistencyFile_usesConsistencyFileBaseline() throws Exception {
    // generate a ConsistencyFile, make a new edit, use the directory
    // with the generated patch as baseline to regenerate again
    setupPristineBaseline("foofoo");
    setupBaseline("foo");
    setupTarget("bar");

    String testfile = "asdf.txt";
    writeDestination("foo", testfile, "foo");
    writeDestination("foofoo", testfile, "foofoo");
    writeDestination("bar", testfile, "bar");

    setupBaselineConsistencyFile("foofoo", "foo");

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
    clearDir(destinationPath("bar"));
    FileUtil.copyFilesRecursively(
        pathArg.getValue(), destinationPath("bar"), CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
    writeDestination("foobar", testfile, "foobar");

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

  @Test
  public void testConsistencyFile_optionBaseline_inferredBaseline_usesOptionBaseline()
      throws Exception {
    setupPristineBaseline("pristine");
    setupBaseline("foo");
    setupBaseline("baseline");
    setupTarget("target");

    when(patchRegenerator.inferRegenBaseline()).thenReturn(Optional.of("foo"));

    writeDestination("pristine", "test.txt", "original");
    writeDestination("baseline", "test.txt", "oldpatch");
    writeDestination("foo", "test.txt", "badpatch");
    writeDestination("target", "test.txt", "newpatch");

    // after we run, we will check that the restored baseline is from the pristine directory
    // and not foo
    setupBaselineConsistencyFile("pristine", "baseline");
    setupBaselineConsistencyFile("foo", "foo");

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
        .updateChange(any(), pathArg.capture(), eq(Glob.ALL_FILES), eq("target"));

    assertThatPath(pathArg.getValue()).containsFile("test.txt", "newpatch");
    assertThatPath(pathArg.getValue()).containsFiles(CONSISTENCY_FILE_PATH);

    ConsistencyFile consistencyFile =
        ConsistencyFile.fromBytes(
            Files.readAllBytes(pathArg.getValue().resolve(CONSISTENCY_FILE_PATH)));
    consistencyFile.reversePatches(pathArg.getValue(), env);

    // reversing the diff should result in the pristine baseline, not the foo one
    assertThatPath(pathArg.getValue()).containsFile("test.txt", "original");
  }

  @Test
  public void testConsistencyFile_noOptionBaseline_inferredBaseline_usesInferredBaseline()
      throws Exception {
    setupPristineBaseline("pristine");
    setupBaseline("foo");
    setupBaseline("baseline");
    options.regenerateOptions.setRegenBaseline(null);
    setupTarget("target");

    when(patchRegenerator.inferRegenBaseline()).thenReturn(Optional.of("baseline"));

    writeDestination("pristine", "test.txt", "original");
    writeDestination("baseline", "test.txt", "oldpatch");
    writeDestination("foo", "test.txt", "badpatch");
    writeDestination("target", "test.txt", "newpatch");

    // after we run, we will check that the restored baseline is from the pristine directory
    // and not foo
    setupBaselineConsistencyFile("pristine", "baseline");
    setupBaselineConsistencyFile("foo", "foo");

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
        .updateChange(any(), pathArg.capture(), eq(Glob.ALL_FILES), eq("target"));

    assertThatPath(pathArg.getValue()).containsFile("test.txt", "newpatch");
    assertThatPath(pathArg.getValue()).containsFiles(CONSISTENCY_FILE_PATH);

    ConsistencyFile consistencyFile =
        ConsistencyFile.fromBytes(
            Files.readAllBytes(pathArg.getValue().resolve(CONSISTENCY_FILE_PATH)));
    consistencyFile.reversePatches(pathArg.getValue(), env);

    // reversing the diff should result in the pristine baseline, not the foo one
    assertThatPath(pathArg.getValue()).containsFile("test.txt", "original");
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

  private String getImportAutopatchesConfigString() {
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

  private void clearDir(Path dir) throws IOException {
    FileUtil.deleteRecursively(dir);
    Files.createDirectories(dir);
  }

  private void writeDestination(String name, String filepath, String contents) throws IOException {
    Path writePath = destinationRoot.resolve(name).resolve(filepath);
    Files.createDirectories(writePath.getParent());
    Files.writeString(writePath, contents);
  }

  private void writeDestination(String name, String filepath, byte[] bytes) throws IOException {
    Path writePath = destinationRoot.resolve(name).resolve(filepath);
    Files.createDirectories(writePath.getParent());
    Files.write(writePath, bytes);
  }

  private Path destinationPath(String name) {
    return destinationRoot.resolve(name);
  }
}
