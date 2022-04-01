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

import static net.starlark.java.eval.Starlark.errorf;

import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.authoring.Author;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.ValidationException;
import java.util.Arrays;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/** Module for helpers to load files from a source other than the origin. Use with caution. */
@StarlarkBuiltin(
    name = "remotefiles",
    doc = "Functions to access remote files not in either repo.",
    documented = false)
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
              named = true,
              defaultValue = "[]",
              doc = "The GitHub project from which to load the file, e.g. google/copybara"),
          @Param(
              name = "revision",
              named = true,
              defaultValue = "[]",
              doc = "The revision to download from the project, typically a commit SHA1."),
          @Param(
              name = "type",
              named = true,
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
      throw Starlark.errorf("Error setting up remote http file: %s", e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "origin",
      doc = "Defines a remote file origin. This is a WIP and experimental. Do not use. ",
      parameters = {
        @Param(
            name = "unpack_method",
            defaultValue = "None",
            doc =
                "The method by which to unpack the remote file. Currently 'zip' allowed. 'tar' and"
                    + " 'as-is' to be added soon.",
            named = true),
        @Param(
            name = "author",
            defaultValue = "'Copybara <noreply@copybara.io>'",
            doc = "Author to attribute the change to",
            named = true),
        // TODO(joshgoldman): support labels in addition to message
        @Param(
            name = "message",
            defaultValue = "'Placeholder message'",
            doc = "Message to attach to the change",
            named = true),
        @Param(
            name = "version_selector",
            defaultValue = "None",
            doc = "Object that contains version selecting logic",
            named = true),
        @Param(
            name = "base_url",
            named = true,
            doc = "base URL to construct the full URL",
            defaultValue = "None")
      })
  @UsesFlags(RemoteFileOptions.class)
  public RemoteArchiveOrigin remoteArchiveOrigin(
      String fileType,
      String author,
      String message,
      RemoteArchiveVersionSelector versionSelector,
      String baseUrl)
      throws EvalException, ValidationException {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    RemoteFileOptions remoteFileOptions = options.get(RemoteFileOptions.class);
    return new RemoteArchiveOrigin(
        fileType,
        Author.parse(author),
        message,
        remoteFileOptions.getTransport(),
        generalOptions.profiler(),
        remoteFileOptions,
        baseUrl,
        versionSelector);
  }

  @StarlarkMethod(
      name = "no_version_selector",
      doc = "A RemoteArchiveVersionSelector that ignores version and returns only base url",
      documented = false)
  public NoVersionSelector noVersionSelector() {
    return new NoVersionSelector();
  }
}
