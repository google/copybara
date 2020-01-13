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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

/** A tarball for a given ref, downloaded from GitHub */
@SkylarkModule(
    name = "remote_http_file.GitHubTarball",
    category = SkylarkModuleCategory.BUILTIN,
    documented = false,
    doc = "A GitHub tarball that can be downloaded at the given revision.")
public class GithubTarball extends RemoteHttpFile implements StarlarkValue {

  private final String project;

  public GithubTarball(
      String project,
      String reference,
      Path storageDir,
      HttpStreamFactory transport,
      Profiler profiler,
      Console console) {
    super(storageDir, reference, "tar.gz", transport, console, profiler);
    this.project = checkNotNull(project);
  }

  @Override
  protected URL getRemote() throws ValidationException {
    try {
      // This is somewhat limited and does not support private repos. We can use
      // https://developer.github.com/v3/repos/contents/#get-archive-link if a use case for private
      // repos comes up.
      return new URL(String.format("https://github.com/%s/archive/%s.tar.gz", project, reference));
    } catch (MalformedURLException e) {
      throw new ValidationException(
          String.format("Error assembling URL for tarball of %s at %s", project, reference), e);
    }
  }
}
