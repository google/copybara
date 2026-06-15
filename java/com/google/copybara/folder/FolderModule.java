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
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.FileUtil.SymlinkMode;
import java.nio.file.FileSystem;
import java.util.Optional;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/** Main module that groups all the functions related to folders. */
@StarlarkBuiltin(name = "folder", doc = "Module for dealing with local filesystem folders")
public class FolderModule implements StarlarkValue {

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
  @StarlarkMethod(name = DESTINATION_VAR,
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
  @StarlarkMethod(
      name = "origin",
      doc =
          "A folder origin is a origin that uses a folder as input. The folder is specified via "
              + "the source_ref argument.",
      parameters = {
        @Param(
            name = "materialize_outside_symlinks",
            doc = "DEPRECATED - equivalent to outside_symlinks_mode='MATERIALIZE'",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
        @Param(
            name = "inside_symlinks_mode",
            doc =
                "How to handle symlinks pointing inside the origin folder. Possible values:"
                    + " 'COPY_AS_IS' (copy the symlink as-is), 'MATERIALIZE' (copy the content of"
                    + " the target instead of the symlink), 'IGNORE' (ignore the symlink), 'FAIL'"
                    + " (fail the operation). Defaults to 'COPY_AS_IS'.",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
        @Param(
            name = "outside_symlinks_mode",
            doc =
                "How to handle symlinks pointing outside the origin folder. See"
                    + " inside_symlinks_mode for possible values. Defaults to 'FAIL'.",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
        @Param(
            name = "broken_symlinks_mode",
            doc =
                "How to handle broken symlinks. See inside_symlinks_mode for possible values"
                    + " (except 'MATERIALIZE', which is invalid for broken symlinks). Defaults to"
                    + " 'FAIL'.",
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
      })
  @UsesFlags(FolderOriginOptions.class)
  public FolderOrigin origin(
      Object materializeOutsideSymlinks,
      Object insideSymlinksMode,
      Object outsideSymlinksMode,
      Object brokenSymlinksMode)
      throws EvalException {
    boolean materializeOutsideSymlinksSet = materializeOutsideSymlinks != Starlark.NONE;
    boolean ignoreInvalidSymlinksSet = originOptions.ignoreInvalidSymlinks != null;
    boolean modernSymlinkOptionsSet =
        insideSymlinksMode != Starlark.NONE
            || outsideSymlinksMode != Starlark.NONE
            || brokenSymlinksMode != Starlark.NONE;

    if (materializeOutsideSymlinksSet) {
      generalOptions
          .console()
          .warn(
              "folder.origin(materialize_outside_symlinks = ...) is deprecated. Use"
                  + " outside_symlinks_mode instead.");
    }
    if (ignoreInvalidSymlinksSet) {
      generalOptions
          .console()
          .warn(
              "--folder-origin-ignore-invalid-symlinks is deprecated. Use"
                  + " folder.origin(outside_symlinks_mode = ..., broken_symlinks_mode = ...)"
                  + " instead.");
    }
    if (modernSymlinkOptionsSet && (materializeOutsideSymlinksSet || ignoreInvalidSymlinksSet)) {
      throw Starlark.errorf(
          "Cannot mix deprecated symlink configuration ('materialize_outside_symlinks' Starlark"
              + " parameter or '--folder-origin-ignore-invalid-symlinks' CLI flag) with new symlink"
              + " mode parameters ('inside_symlinks_mode', 'outside_symlinks_mode',"
              + " 'broken_symlinks_mode')");
    }

    CopySymlinkStrategy symlinkStrategy;
    if (modernSymlinkOptionsSet) {
      try {
        SymlinkMode inside =
            insideSymlinksMode == Starlark.NONE
                ? SymlinkMode.COPY_AS_IS
                : SymlinkMode.valueOf((String) insideSymlinksMode);
        SymlinkMode outside =
            outsideSymlinksMode == Starlark.NONE
                ? SymlinkMode.FAIL
                : SymlinkMode.valueOf((String) outsideSymlinksMode);
        SymlinkMode broken =
            brokenSymlinksMode == Starlark.NONE
                ? SymlinkMode.FAIL
                : SymlinkMode.valueOf((String) brokenSymlinksMode);
        symlinkStrategy = new CopySymlinkStrategy(inside, outside, broken);
      } catch (IllegalArgumentException e) {
        throw Starlark.errorf("Invalid symlink configuration: %s", e.getMessage());
      }
    } else {
      boolean materializeOutside =
          materializeOutsideSymlinksSet && (Boolean) materializeOutsideSymlinks;
      boolean ignoreInvalid = ignoreInvalidSymlinksSet && originOptions.ignoreInvalidSymlinks;
      symlinkStrategy =
          new CopySymlinkStrategy(
              /* inside= */ SymlinkMode.COPY_AS_IS,
              /* outside= */ ignoreInvalid
                  ? SymlinkMode.IGNORE
                  : (materializeOutside ? SymlinkMode.MATERIALIZE : SymlinkMode.FAIL),
              /* broken= */ ignoreInvalid ? SymlinkMode.IGNORE : SymlinkMode.FAIL);
    }

    FileSystem fs = generalOptions.getFileSystem();
    return new FolderOrigin(
        fs,
        Author.parse(originOptions.author),
        originOptions.message,
        generalOptions.getCwd(),
        symlinkStrategy,
        Optional.ofNullable(originOptions.version));
  }

}
