/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.transform.patch;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Console;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.starlark.java.syntax.Location;

/**
 * Transformation for applying and updating patch files using Quilt during a workflow. Instantiated
 * by {@link PatchModule}.
 */

public final class QuiltTransformation implements Transformation {
  private final Optional<ConfigFile> series;
  private final ImmutableList<ConfigFile> patchFiles;
  private final PatchingOptions options;
  // TODO(copybara-team): Add support for reverse=True.
  // We could possibly implement it without using quilt. Assuming reversal does not require updating
  // any patch files, it can run "patch -R" for each patch in the reverse order of the series file.
  private final boolean reverse;
  private final String directory;
  private final Location location;
  private final String patchesDirName;

  QuiltTransformation(
      Optional<ConfigFile> series,
      ImmutableList<ConfigFile> patchFiles,
      PatchingOptions options,
      boolean reverse,
      String directory,
      Location location,
      String patchesDirName) {
    this.series = series;
    this.patchFiles = patchFiles;
    this.options = options;
    this.reverse = reverse;
    this.directory = checkNotNull(directory);
    this.location = checkNotNull(location);
    this.patchesDirName = checkNotNull(patchesDirName);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    if (this.series.isEmpty()) {
      return TransformationStatus.success();
    }

    Path checkoutDir = work.getCheckoutDir().resolve(directory);
    createPatchDirectory(checkoutDir, work.getConsole());

    // avoid setting up and cleaning up quilt if not needed
    if (this.patchFiles.isEmpty()) {
      copySeriesFile(checkoutDir);
      return TransformationStatus.success();
    }

    boolean verbose = options.getGeneralOptions().isVerbose();
    work.getConsole().infoFmt("Applying and updating patches with quilt.");
    // "quilt import <patch_path>" only works for a local path, so we copy the patch files to a
    // local temp directory first and setup a fresh empty series file using these tmp paths
    ImmutableList<Path> patches = copyPatchFilesToTmpDir();
    Map<String, String> env = options.getGeneralOptions().getEnvironment();
    env = initializeQuilt(checkoutDir, env);
    importPatches(checkoutDir, patches, env, verbose);
    // restore the original series file with real paths
    copySeriesFile(checkoutDir);
    cleanupQuilt(checkoutDir);
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() {
    return new QuiltTransformation(
        series, patchFiles, options, !reverse, directory, location, patchesDirName);
  }

  @Override
  public String describe() {
    return "Patch.quilt_apply: using quilt to apply and update patches: "
        + patchFiles.stream().map(ConfigFile::path).collect(joining(", "));
  }

  @Override
  public Location location() {
    return location;
  }

  private void runQuiltCommand(
      Path checkoutDir, Map<String, String> env, boolean verbose, String... args)
      throws IOException, ValidationException {
    ImmutableList.Builder<String> params = ImmutableList.builder();
    params.add(options.quiltBin);
    params.add(args);
    ImmutableList<String> paramsList = params.build();
    Command cmd =
          new Command(paramsList.toArray(new String[0]), env, checkoutDir.toFile());
    try {
      options.getGeneralOptions().newCommandRunner(cmd)
          .withVerbose(verbose)
          .execute();
    } catch (BadExitStatusWithOutputException e) {
      Pattern patchDoesNotApplyMsgMatcher = Pattern.compile("Patch .* does not apply");
      if (patchDoesNotApplyMsgMatcher.matcher(e.getOutput().getStdout()).find()) {
        throw new ValidationException(
            String.format(
                "Error executing '%s': Patch file does not apply. Stderr: \n%s",
                String.join(" ", paramsList), e.getOutput().getStdout()),
            e);
      } else {
        throw new IOException(
            String.format(
                "Error executing '%s': %s. Stderr: \n%s",
                String.join(" ", paramsList), e.getMessage(), e.getOutput().getStdout()),
            e);
      }
    } catch (CommandException e) {
      throw new IOException(e);
    }
  }

  private ImmutableList<Path> copyPatchFilesToTmpDir() throws IOException {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    Path patchDir = options.getGeneralOptions().getDirFactory().newTempDir("inputpatches");
    for (ConfigFile patch : patchFiles) {
      // Uses String instead of Path for baseName, because patchDir's FileSystem may not match
      // the default FileSystem from Paths.get().
      String baseName = Paths.get(patch.path()).getFileName().toString();
      Path patchFile = patchDir.resolve(baseName);
      builder.add(patchFile);
      try {
        Files.write(patchFile, patch.readContentBytes());
      } catch (CannotResolveLabel e) {
        throw new IOException("Error reading input patch", e);
      }
    }
    return builder.build();
  }

  private void createPatchDirectory(Path checkoutDir, Console console) throws IOException {
    Path patchesDir = checkoutDir.resolve(patchesDirName);
    if (Files.exists(patchesDir)) {
      console.warnFmt("Destination already has a '%s' directory. Replacing files.", patchesDirName);
    }
    Files.createDirectories(patchesDir);
  }

  private void copySeriesFile(Path checkoutDir) throws IOException {
    Path patchesDir = checkoutDir.resolve(patchesDirName);
    try {
      Files.write(
          patchesDir.resolve("series"),
          series
              .orElseThrow(() -> new CannotResolveLabel("Cannot find series file"))
              .readContentBytes());
    } catch (CannotResolveLabel e) {
      throw new IOException("Error reading original 'series' file", e);
    }
  }

  private ImmutableMap<String, String> initializeQuilt(Path checkoutDir, Map<String, String> env)
      throws ValidationException, IOException {
    // Creates quiltrc file and sets up QUILTRC environment variable.
    ImmutableMap<String, String> quiltOptions =
        ImmutableMap.<String, String>builder()
            .put("QUILT_NO_DIFF_TIMESTAMPS", "1")
            .put("QUILT_DIFF_OPTS", "--show-c-function")
            // Uses the "-p ab" format in order to keep patch files' content independent of the
            // parent directory's name.
            .put("QUILT_DIFF_ARGS", "-p ab --no-index")
            .put("QUILT_REFRESH_ARGS", "-p ab --no-index")
            .put("QUILT_PATCHES_PREFIX", "yes")
            .put("QUILT_PATCHES", patchesDirName)
            .buildOrThrow();
    // It overwrites any existing copybara.quiltrc file, which is OK because it is in the
    // temporary directory and its content is always the same.
    Path quilrcPath = options.getGeneralOptions().getDirFactory().getTmpRoot().resolve(
        "copybara.quiltrc");
    try (BufferedWriter wr = Files.newBufferedWriter(quilrcPath)) {
      for (Map.Entry<String, String> entry : quiltOptions.entrySet()) {
        wr.append(String.format("%s=\"%s\"\n", entry.getKey(), entry.getValue()));
      }
    }

    ImmutableMap.Builder<String, String> envBuilder = ImmutableMap.builder();
    // Don't pass user settings through to Quilt.
    for (Map.Entry<String, String> var : env.entrySet()) {
      if (var.getKey().startsWith("QUILT_")) {
        continue;
      }
      envBuilder.put(var);
    }
    envBuilder.put("QUILTRC", quilrcPath.toRealPath().toString());

    // Creates and checks for necessary directories.
    Path pcDir = checkoutDir.resolve(".pc");
    if (Files.exists(pcDir)) {
      try {
        throw new ValidationException(
            ".pc aready exists at " + pcDir.toAbsolutePath().toRealPath());
      } catch (IOException e) {
        throw new ValidationException(
            String.format("Destination already has a '.pc' directory: %s", e.getMessage()));
      }
    }
    return envBuilder.buildOrThrow();
  }

  private void importPatches(
      Path checkoutDir, ImmutableList<Path> patches, Map<String, String> env, boolean verbose)
      throws IOException, ValidationException {
    for (Path patch : patches) {
      Path targetPatch = checkoutDir.resolve(patchesDirName).resolve(patch.getFileName());
      Files.deleteIfExists(targetPatch);
      runQuiltCommand(checkoutDir, env, verbose, "import", patch.toString());
      runQuiltCommand(checkoutDir, env, verbose, "push");
      runQuiltCommand(checkoutDir, env, verbose, "refresh");
    }
  }

  private void cleanupQuilt(Path checkoutDir) throws IOException {
    // Deletes ".pc" directory.
    FileUtil.deleteRecursively(checkoutDir.resolve(".pc"));
  }
}
