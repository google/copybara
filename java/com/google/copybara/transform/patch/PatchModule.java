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
import static com.google.copybara.config.SkylarkUtil.check;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.util.Glob;
import java.io.IOException;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

/** Skylark module that provides a basic transform to apply patchfiles. */
@StarlarkBuiltin(name = "patch", doc = "Module for applying patches.")
public class PatchModule implements LabelsAwareModule, StarlarkValue {
  private static final Splitter LINES =
      Splitter.onPattern("\\r?\\n").omitEmptyStrings().trimResults();

  private ConfigFile configFile;
  private final PatchingOptions patchingOptions;

  public PatchModule(PatchingOptions patchingOptions) {
    this.patchingOptions = checkNotNull(patchingOptions);
  }

  @Override
  public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
    this.configFile = currentConfigFile;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "apply",
      doc =
          "A transformation that applies the given patch files. If a path does not exist in a"
              + " patch, it will be ignored.",
      parameters = {
        @Param(
            name = "patches",
            named = true,
            defaultValue = "[]",
            doc =
                "The list of patchfiles to apply, relative to the current config file."
                    + "The files will be applied relative to the checkout dir and the leading path"
                    + "component will be stripped (-p1).<br><br>"
                    + "This field can be combined with 'series'. Both 'patches' and 'series' will "
                    + "be applied in order (patches first). **This field doesn't accept a glob**"),
        @Param(
            name = "excluded_patch_paths",
            type = Sequence.class,
            named = true,
            generic1 = String.class,
            defaultValue = "[]",
            doc =
                "The list of paths to exclude from each of the patches. Each of the paths will be"
                    + " excluded from all the patches. Note that these are not workdir paths, but"
                    + " paths relative to the patch itself. If not empty, the patch will be"
                    + " applied using 'git apply' instead of GNU Patch."),
        @Param(
            name = "series",
            named = true,
            noneable = true,
            positional = false,
            type = String.class,
            defaultValue = "None",
            doc =
                "The config file that contains a list of patches to apply. "
                    + "The <i>series</i> file contains names of the patch files one per line. "
                    + "The names of the patch files are relative to the <i>series</i> config file. "
                    + "The files will be applied relative to the checkout dir and the leading path "
                    + "component will be stripped (-p1).:<br>:<br>"
                    + "This field can be combined with 'patches'. Both 'patches' and 'series' will "
                    + "be applied in order (patches first)."),
        @Param(
            name = "strip",
            named = true,
            positional = false,
            type = Integer.class,
            defaultValue = "1",
            doc =
                "Number of segments to strip. (This sets -pX flag, for example -p0, -p1, etc.)."
                    + "By default it uses -p1"),
      },
      useStarlarkThread = true)
  @UsesFlags(PatchingOptions.class)
  public PatchTransformation apply(
      Object patches,
      Sequence<?> excludedPaths,
      Object seriesOrNone,
      Integer strip,
      StarlarkThread thread)
      throws EvalException {
    ImmutableList.Builder<ConfigFile> builder = ImmutableList.builder();
    check(
        !(patches instanceof Glob),
        "'patches' cannot be a glob, only an explicit list of patches are accepted");
    for (String patch : SkylarkUtil.convertStringList(patches, "patches")) {
      builder.add(resolve(patch));
    }
    String series = SkylarkUtil.convertOptionalString(seriesOrNone);
    if (series != null && !series.trim().isEmpty()) {
      try {
        ConfigFile seriesFile = resolve(series.trim());
        for (String line : LINES.split(seriesFile.readContent())) {
          // Comment at the begining of the line or
          // a whitespace followed by the hash character.
          int comment = line.indexOf('#');
          if (comment != 0) {
            if (comment > 0 && Character.isWhitespace(line.charAt(comment - 1))) {
              line = line.substring(0, comment - 1).trim();
            }
            if (!line.isEmpty()) {
              builder.add(seriesFile.resolve(line));
            }
          }
        }
      } catch (CannotResolveLabel | IOException e) {
        throw Starlark.errorf("Error reading patch series file: %s", series);
      }
    }
    return new PatchTransformation(
        builder.build(),
        ImmutableList.copyOf(SkylarkUtil.convertStringList(excludedPaths, "excludedPaths")),
        patchingOptions,
        /*reverse=*/ false,
        strip,
        thread.getCallerLocation());
  }

  private ConfigFile resolve(String path) throws EvalException {
    try {
      return configFile.resolve(path);
    } catch (CannotResolveLabel e) {
      throw Starlark.errorf("Failed to resolve patch: %s", path);
    }
  }
}
