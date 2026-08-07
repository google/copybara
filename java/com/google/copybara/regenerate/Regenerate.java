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

import static com.google.copybara.exception.ValidationException.checkCondition;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.MoreFiles;
import com.google.common.primitives.Bytes;
import com.google.copybara.AutoPatchfileConfiguration;
import com.google.copybara.Destination.PatchRegenerator;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationReader;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.Workflow;
import com.google.copybara.WorkflowMode;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.WorkflowRunHelper;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.monitor.EventMonitor.ChangeMigrationFinishedEvent;
import com.google.copybara.revision.Revision;
import com.google.copybara.transform.patch.PatchingOptions;
import com.google.copybara.util.AutoPatchUtil;
import com.google.copybara.util.ConsistencyFile;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Regenerate contains the implementation of the logic to checkout the correct versions of code and
 * calling the helper classes to diff and upload the contents.
 */
public class Regenerate<O extends Revision, D extends Revision> {

  private static final Pattern LINE_SPLITTER = Pattern.compile("\r?\n");

  Console console;
  @Nullable AutoPatchfileConfiguration autoPatchfileConfiguration;
  Workflow<O, D> workflow;
  Path workdir;
  PatchingOptions patchingOptions;
  GeneralOptions generalOptions;
  WorkflowOptions workflowOptions;
  RegenerateOptions regenerateOptions;
  @Nullable String sourceRef;

  public static Regenerate<? extends Revision, ? extends Revision> newRegenerate(
      Workflow<? extends Revision, ? extends Revision> workflow,
      Path workdir,
      PatchingOptions patchingOptions,
      GeneralOptions generalOptions,
      WorkflowOptions workflowOptions,
      RegenerateOptions regenerateOptions,
      @Nullable String sourceRef) {
    return new Regenerate<>(
        workflow,
        workdir,
        patchingOptions,
        generalOptions,
        workflowOptions,
        regenerateOptions,
        sourceRef);
  }

  public Regenerate(
      Workflow<O, D> workflow,
      Path workdir,
      PatchingOptions patchingOptions,
      GeneralOptions generalOptions,
      WorkflowOptions workflowOptions,
      RegenerateOptions regenerateOptions,
      @Nullable String sourceRef) {
    this.workflow = workflow;
    this.workdir = workdir;
    this.patchingOptions = patchingOptions;
    this.generalOptions = generalOptions;
    this.workflowOptions = workflowOptions;
    this.regenerateOptions = regenerateOptions;
    this.console = generalOptions.console();
    this.sourceRef = sourceRef;
    this.autoPatchfileConfiguration = workflow.getAutoPatchfileConfiguration();
  }

