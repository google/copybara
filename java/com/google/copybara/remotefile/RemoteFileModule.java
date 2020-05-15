/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.remotefile;

import static com.google.devtools.build.lib.syntax.Starlark.errorf;

import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.skylarkinterface.StarlarkMethod;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.Arrays;

/** Module for helpers to load files from a source other than the origin. Use with caution. */
@StarlarkBuiltin(
    name = "remotefiles",
    doc = "Functions to access remote files not in either repo.",
    documented = false,
    category = StarlarkDocumentationCategory.BUILTIN)
@UsesFlags(RemoteFileOptions.class)
public class RemoteFileModule implements LabelsAwareModule, StarlarkValue {

  protected final Options options;

  public RemoteFileModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "github_archive",
      doc = "A tarball for a specific SHA1 on GitHub. Experimental.",
      documented = false,
      parameters = {
          @Param(
              name = "project",
              type = String.class,
              named = true,
              defaultValue = "[]",
              doc = "The GitHub project from which to load the file, e.g. google/copybara"),
          @Param(
              name = "revision",
              type = String.class,
              named = true,
              generic1 = String.class,
              defaultValue = "[]",
              doc = "The revision to download from the project, typically a commit SHA1."),
          @Param(
              name = "type",
              type = String.class,
              named = true,
              generic1 = String.class,
              defaultValue = "'TARBALL'",
              doc = "Archive type to download, options are 'TARBALL' or 'ZIP'."),
          })
  @UsesFlags(RemoteFileOptions.class)
  public GithubArchive gitHubTarball(
      String project,
      String revision,
      String type)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    RemoteFileOptions remoteFileOptions = options.get(RemoteFileOptions.class);
    try {
      return new GithubArchive(project,
          revision,
          Enums.getIfPresent(GithubArchive.Type.class, type).toJavaUtil()
              .orElseThrow(() -> errorf("Unsupported archive type: '%s'. "
                      + "Supported values: %s", type, Arrays.asList(GithubArchive.Type.values()))),
          remoteFileOptions.getTransport(),
          generalOptions.profiler(),
          generalOptions.console());
    } catch (ValidationException e) {
      throw new EvalException("Error setting up remote http file:" + e.getMessage());
    }
  }
}
