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
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import net.starlark.java.syntax.Location;

/**
 * Transformation for applying and updating patch files using Quilt during a workflow. Instantiated
 * by {@link PatchModule}.
 */

public final class QuiltTransformation implements Transformation {
  private final ConfigFile series;
  private final ImmutableList<ConfigFile> patchConfigs;
  private final PatchingOptions options;
  // TODO(copybara-team): Add support for reverse=True.
  // We could possibly implement it without using quilt. Assuming reversal does not require updating
  // any patch files, it can run "patch -R" for each patch in the reverse order of the series file.
  private final boolean reverse;
  private final Location location;

  QuiltTransformation(
      ConfigFile series, ImmutableList<ConfigFile> patches, PatchingOptions options,
      boolean reverse, Location location) {
    this.series = series;
    this.patchConfigs = patches;
    this.options = options;
    this.reverse = reverse;
    this.location = checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException {
    boolean verbose = options.getGeneralOptions().isVerbose();
    Path checkoutDir = work.getCheckoutDir();
    work.getConsole().infoFmt("Applying and updating patches with quilt.");
    // "quilt import <patch_path>" only works for a local path, so we copy the patch config files
    // to a local temp directory first.
    ImmutableList<Path> patches = copyPatchConfigsToTmpDir();
    Map<String, String> env = options.getGeneralOptions().getEnvironment();
    env = initializeQuilt(checkoutDir, env);
    importPatches(checkoutDir, patches, env, verbose);
    restoreSeriesAndCleanup(checkoutDir);
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() {
    return new QuiltTransformation(series, patchConfigs, options, !reverse, location);
  }

  @Override
  public String describe() {
    return "Patch.quilt_apply: using quilt to apply and update patches: "
        + patchConfigs.stream().map(ConfigFile::path).collect(joining(", "));
  }

  @Override
  public Location location() {
    return location;
  }

  private void runQuiltCommand(Path checkoutDir, Map<String, String> env,
      boolean verbose, String... args) throws IOException {
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
      throw new IOException(
          String.format("Error executing '%s': %s. Stderr: \n%s", String.join(" ", paramsList),
              e.getMessage(), e.getOutput().getStdout()),
          e);
    } catch (CommandException e) {
      throw new IOException(e);
    }
  }

  private ImmutableList<Path> copyPatchConfigsToTmpDir() throws IOException {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    Path patchDir = options.getGeneralOptions().getDirFactory().newTempDir("inputpatches");
    for (ConfigFile patch : patchConfigs) {
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

  private ImmutableMap<String, String> initializeQuilt(Path checkoutDir, Map<String, String> env)
      throws IOException {
    // Creates quiltrc file and sets up QUILTRC environment variable.
    ImmutableMap<String, String> quiltOptions = ImmutableMap.of(
      "QUILT_NO_DIFF_TIMESTAMPS", "1",
      "QUILT_DIFF_OPTS", "--show-c-function",
      // Uses the "-p ab" format in order to keep patch files' content independent of the parent
      // directory's name.
      "QUILT_DIFF_ARGS", "-p ab --no-index",
      "QUILT_REFRESH_ARGS", "-p ab --no-index",
      "QUILT_PATCHES_PREFIX", "yes");
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
    envBuilder.putAll(env).put("QUILTRC", quilrcPath.toRealPath().toString());

    // Creates and checks for necessary directories.
    Path patchesDir = checkoutDir.resolve("patches");
    // Fails if "patches" already exists.
    Files.createDirectory(patchesDir);
    Files.createFile(patchesDir.resolve("series"));
    Path pcDir = checkoutDir.resolve(".pc");
    if (Files.exists(pcDir)) {
      throw new FileAlreadyExistsException(pcDir.toRealPath().toString());
    }
    return envBuilder.buildOrThrow();
  }

  private void importPatches(Path checkoutDir, ImmutableList<Path> patches, Map<String, String> env,
      boolean verbose) throws IOException {
    for (Path patch : patches) {
      runQuiltCommand(checkoutDir, env, verbose, "import", patch.toString());
      runQuiltCommand(checkoutDir, env, verbose, "push");
      runQuiltCommand(checkoutDir, env, verbose, "refresh");
    }
  }

  private void restoreSeriesAndCleanup(Path checkoutDir) throws IOException {
    // Restores the original "series" file.
    try {
      Files.write(checkoutDir.resolve("patches").resolve("series"), series.readContentBytes());
    } catch (CannotResolveLabel e) {
      throw new IOException("Error reading original 'series' file", e);
    }
    // Deletes ".pc" directory.
    FileUtil.deleteRecursively(checkoutDir.resolve(".pc"));
  }
}
