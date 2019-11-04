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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.GeneralOptions;
import com.google.copybara.authoring.Author;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.EvalException;
import java.nio.file.FileSystem;

/** Main module that groups all the functions related to folders. */
@SkylarkModule(
    name = "folder",
    doc = "Module for dealing with local filesystem folders",
    category = SkylarkModuleCategory.BUILTIN)
public class FolderModule implements SkylarkValue {

  private static final String DESTINATION_VAR = "destination";

  private final FolderOriginOptions originOptions;
  private final FolderDestinationOptions destinationOptions;
  private final GeneralOptions generalOptions;

  public FolderModule(
      FolderOriginOptions originOptions,
      FolderDestinationOptions destinationOptions,
      GeneralOptions generalOptions) {
    this.originOptions = checkNotNull(originOptions);
    this.destinationOptions = checkNotNull(destinationOptions);
    this.generalOptions = checkNotNull(generalOptions);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = DESTINATION_VAR,
      doc = "A folder destination is a destination that puts the output in a folder. It can be used"
          + " both for testing or real production migrations."
          + "Given that folder destination does not support a lot of the features of real VCS, "
          + "there are some limitations on how to use it:"
          + "<ul>"
          + "<li>It requires passing a ref as an argument, as there is no way of calculating "
          + "previous migrated changes. Alternatively, --last-rev can be used, which could migrate "
          + "N changes."
          + "<li>Most likely, the workflow should use 'SQUASH' mode, as history is not supported."
          + "<li>If 'ITERATIVE' mode is used, a new temp directory will be created for each change "
          + "migrated."
          + "</ul>"
      )
  @UsesFlags(FolderDestinationOptions.class)
  public FolderDestination destination() {
    return new FolderDestination(generalOptions, destinationOptions);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(name = "origin",
      doc = "A folder origin is a origin that uses a folder as input. The folder is specified via "
          + "the source_ref argument.",
      parameters = {
          @Param(name = "materialize_outside_symlinks", type = Boolean.class,
              doc = "By default folder.origin will refuse any symlink in the migration folder"
                  + " that is an absolute symlink or that refers to a file outside of the folder."
                  + " If this flag is set, it will materialize those symlinks as regular files"
                  + " in the checkout directory.", defaultValue = "False", named = true),
      },
      useLocation = true)
  @UsesFlags(FolderOriginOptions.class)
  public FolderOrigin origin(Boolean materializeOutsideSymlinks, Location location)
      throws EvalException {
     // Lets assume we are in the same filesystem for now...
    FileSystem fs = generalOptions.getFileSystem();
    return new FolderOrigin(fs,
        Author.parse(location, originOptions.author),
        originOptions.message,
        generalOptions.getCwd(),
        materializeOutsideSymlinks,
        originOptions.ignoreInvalidSymlinks);
  }

}
