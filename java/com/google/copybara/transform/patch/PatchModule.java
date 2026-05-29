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

package com.google.copybara.transform.patch;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

/** Skylark module that provides a basic transform to apply patchfiles. */
@StarlarkBuiltin(name = "patch", doc = "Module for applying patches.")
public class PatchModule implements LabelsAwareModule, StarlarkValue {
  private enum ValidationLevel {
    FULL,
    OPTIONAL_SERIES,
    NONE
  }

  private static final Splitter LINES =
      Splitter.onPattern("\\r?\\n").omitEmptyStrings().trimResults();

  private ConfigFile configFile;
  private final PatchingOptions patchingOptions;
  private final GeneralOptions generalOptions;

  public PatchModule(PatchingOptions patchingOptions, GeneralOptions generalOptions) {
    this.patchingOptions = checkNotNull(patchingOptions);
    this.generalOptions = checkNotNull(generalOptions);
  }

  @Override
  public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
    this.configFile = currentConfigFile;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "apply",
      doc =
          """
          A transformation that applies the given patch files. If a path does not exist in a \
          patch, it will be ignored.\
          """,
      parameters = {
        @Param(
            name = "patches",
            named = true,
            defaultValue = "[]",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            doc =
                """
                The list of patchfiles to apply, relative to the current config file. The files \
                will be applied relative to the checkout dir and the leading path component \
                will be stripped (-p1).

                If `series` is also specified, these patches \
                will be applied before those ones.

                **This field doesn't accept a glob.**\
                """),
        @Param(
            name = "excluded_patch_paths",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true,
            defaultValue = "[]",
            doc =
                """
                The list of paths to exclude from each of the patches. Each of the paths will be \
                excluded from all the patches. Note that these are not workdir paths, but \
                paths relative to the patch itself. If not empty, the patch will be \
                applied using 'git apply' instead of GNU Patch.\
                """),
        @Param(
            name = "series",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            positional = false,
            defaultValue = "None",
            doc =
                """
                A file which contains a list of patches to apply. The patch files to apply are \
                interpreted relative to this file and must be written one per line. The \
                patches listed in this file will be applied relative to the checkout dir \
                and the leading path component will be stripped (via the `-p1` flag).

                You can generate a file which matches this format by running \
                'find . -name *.patch &#124; sort > series'.

                If `patches` is also specified, those patches will be applied before these ones.\
                """),
        @Param(
            name = "strip",
            named = true,
            positional = false,
            defaultValue = "1",
            doc =
                """
                Number of segments to strip. (This sets the `-pX` flag, for example `-p0`, `-p1`, \
                etc.) By default it uses `-p1`.\
                """),
        @Param(
            name = "directory",
            named = true,
            positional = false,
            defaultValue = "''",
            doc =
                """
                Path relative to the working directory from which to apply patches. This supports \
                patches that specify relative paths in their file diffs but use a different \
                relative path base than the working directory. (This sets the `-d` flag, \
                for example `-d sub/dir/`). By default, it uses the current directory.\
                """),
        @Param(
            name = "validation_level",
            named = true,
            positional = false,
            defaultValue = "\"OPTIONAL_SERIES\"",
            doc =
                """
                The validation level to use for patch files and series:
                'FULL': if series is provided, series file must exist in the filesystem, be not \
                empty, and all patch files mentioned within must also exist. Patch files used \
                directly must also exist in the filesystem.
                'OPTIONAL_SERIES': not an error if series does not exist or is empty. If series \
                exists, patch files mentioned within must still exist.
                'NONE': no validation, series or patches within might not exist or be empty.\
                """),
      },
      useStarlarkThread = true)
  @UsesFlags(PatchingOptions.class)
  public PatchTransformation apply(
      Object patches,
      Sequence<?> excludedPaths,
      Object seriesOrNone,
      StarlarkInt stripI,
      String directory,
      String validationLevelString,
      StarlarkThread thread)
      throws EvalException, ValidationException {
    ValidationLevel validationLevel = getValidationLevel(validationLevelString);

    int strip = stripI.toInt("strip");
    ImmutableList.Builder<ConfigFile> patchFiles = ImmutableList.builder();
    for (String patch : SkylarkUtil.convertStringList(patches, "patches")) {
      Optional<ConfigFile> resolved = resolve(patch, validationLevel);
      resolved.ifPresent(patchFiles::add);
    }
    String series = SkylarkUtil.convertOptionalString(seriesOrNone);
    if (series != null && !series.trim().isEmpty()) {
      parseSeries(series, patchFiles, validationLevel);
    }
    return new PatchTransformation(
        patchFiles.build(),
        ImmutableList.copyOf(SkylarkUtil.convertStringList(excludedPaths, "excludedPaths")),
        patchingOptions,
        /* reverse= */ false,
        strip,
        directory,
        thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "quilt_apply",
      doc =
          """
          A transformation that applies and updates patch files using Quilt. Compared to \
          `patch.apply`, this transformation supports updating the content of patch files \
          if they can be successfully applied with fuzz.

          The series and patch files must be included in the destination_files glob in order to \
          get updated. The updated files end up in workingDirectory/`directory`/`patchesDirectory`.\
           `patchesDirectory` is the directory name from `series` in which the series file is \
          placed. If a directory with the same name already exists in the output, a warning is \
          logged and its content overridden.

          Underneath, Copybara runs quilt in the `directory` parameter's location and then calls \
          `quilt import; quilt push; quilt refresh` for each patch file in the `series` file in \
          order. Copybara also uses the `-p ab` flag to strip out the leading `a/` and `b/` path \
          components and then applies the patch files relative to the `directory` parameter's \
          location.\
          """,
      parameters = {
        @Param(
            name = "series",
            named = true,
            positional = false,
            doc =
                """
                A path to a series file to apply using Quilt, relative to the Copybara config \
                directory.

                This parameter must represent a simple non-empty relative path. `.` or `..` or \
                absolute paths are not supported.

                Quilt's standard path is `patches/series`, but overriding Quilt's patches \
                directory name is supported by specifying a different directory name here or no \
                directory. A different name for the series file is currently not supported, so \
                this parameter must end in `series`.\
                """),
        @Param(
            name = "directory",
            named = true,
            positional = false,
            defaultValue = "''",
            doc =
                """
                Path relative to the working directory from which to run quilt and apply patches. \
                This supports patches that specify relative paths in their file diffs but use a \
                different relative path base than the working directory.

                This is also the output directory in which the updated patches directory, \
                series and patch files get returned.

                By default, it uses the root of the working directory.\
                """),
        @Param(
            name = "validation_level",
            named = true,
            positional = false,
            defaultValue = "\"FULL\"",
            doc =
                """
                The validation level to use for patch files and series:
                'FULL': series must exist in the filesystem, be not empty, and all patch files \
                mentioned within must also exist.
                'OPTIONAL_SERIES': not an error if series does not exist or is empty. If series \
                exists, patch files mentioned within must still exist.
                'NONE': no file system or content validation. Series or patches within might not \
                exist or be empty files. The series parameter must still be specified and \
                represent a simple relative path.\
                """),
      },
      useStarlarkThread = true)
  @Example(
      title = "Workflow to apply and update patches",
      before =
          """
          Suppose the destination repository's directory structure looks like:
          ```
          source_root/BUILD
          source_root/copy.bara.sky
          source_root/migrated_file1
          source_root/migrated_file2
          source_root/patches/series
          source_root/patches/patch1.patch
          ```
          Then the transformations in `source_root/copy.bara.sky` should look like:\
          """,
      code =
          """
          [
              patch.quilt_apply(series = "patches/series"),
              core.move("", "source_root"),
          ]\
          """,
      after =
          """
          In this example, `patch1.patch` is applied to `migrated_file1` and/or `migrated_file2`. \
          `patch1.patch` itself will be updated during the migration if it is applied with \
          fuzz.\
          """)
  @UsesFlags(PatchingOptions.class)
  public QuiltTransformation quiltApply(
      String series, String directory, String validationLevelString, StarlarkThread thread)
      throws EvalException, ValidationException {
    validateQuiltSeriesParameter(series);

    ValidationLevel validationLevel = getValidationLevel(validationLevelString);
    ImmutableList.Builder<ConfigFile> patchFiles = ImmutableList.builder();
    Optional<ConfigFile> seriesFile = parseSeries(series, patchFiles, validationLevel);
    return new QuiltTransformation(
        seriesFile,
        patchFiles.build(),
        patchingOptions,
        /* reverse= */ false,
        directory,
        thread.getCallerLocation(),
        getPatchesDirName(series));
  }

  private static void validateQuiltSeriesParameter(String series) throws ValidationException {
    if (Strings.isNullOrEmpty(series) || series.trim().isEmpty()) {
      throw new ValidationException("Series parameter is required and cannot be empty.");
    }

    try {
      FileUtil.checkNormalizedRelative(series);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(e.getMessage(), e);
    }

    try {
      checkCondition(
          series != null && Path.of(series).getFileName().toString().equals("series"),
          String.format(
              "Custom patch series file names besides `series` are not supported. "
                  + "Please update your series parameter %s to end in `series`.",
              series));
    } catch (InvalidPathException e) {
      throw new ValidationException("Series parameter must represent a filesystem path.", e);
    }
  }

  private ValidationLevel getValidationLevel(String validationLevelString) throws EvalException {
    ValidationLevel validationLevel =
        SkylarkUtil.stringToEnum("validation_level", validationLevelString, ValidationLevel.class);
    if (Objects.equals(patchingOptions.validateOnLoad, false)) {
      validationLevel = ValidationLevel.NONE;
    } else if (Objects.equals(patchingOptions.validateOnLoad, true)) {
      validationLevel = ValidationLevel.FULL;
    }
    return validationLevel;
  }

  private String getPatchesDirName(String series) {
    Path parentFolder = Path.of(series).getParent();
    if (parentFolder != null) {
      return parentFolder.getFileName().toString();
    }
    return ".";
  }

  private Optional<ConfigFile> resolve(String path, ValidationLevel validationLevel)
      throws EvalException {
    try {
      return Optional.of(configFile.resolve(path));
    } catch (CannotResolveLabel e) {
      if (validationLevel == ValidationLevel.NONE) {
        generalOptions.console().infoFmt("Cannot load: %s", path);
        return Optional.empty();
      } else {
        throw Starlark.errorf("Failed to resolve patch: %s", path);
      }
    }
  }

  @CanIgnoreReturnValue
  private Optional<ConfigFile> parseSeries(
      String series,
      ImmutableList.Builder<ConfigFile> outputBuilder,
      ValidationLevel validationLevel)
      throws EvalException, ValidationException {
    ConfigFile seriesFile = null;
    try {
      // Don't use this.resolve(), because its error message mentions patch file instead of series.
      try {
        seriesFile = configFile.resolve(series.trim());
      } catch (CannotResolveLabel e) {
        switch (validationLevel) {
          case NONE, OPTIONAL_SERIES -> {
            generalOptions.console().infoFmt("Cannot load %s: %s", series.trim(), e);
            return Optional.empty();
          }
          default -> throw e;
        }
      }
      ImmutableList.Builder<ConfigFile> patchesBuilder = ImmutableList.builder();
      for (String line : LINES.split(seriesFile.readContent())) {
        // Comment at the beginning of the line or
        // a whitespace followed by the hash character.
        int comment = line.indexOf('#');
        if (comment != 0) {
          if (comment > 0 && Character.isWhitespace(line.charAt(comment - 1))) {
            line = line.substring(0, comment - 1).trim();
          }
          if (!line.isEmpty()) {
            try {
              patchesBuilder.add(seriesFile.resolve(line));
            } catch (CannotResolveLabel e) {
              if (validationLevel == ValidationLevel.NONE) {
                generalOptions.console().infoFmt("Cannot load %s: %s", line, e);
              } else {
                throw e;
              }
            }
          }
        }
      }
      ImmutableList<ConfigFile> patches = patchesBuilder.build();
      outputBuilder.addAll(patches);
    } catch (CannotResolveLabel | IOException e) {
      throw Starlark.errorf(
          "Error reading patch series file: %s. Caused by: %s", series, e.toString());
    }
    if (validationLevel == ValidationLevel.FULL) {
      checkCondition(
          seriesFile != null && !outputBuilder.build().isEmpty(),
          String.format("Patch series %s cannot be empty for full validation.", series));
    }
    return Optional.ofNullable(seriesFile);
  }
}