  @VisibleForTesting
  public void regenerate() throws ValidationException, RepoException, IOException {

    Writer<D> destinationWriter =
        workflow
            .getDestination()
            .newWriter(
                new WriterContext(
                    workflow.getName(),
                    workflowOptions.workflowIdentityUser,
                    generalOptions.dryRunMode,
                    workflow.getOrigin().resolve(null),
                    workflow.getDestinationFiles().roots()));
    PatchRegenerator patchRegenerator =
        destinationWriter
            .getPatchRegenerator(generalOptions.console())
            .orElseThrow(
                () ->
                    new ValidationException(
                        "this destination does not support regenerating patch files"));

    Path previousPath = workdir.resolve(ConsistencyFile.PREMERGE_DIR_NAME);
    Path nextPath = workdir.resolve(ConsistencyFile.CHECKOUT_DIR_NAME);

    Path autopatchPath = workdir.resolve("autopatches");
    Files.createDirectories(previousPath);
    Files.createDirectories(nextPath);
    Files.createDirectories(autopatchPath);

    Optional<String> getRegenTargetResult = regenerateOptions.getRegenTarget();
    if (getRegenTargetResult.isEmpty()) {
      getRegenTargetResult = patchRegenerator.inferRegenTarget();
    }
    String regenTarget =
        getRegenTargetResult.orElseThrow(
            () ->
                new ValidationException(
                    "Regen target was neither supplied nor able to be inferred. Supply with"
                        + " --regen-target parameter"));
    AutoPatchfileConfiguration autopatchConfig = workflow.getAutoPatchfileConfiguration();

    Optional<String> regenBaseline = Optional.empty();
    if (workflow.isConsistencyFileMergeImport()) {
      regenBaseline = regenerateOptions.getRegenBaseline();
      if (regenBaseline.isEmpty()) {
        regenBaseline = patchRegenerator.inferRegenBaseline();
      }
      if (regenBaseline.isEmpty()) {
        console.info("Regen baseline could not be inferred. Falling back to import baseline");
      }
    }

    String patchFilePath = null;
    boolean useExplicitPatch =
        !workflow.isMergeImport() && workflow.getConsistencyFilePath() != null;
    if (useExplicitPatch) {
      patchFilePath = getValidatedPatchFilePath();
      if (patchFilePath != null) {
        patchingOptions.skippedPatchFiles = ImmutableList.of(patchFilePath);
      } else {
        useExplicitPatch = false;
      }
    }

    if (regenBaseline.isPresent()) {
      checkCondition(
          consistencyFileExists(
              destinationWriter, regenBaseline.get(), workflow.getConsistencyFilePath()),
          "Regenerating a consistency file merge import change but no consistency file found.");
      prepareDiffWithConsistencyFileBaseline(
          autopatchConfig,
          workflow,
          destinationWriter,
          previousPath,
          nextPath,
          autopatchPath,
          regenBaseline.get(),
          regenTarget);
    } else {
      previousPath =
          prepareDiffWithImportBaseline(
              patchRegenerator,
              autopatchConfig,
              workflow,
              workdir,
              nextPath,
              regenTarget,
              destinationWriter);
    }

    ConsistencyFile consistencyFile = null;
    if (workflow.getConsistencyFilePath() != null) {
      try {
        boolean excludeBuildFiles = false;
        if (workflow.getConsistencyFileConfig() != null) {
          excludeBuildFiles = workflow.getConsistencyFileConfig().excludeBuildFiles();
        }
        ImmutableSet.Builder<String> excludedFilesBuilder = ImmutableSet.builder();
        if (useExplicitPatch && patchFilePath != null) {
          excludedFilesBuilder.add(patchFilePath).add(getSeriesFilePath(patchFilePath));
        }
        consistencyFile =
            ConsistencyFile.generate(
                previousPath,
                nextPath,
                workflow.getDestination().getHashFunction(),
                workflow.getGeneralOptions().getEnvironment(),
                workflow.isVerbose(),
                workflow.getMainConfigFile().getIdentifier(),
                workflow.getName(),
                excludeBuildFiles,
                excludedFilesBuilder.build());
      } catch (InsideGitDirException e) {
        throw new ValidationException("Error generating consistency file", e);
      }
    }

    if (autopatchConfig != null && !useExplicitPatch) {
      // generate new autopatch files in the target directory
      // if explicit patch file is used, autopatches should always be empty, so skip early
      try {
        AutoPatchUtil.generatePatchFiles(
            previousPath,
            nextPath,
            Path.of(autopatchConfig.directoryPrefix()),
            autopatchConfig.directory(),
            workflow.isVerbose(),
            workflow.getGeneralOptions().getEnvironment(),
            autopatchConfig.header(),
            autopatchConfig.suffix(),
            nextPath,
            autopatchConfig.stripFilenames(),
            autopatchConfig.stripLineNumbers(),
            autopatchConfig.glob());
      } catch (InsideGitDirException e) {
        throw new ValidationException(
            String.format(
                "Could not automatically generate patch files because temporary directory %s is"
                    + " inside git repository %s. Error received is %s",
                e.getPath(), e.getGitDirPath(), e.getMessage()),
            e);
      }
    }

    Glob destinationFiles = workflow.getDestinationFiles();
    if (consistencyFile != null) {
      byte[] consistencyFileBytes;
      if (!useExplicitPatch) {
        consistencyFileBytes = consistencyFile.toBytes();
      } else {
        // extend destination files with explicit patch and series if necessary
        DestinationReader baselineDestinationReader =
            destinationWriter.getDestinationReader(console, regenTarget, workdir);
        String patchDescription = resolvePatchDescription(patchFilePath, baselineDestinationReader);
        destinationFiles =
            writePatchAndSeriesFiles(
                consistencyFile,
                patchFilePath,
                nextPath,
                baselineDestinationReader,
                destinationFiles,
                patchDescription);
        consistencyFile = updateConsistencyFileHashes(consistencyFile, patchFilePath, nextPath);
        consistencyFileBytes = consistencyFile.withoutDiff().toBytes();
      }
      Files.createDirectories(nextPath.resolve(workflow.getConsistencyFilePath()).getParent());
      Files.write(nextPath.resolve(workflow.getConsistencyFilePath()), consistencyFileBytes);
    }

    // push the new set of files
    patchRegenerator.updateChange(workflow.getName(), nextPath, destinationFiles, regenTarget);
  }

