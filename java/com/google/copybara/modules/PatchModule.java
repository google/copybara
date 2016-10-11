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

package com.google.copybara.modules;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.CannotResolveLabel;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;

/**
 * Skylark module that provides a basic transform to apply patchfiles.
 */
@SkylarkModule(
    name = "patch",
    doc = "Module for applying patches.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GeneralOptions.class)
public class PatchModule implements LabelsAwareModule, OptionsAwareModule {

  private ConfigFile configFile;
  private GeneralOptions generalOptions;

  @Override
  public void setConfigFile(ConfigFile configFile) {
    this.configFile = configFile;
  }

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "apply", returnType = PatchTransformation.class,
      doc = "A transformation that applies the given patch files.",
      parameters = {
          @Param(name = "self", type = PatchModule.class, doc = "this object"),
          @Param(name = "patches",
              type = SkylarkList.class, generic1 = String.class, defaultValue = "[]",
              doc = "The list of patchfiles to apply, relative to the current config file."
                  + "The files will be applied relative to the checkout dir and the leading path"
                  + "component will be stripped (-p1)."),
      },
      objectType = PatchModule.class, useLocation = true)
  public static final BuiltinFunction APPLY = new BuiltinFunction("apply") {
    @SuppressWarnings("unused")
    public PatchTransformation invoke(
        PatchModule self,
        SkylarkList patches,
        Location location) throws EvalException {
      ImmutableList.Builder<ConfigFile<?>> builder = ImmutableList.builder();
      for (String patch : Type.STRING_LIST.convert(patches, "patches")) {
        builder.add(self.resolve(patch, location));
      }
      return new PatchTransformation(
          builder.build(), self.generalOptions, /*reverse=*/ false);
    }
  };

  private ConfigFile<?> resolve(String path, Location location) throws EvalException {
    try {
      return configFile.resolve(path);
    } catch (CannotResolveLabel e) {
      throw new EvalException(location, "Failed to resolve patch: " + path, e);
    }
  }

  @Override
  public void setOptions(Options options) {
    generalOptions = options.get(GeneralOptions.class);
  }
}
