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

package com.google.copybara.transform.patch;

import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.util.DiffUtil.checkNotInsideGitRepo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.InsideGitDirException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Options related to applying patches to directories (non-git).
 */
@Parameters(separators = "=")
public class PatchingOptions implements Option {

  private static final Pattern PATCH_VERSION_FORMAT = Pattern.compile(
      "[\\w ]+ (?<major>[0-9]+)\\.(?<minor>[0-9]+)(\\.[0-9]+)?.*",
      Pattern.DOTALL);
  private static final String PATCH_BIN_FLAG = "--patch-bin";
  public static final String SKIP_VERSION_CHECK_FLAG = "--patch-skip-version-check";
  private final GeneralOptions generalOptions;

  public PatchingOptions(GeneralOptions generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  @Parameter(names = PATCH_BIN_FLAG, description = "Path for GNU Patch command")
  String patchBin = "patch";

  @Parameter(names = SKIP_VERSION_CHECK_FLAG, description =
      "Skip checking the version of patch and assume it is fine")
  public boolean skipVersionCheck = false;

  @Parameter(names = "--patch-use-git-apply", description =
      "Don't use GNU Patch and instead use 'git apply'",
      // TODO(malcon): Remove arity. This is to have the flexiblity to switch on/off internally
      // until it is well tested.
      arity = 1)
  public boolean useGitApply = true;

  /**
   * Applies the diff into a directory tree.
   *
   * <p>{@code diffContents} is the result of invoking {@link DiffUtil#diff}.
   */
  public void patch(Path rootDir, byte[] diffContents, ImmutableList<String> excludedPaths,
      int stripSlashes, boolean reverse, @Nullable Path gitDir)
      throws IOException, InsideGitDirException, ValidationException {
    if (diffContents.length == 0) {
      return;
    }
    Preconditions.checkArgument(stripSlashes >= 0, "stripSlashes must be >= 0.");
    boolean verbose = generalOptions.isVerbose();
    Map<String, String> env = generalOptions.getEnvironment();
    if (shouldUsePatch(gitDir, excludedPaths)) {
      Preconditions.checkState(excludedPaths.isEmpty(), "Not supported by GNU Patch");
      patchWithGnuPatch(rootDir, diffContents, stripSlashes, verbose, reverse, env);
    } else {
      patchWithGitApply(
          rootDir, diffContents, excludedPaths, stripSlashes, verbose, reverse, env, gitDir);
    }
  }

  @VisibleForTesting
  class Version {

    private final int major;
    private final int minor;

    public Version(int major, int minor) {
      this.major = major;
      this.minor = minor;
    }

    @Override
    public String toString() {
      return major + "." + minor;
    }

    /** If GNU Patch is too old for understanding renames, etc. (at least 2.7.0) */
    public boolean isTooOld() {
      return major <= 2 && (major != 2 || minor < 7);
    }
  }

  private boolean shouldUsePatch(@Nullable Path gitDir, ImmutableList<String> excludedPaths)
      throws ValidationException {
    // We are going to patch a git checkout dir. We should use git apply three way.
    if (gitDir != null) {
      return false;
    }
    if (skipVersionCheck) {
      ValidationException.checkCondition(excludedPaths.isEmpty(),
          "%s is incompatible with patch transformations that uses excluded paths: %s",
          SKIP_VERSION_CHECK_FLAG, excludedPaths);
      return true;
    }
    // GNU Patch doesn't have a way to exclude paths
    if (useGitApply || !excludedPaths.isEmpty()) {
      return false;
    }

    try {
      Version version = getPatchVersion(patchBin);
      if (!version.isTooOld()) {
        return true;
      }

      if (isMac()) {
        generalOptions.console()
            .warnFmt("GNU Patch version is too old (%s) to be used by Copybara. "
                + "Defaulting to 'git apply'. Use %s if patch is available in a different"
                + " location", version, PATCH_BIN_FLAG);
        return false;
      }

      throw new ValidationException(String.format(
          "Too old version of GNU Patch (%s). Copybara required at least 2.7 version."
              + " Path used: %s. Use %s to use a different path",
          version, patchBin, PATCH_BIN_FLAG));

    } catch (CommandException e) {
      // While this might be an environment error, normally it is attributable to the user
      // (not having patch available).
      throw new ValidationException(
          String.format("Error using GNU Patch. Path used: %s. Use %s to use a different path",
              patchBin, PATCH_BIN_FLAG),
          e);
    }
  }

  Version getPatchVersion(String patchBin) throws CommandException, ValidationException {

    String out = new CommandRunner(new Command(new String[]{patchBin, "-v"}))
        .withVerbose(generalOptions.isVerbose())
        .execute()
        .getStdout()
        .trim();
    Matcher matcher = PATCH_VERSION_FORMAT.matcher(out);
    checkCondition(matcher.matches(),
        "Unknown version of GNU Patch. Path used: %s. Use %s to use a different path",
        this.patchBin, PATCH_BIN_FLAG);
    int major = Integer.parseInt(matcher.group("major"));
    int minor = Integer.parseInt(matcher.group("minor"));
    return new Version(major, minor);
  }

  private boolean isMac() {
    return "Mac OS X".equals(StandardSystemProperty.OS_NAME.value());
  }

  private static void patchWithGitApply(Path rootDir, byte[] diffContents,
      ImmutableList<String> excludedPaths, int stripSlashes, boolean verbose, boolean reverse,
      Map<String, String> environment, @Nullable Path gitDir)
      throws IOException, InsideGitDirException {

    GitEnvironment gitEnv = new GitEnvironment(environment);
    if (gitDir == null) {
      checkNotInsideGitRepo(rootDir, verbose, gitEnv);
    }
    ImmutableList.Builder<String> params = ImmutableList.builder();

    // Show verbose output unconditionally since it is helpful for debugging issues with patches.
    params.add(gitEnv.resolveGitBinary());
    if (gitDir != null) {
      params.add("--git-dir=" + gitDir.normalize().toAbsolutePath());
    }
    params.add("apply", "-v", "--stat", "--apply", "-p" + stripSlashes);
    if (gitDir != null) {
      params.add("--3way");
    }
    for (String excludedPath : excludedPaths) {
      params.add("--exclude", excludedPath);
    }
    if (reverse) {
      params.add("-R");
    }

    params.add("-");
    Command cmd =
        new Command(params.build().toArray(new String[0]), environment, rootDir.toFile());
    try {
      new CommandRunner(cmd)
          .withVerbose(verbose)
          .withInput(diffContents)
          .execute();
    } catch (BadExitStatusWithOutputException e) {
      throw new IOException(
          String.format("Error executing 'git apply': %s. Stderr: \n%s", e.getMessage(),
              e.getOutput().getStderr()), e);
    } catch (CommandException e) {
      throw new IOException("Error executing 'git apply'", e);
    }
  }

  private void patchWithGnuPatch(Path rootDir, byte[] diffContents, int stripSlashes,
      boolean verbose, boolean reverse, Map<String, String> environment) throws IOException {
    ImmutableList.Builder<String> params = ImmutableList.builder();

    // Show verbose output unconditionally since it is helpful for debugging issues with patches.
    // When the patch file doesn't match the file exactly, GNU patch creates backup files, but we
    // disable creating those as they don't make sense for Copybara and otherwise they would need
    // to be excluded
    // See: http://b/112639930
    params.add(patchBin, "--no-backup-if-mismatch", "-t", "-p" + stripSlashes);
    if (reverse) {
      params.add("-R");
    }

    // Only apply in the direction requested. Yes, -R --forward semantics is that it reverses
    // and only applies if can be applied like that (-R will try to apply reverse and forward).
    params.add("--forward");

    Command cmd =
        new Command(params.build().toArray(new String[0]), environment, rootDir.toFile());
    try {
      CommandOutputWithStatus output = generalOptions.newCommandRunner(cmd)
          .withVerbose(verbose)
          .withInput(diffContents)
          .execute();
      System.err.println(output);
    } catch (BadExitStatusWithOutputException e) {
      throw new IOException(
          String.format("Error executing 'patch': %s. Stderr: \n%s", e.getMessage(),
              e.getOutput().getStdout()),
          e);
    } catch (CommandException e) {
      throw new IOException("Error executing 'patch'", e);
    }
  }

}