  @Nullable
  private String resolvePatchDescription(String patchFilePath, DestinationReader destinationReader)
      throws ValidationException {
    // 1. CLI description override (absolute precedence)
    Optional<String> cliDesc = regenerateOptions.getRegenPatchDescription();
    if (cliDesc.isPresent()) {
      DiffUtil.validatePatchDescription(cliDesc);
      return cliDesc.get();
    }

    // Determine keep behavior
    boolean keepExisting = false;
    Boolean keepCliOption = regenerateOptions.getPatchDescriptionKeep();
    if (keepCliOption != null) {
      keepExisting = keepCliOption;
    } else {
      // Default to true if explicit patch file is provided, false otherwise
      keepExisting = regenerateOptions.getRegenPatchFile().isPresent();
    }

    // 2. Keep existing description
    if (keepExisting && destinationReader.exists(patchFilePath)) {
      try {
        String existingContent = destinationReader.readFile(patchFilePath);
        return DiffUtil.extractDescription(existingContent);
      } catch (RepoException e) {
        console.warn("Failed to read existing patch file to keep description: " + e.getMessage());
      }
    }

    // 3. Config default
    if (workflow.getConsistencyFileConfig() != null
        && workflow.getConsistencyFileConfig().patchFileDescription() != null) {
      return workflow.getConsistencyFileConfig().patchFileDescription();
    }

    return null;
  }

  /**
   * Return most specific available patch file path according to this fallback logic:
   *
   * <ol>
   *   <li>explicitly provided CLI argument
   *   <li>configured in consistency config
   *   <li>default to consistency file path + ".patch"
   * </ol>
   *
   * and also validates that the picked path is normalized and relative, so it can be used relative
   * to the working directory and destination.
   */
  @Nullable
  private String getValidatedPatchFilePath() throws ValidationException {
    String filePath = null;
    if (regenerateOptions.getRegenPatchFile().isPresent()) {
      filePath = regenerateOptions.getRegenPatchFile().get();
    } else if (workflow.getConsistencyFileConfig() != null
        && workflow.getConsistencyFileConfig().patchFilePath() != null) {
      filePath = workflow.getConsistencyFileConfig().patchFilePath();
    } else {
      String consistencyFilePath = workflow.getConsistencyFilePath();
      if (consistencyFilePath != null) {
        filePath = consistencyFilePath + ".patch";
      }
    }

    if (filePath == null) {
      return null;
    }

    try {
      return FileUtil.standardizePath(filePath);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(e.getMessage(), e);
    }
  }

