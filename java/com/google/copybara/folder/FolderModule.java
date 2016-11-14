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

package com.google.copybara.folder;

import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.authoring.Author;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import java.nio.file.FileSystem;

/**
 * Main module that groups all the functions related to folders.
 */
@SkylarkModule(
    name = "folder",
    doc = "Module for dealing with local filesytem folders",
    category = SkylarkModuleCategory.BUILTIN)
public class FolderModule implements OptionsAwareModule {

  private static final String DESTINATION_VAR = "destination";

  private Options options;

  @SuppressWarnings("unused")
  @SkylarkSignature(name = DESTINATION_VAR, returnType = Destination.class,
      doc = "A folder destination is a destination that puts the output in a folder",
      parameters = {
          @Param(name = "self", type = FolderModule.class, doc = "this object"),
      },
      objectType = FolderModule.class, useLocation = true, useEnvironment = true)
  @UsesFlags(FolderDestinationOptions.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction(DESTINATION_VAR) {
    @SuppressWarnings("unused")
    public Destination invoke(FolderModule self, Location location, Environment env)
        throws EvalException {

      return new FolderDestination(self.options.get(GeneralOptions.class),
          self.options.get(FolderDestinationOptions.class));
    }
  };


  @SuppressWarnings("unused")
  @SkylarkSignature(name = "origin", returnType = FolderOrigin.class,
      doc = "A folder origin is a origin that uses a folder as input",
      parameters = {
          @Param(name = "self", type = FolderModule.class, doc = "this object"),
          @Param(name = "materialize_outside_symlinks", type = Boolean.class,
              doc = "By default folder.origin will refuse any symlink in the migration folder"
                  + " that is an absolute symlink or that refers to a file outside of the folder."
                  + " If this flag is set, it will materialize those symlinks as regular files"
                  + " in the checkout directory.", defaultValue = "False"),
      },
      objectType = FolderModule.class, useLocation = true, useEnvironment = true)
  @UsesFlags(FolderOriginOptions.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    @SuppressWarnings("unused")
    public FolderOrigin invoke(FolderModule self, Boolean materializeOutsideSymlinks,
        Location location, Environment env) throws EvalException {

      GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
      // Lets assume we are in the same filesystem for now...
      FileSystem fs = generalOptions.getFileSystem();
      return new FolderOrigin(fs,
          Author.parse(location, self.options.get(FolderOriginOptions.class).author),
          self.options.get(FolderOriginOptions.class).message,
          self.options.get(GeneralOptions.class).getCwd(),
          materializeOutsideSymlinks);
    }
  };

  @Override
  public void setOptions(Options options) {
    this.options = options;
  }
}
