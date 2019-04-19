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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.io.IOException;

/**
 * Skylark module that provides a basic transform to apply patchfiles.
 */
@SkylarkModule(
    name = "patch",
    doc = "Module for applying patches.",
    category = SkylarkModuleCategory.BUILTIN)
public class PatchModule implements LabelsAwareModule {
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
  @SkylarkCallable(
      name = "apply",
      doc = "A transformation that applies the given patch files. If a path does not exist in a"
          + " patch, it will be ignored.",
      parameters = {
          @Param(name = "patches",
              type = SkylarkList.class, named = true, generic1 = String.class, defaultValue = "[]",
              doc = "The list of patchfiles to apply, relative to the current config file."
                  + "The files will be applied relative to the checkout dir and the leading path"
                  + "component will be stripped (-p1).<br><br>"
                  + "This field can be combined with 'series'. Both 'patches' and 'series' will "
                  + "be applied in order (patches first)."),
          @Param(name = "excluded_patch_paths",
              type = SkylarkList.class, named = true, generic1 = String.class, defaultValue = "[]",
              doc = "The list of paths to exclude from each of the patches. Each of the paths will "
                  + "be excluded from all the patches. Note that these are not workdir paths, but "
                  + "paths relative to the patch itself. If not empty, the patch will be applied "
                  + "using 'git apply' instead of GNU Patch."),
          @Param(name = "series", named = true, noneable = true, positional = false,
              type = String.class, defaultValue = "None",
              doc = "The config file that contains a list of patches to apply. "
                  + "The <i>series</i> file contains names of the patch files one per line. "
                  + "The names of the patch files are relative to the <i>series</i> config file. "
                  + "The files will be applied relative to the checkout dir and the leading path "
                  + "component will be stripped (-p1).:<br>:<br>"
                  + "This field can be combined with 'patches'. Both 'patches' and 'series' will "
                  + "be applied in order (patches first)."),
      },
      useLocation = true)
  public PatchTransformation apply(
      SkylarkList patches,
      SkylarkList excludedPaths,
      Object seriesOrNone,
      Location location) throws EvalException {
    ImmutableList.Builder<ConfigFile> builder = ImmutableList.builder();
    for (String patch : Type.STRING_LIST.convert(patches, "patches")) {
      builder.add(resolve(patch, location));
    }
    String series = Type.STRING.convertOptional(seriesOrNone, "series");
    if (series != null && !series.trim().isEmpty()) {
      try {
        ConfigFile seriesFile = resolve(series.trim(), location);
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
        throw new EvalException(location, "Error reading patch series file: " + series, e);
      }
    }
    return new PatchTransformation(
        builder.build(),
        ImmutableList.copyOf(Type.STRING_LIST.convert(excludedPaths, "excludedPaths")),
        patchingOptions, /*reverse=*/ false);
  }


  private ConfigFile resolve(String path, Location location) throws EvalException {
    try {
      return configFile.resolve(path);
    } catch (CannotResolveLabel e) {
      throw new EvalException(location, "Failed to resolve patch: " + path, e);
    }
  }
}
