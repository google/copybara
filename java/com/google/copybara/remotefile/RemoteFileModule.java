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

import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.io.IOException;

/** Module for helpers to load files from a source other than the origin. Use with caution. */
@SkylarkModule(
    name = "remotefiles",
    doc = "Functions to access remote files not in either repo.",
    documented = false,
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(RemoteFileOptions.class)
public class RemoteFileModule implements LabelsAwareModule, StarlarkValue {

  protected final Options options;

  public RemoteFileModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "github_tarball",
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
      })
  @UsesFlags(RemoteFileOptions.class)
  public GithubTarball gitHubTarball(
      String project,
      String revision)
      throws EvalException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    RemoteFileOptions remoteFileOptions = options.get(RemoteFileOptions.class);
    try {
      return new GithubTarball(project,
          revision,
          remoteFileOptions.getStorageDir(),
          remoteFileOptions.getTransport(),
          generalOptions.profiler(),
          generalOptions.console());
    } catch (IOException | ValidationException e) {
      throw new EvalException("Error setting up remote http file:" + e.getMessage());
    }
  }
}