  private String getSeriesFilePath(String patchFilePath) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(patchFilePath));

    Path patchPath = Path.of(patchFilePath);
    Path parent = patchPath.getParent();
    if (parent != null) {
      return parent.resolve("series").toString();
    }
    return "series";
  }

  private boolean consistencyFileExists(
      Writer<D> destinationWriter, String regenBaseline, String consistencyFilePath)
      throws ValidationException, RepoException {
    DestinationReader previousDestinationReader =
        destinationWriter.getDestinationReader(console, regenBaseline, workdir);
    return previousDestinationReader.exists(consistencyFilePath);
  }

  private void prepareDiffWithConsistencyFileBaseline(
      @Nullable AutoPatchfileConfiguration autopatchConfig,
      Workflow<O, D> workflow,
      Writer<D> destinationWriter,
      Path previousPath,
      Path nextPath,
      Path patchPath,
      String regenBaseline,
      String regenTarget)
      throws ValidationException, RepoException, IOException {

    Glob autopatchlessDestinationFiles = workflow.getDestinationFiles();

    // download all files except for patch files
    if (autopatchConfig != null) {
      Glob autopatchGlob =
          AutoPatchUtil.getAutopatchGlob(
              autopatchConfig.directoryPrefix(), autopatchConfig.directory());
      autopatchlessDestinationFiles = Glob.difference(autopatchlessDestinationFiles, autopatchGlob);
    }

    Glob consistencyFileGlob = Glob.createGlob(ImmutableList.of(workflow.getConsistencyFilePath()));
    autopatchlessDestinationFiles =
        Glob.difference(autopatchlessDestinationFiles, consistencyFileGlob);

    // copy the baseline to one directory
    DestinationReader previousDestinationReader =
        destinationWriter.getDestinationReader(console, regenBaseline, workdir);
    previousDestinationReader.copyDestinationFilesToDirectory(
        autopatchlessDestinationFiles, previousPath);

    // copy the target to another directory
    DestinationReader nextDestinationReader =
        destinationWriter.getDestinationReader(console, regenTarget, workdir);
    nextDestinationReader.copyDestinationFilesToDirectory(autopatchlessDestinationFiles, nextPath);

    // copy consistency file to a third directory
    previousDestinationReader.copyDestinationFilesToDirectory(consistencyFileGlob, patchPath);

    // reverse patch files on the target directory here to get a pristine import
    Path consistencyFilePath = patchPath.resolve(workflow.getConsistencyFilePath());
    if (Files.exists(consistencyFilePath)) {
      ConsistencyFile consistencyFile =
          ConsistencyFile.fromBytes(Files.readAllBytes(consistencyFilePath));
      consistencyFile.reversePatches(previousPath, workflow.getGeneralOptions().getEnvironment());
    } else {
      console.warn("ConsistencyFile enabled but no ConsistencyFile file encountered");
    }
  }

  private Path prepareDiffWithImportBaseline(
      PatchRegenerator patchRegenerator,
      @Nullable AutoPatchfileConfiguration autopatchConfig,
      Workflow<O, D> workflow,
      Path workdir,
      Path nextPath,
      String regenTarget,
      Writer<D> destinationWriter)
      throws ValidationException, RepoException, IOException {

    WorkflowRunHelper<O, D> runHelper;
    O importRevision;

    if (sourceRef == null) {
      // no source ref specified, attempt to infer
      Optional<String> inferImportBaselineResult =
          patchRegenerator.inferImportBaseline(regenTarget, workdir);
      if (inferImportBaselineResult.isPresent()) {
        console.infoFmt(
            "Inferred import baseline %s from destination", inferImportBaselineResult.get());
        importRevision = workflow.getOrigin().resolve(inferImportBaselineResult.get());
        runHelper =
            createRunHelper(workflow, workdir, importRevision, inferImportBaselineResult.get());
      } else {
        // no source ref, no inferred baseline
        console.warn(
            "Regenerate was unable to detect the import baseline reference nor was a reference"
                + " passed in.\n"
                + "Ideally, the reference imported by the workflow migration is the one used for"
                + " the import baseline.\n"
                + "Regenerate will use the latest reference or follow `--same-version`, but this"
                + " may not match the one used for the initial import\n"
                + "To pass in a reference, add it to the copybara command, e.g. `copybara"
                + " regenerate [config path] [migration name] [reference]`\n");
        // use workflow logic to determine reference
        importRevision = workflow.getOrigin().resolve(sourceRef);
        runHelper = createRunHelper(workflow, workdir, importRevision, sourceRef);

        if (WorkflowMode.isHistorySupported(runHelper)) {
          if (workflowOptions.importSameVersion) {
            importRevision = WorkflowMode.maybeGetLastRev(runHelper);
          }
        }

        console.infoFmt(
            "Regenerating with import baseline from origin revision %s", importRevision.asString());
      }
    } else {
      console.infoFmt("Regenerating with import baseline from source ref %s", sourceRef);
      importRevision = workflow.getOrigin().resolve(sourceRef);
      runHelper = createRunHelper(workflow, workdir, importRevision, sourceRef);
    }

    // exclude copybara-managed patch files from diffing to prevent circular diffs
    Glob autopatchlessDestinationFiles = workflow.getDestinationFiles();
    if (autopatchConfig != null) {
      Glob autopatchGlob =
          AutoPatchUtil.getAutopatchGlob(
              autopatchConfig.directoryPrefix(), autopatchConfig.directory());
      autopatchlessDestinationFiles =
          Glob.difference(workflow.getDestinationFiles(), autopatchGlob);
    }
    if (workflow.getConsistencyFilePath() != null) {
      Glob consistencyFileGlob =
          Glob.createGlob(ImmutableList.of(workflow.getConsistencyFilePath()));
      autopatchlessDestinationFiles =
          Glob.difference(autopatchlessDestinationFiles, consistencyFileGlob);

      boolean useExplicitPatch = !workflow.isMergeImport();
      if (useExplicitPatch) {
        String patchFilePath = getValidatedPatchFilePath();
        if (patchFilePath != null) {
          Glob explicitPatchGlob =
              Glob.createGlob(ImmutableList.of(patchFilePath, getSeriesFilePath(patchFilePath)));
          autopatchlessDestinationFiles =
              Glob.difference(autopatchlessDestinationFiles, explicitPatchGlob);
        }
      }
    }

    // copy the baseline to one directory
    DestinationReader previousDestinationReader =
        destinationWriter.getDestinationReader(console, (Baseline<?>) null, workdir);
    Path importPath =
        runHelper.importAndTransformRevision(
            console, null, importRevision, () -> previousDestinationReader);

    // copy the target to another directory
    DestinationReader nextDestinationReader =
        destinationWriter.getDestinationReader(console, regenTarget, workdir);
    nextDestinationReader.copyDestinationFilesToDirectory(autopatchlessDestinationFiles, nextPath);

    return importPath;
  }

  private Glob writePatchAndSeriesFiles(
      ConsistencyFile consistencyFile,
      String patchFilePath,
      Path nextPath,
      DestinationReader destinationReader,
      Glob destinationFiles,
      @Nullable String patchDescription)
      throws IOException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(patchFilePath));

    byte[] diff = consistencyFile.getDiffContent();

    // fix paths within diff to ensure patches apply to real destination folders.
    // if configured, also strip out and make file paths relative to a given folder.
    String pathPrefix =
        workflow.getConsistencyFileConfig() == null
            ? null
            : workflow.getConsistencyFileConfig().patchPathPrefixToStrip();
    diff =
        DiffUtil.stripPathPrefixes(
            diff,
            /* leftPrefix= */ ConsistencyFile.PREMERGE_DIR_NAME,
            /* rightPrefix= */ ConsistencyFile.CHECKOUT_DIR_NAME,
            /* commonPrefix= */ pathPrefix);
    diff = DiffUtil.normalizeDiff(diff);

    // read series
    String seriesFilePath = getSeriesFilePath(patchFilePath);
    boolean seriesExisted = destinationReader.exists(seriesFilePath);
    List<String> seriesLines = new ArrayList<>();
    if (seriesExisted) {
      try {
        String content = destinationReader.readFile(seriesFilePath).trim();
        if (!content.isEmpty()) {
          seriesLines = new ArrayList<>(Splitter.on(LINE_SPLITTER).splitToList(content));
        }
      } catch (RepoException e) {
        seriesExisted = false;
      }
    }

    String patchName = Path.of(patchFilePath).getFileName().toString();

    // no diffs found: cleanup patch from series if present, write series to output if preexisting
    // or changed
    if (diff.length == 0) {
      seriesLines.removeIf(line -> line.trim().equals(patchName));
      if (seriesExisted || !seriesLines.isEmpty()) {
        destinationFiles =
            writeFileAndRegister(
                nextPath, seriesFilePath, joinLinesToBytes(seriesLines), destinationFiles);
      }
      destinationFiles = registerFile(destinationFiles, patchFilePath, nextPath);
      return destinationFiles;
    }

    // write diff to patch file
    boolean patchExisted = destinationReader.exists(patchFilePath);
    byte[] patchContent = diff;
    if (!Strings.isNullOrEmpty(patchDescription)) {
      String descriptionWithNewline = patchDescription + "\n";
      byte[] descriptionBytes = descriptionWithNewline.getBytes(UTF_8);
      patchContent = Bytes.concat(descriptionBytes, diff);
    }
    destinationFiles =
        writeFileAndRegister(nextPath, patchFilePath, patchContent, destinationFiles);

    // write series if preexisting or if patch+series both don't exist yet
    if (seriesExisted || !patchExisted) {
      seriesLines.removeIf(line -> line.trim().equals(patchName));
      seriesLines.add(patchName);
      destinationFiles =
          writeFileAndRegister(
              nextPath, seriesFilePath, joinLinesToBytes(seriesLines), destinationFiles);
    }

    if (!seriesExisted) {
      console.warn(
          """
          WARNING: Generated patch file and series file, but they might not be applied yet. Check \
          your config and ensure a patch transformation like patch.apply or patch.quilt_apply \
          applies these patches.\
          """);
    }
    return destinationFiles;
  }

  private ConsistencyFile updateConsistencyFileHashes(
      ConsistencyFile consistencyFile, String patchFilePath, Path nextPath) throws IOException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(patchFilePath));

    ImmutableMap.Builder<String, String> finalHashes = ImmutableMap.builder();
    finalHashes.putAll(consistencyFile.getFileHashes());

    HashFunction hashFunction = workflow.getDestination().getHashFunction();

    // refresh patch file hash
    Path patchFile = nextPath.resolve(patchFilePath);
    if (Files.exists(patchFile)) {
      HashCode hashCode = MoreFiles.asByteSource(patchFile).hash(hashFunction);
      finalHashes.put(patchFilePath, hashCode.toString());
    }

    // refresh series file hash
    String seriesFilePath = getSeriesFilePath(patchFilePath);
    Path seriesFile = nextPath.resolve(seriesFilePath);
    if (Files.exists(seriesFile)) {
      HashCode hashCode = MoreFiles.asByteSource(seriesFile).hash(hashFunction);
      finalHashes.put(seriesFilePath, hashCode.toString());
    }

    return consistencyFile.withHashes(finalHashes.buildKeepingLast());
  }

  private static Glob registerFile(Glob destinationFiles, String relativeFilePath, Path nextPath) {
    Path fullPath = nextPath.resolve(relativeFilePath);
    PathMatcher destPathMatcher = destinationFiles.relativeTo(nextPath);
    if (!destPathMatcher.matches(fullPath)) {
      return Glob.union(destinationFiles, Glob.createGlob(ImmutableList.of(relativeFilePath)));
    }
    return destinationFiles;
  }

  private static Glob writeFileAndRegister(
      Path nextPath, String relativeFilePath, byte[] content, Glob destinationFiles)
      throws IOException {
    Path fullPath = nextPath.resolve(relativeFilePath);
    Files.createDirectories(fullPath.getParent());
    Files.write(fullPath, content);
    return registerFile(destinationFiles, relativeFilePath, nextPath);
  }

  private static byte[] joinLinesToBytes(List<String> lines) {
    if (lines.isEmpty()) {
      return new byte[0];
    }
    return String.join("\n", lines).getBytes(UTF_8);
  }

  private WorkflowRunHelper<O, D> createRunHelper(
      Workflow<O, D> workflow, Path workdir, O resolvedRef, String sourceRef)
      throws ValidationException {
    return workflow.newRunHelper(
        workdir, resolvedRef, sourceRef, (ChangeMigrationFinishedEvent e) -> {});
  }
}
