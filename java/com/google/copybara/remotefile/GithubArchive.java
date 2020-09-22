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

import com.google.common.io.ByteSink;
import com.google.common.io.ByteStreams;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.util.console.Console;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** A tarball for a given ref, downloaded from GitHub */
@StarlarkBuiltin(
    name = "remote_http_file.GitHubArchive",
    documented = false,
    doc = "A GitHub archive that can be downloaded at the given revision. Only exposes the SHA256 "
        + "hash of the archive.")
public class GithubArchive extends RemoteHttpFile implements StarlarkValue {

  private final String project;
  private final Type fileType;

  public GithubArchive(
      String project,
      String reference,
      Type fileType,
      HttpStreamFactory transport,
      Profiler profiler,
      Console console) {
    super(reference, transport, console, profiler);
    this.project = checkNotNull(project);
    this.fileType = fileType;
  }

  @Override
  protected URL getRemote() throws ValidationException {
    try {
      // This is somewhat limited and does not support private repos. We can use
      // https://developer.github.com/v3/repos/contents/#get-archive-link if a use case for private
      // repos comes up.
      return new URL(String.format("https://github.com/%s/archive/%s.%s",
          project, reference, fileType.extension));
    } catch (MalformedURLException e) {
      throw new ValidationException(
          String.format("Error assembling URL for archive of %s at %s", project, reference), e);
    }
  }

  @Override
  protected ByteSink getSink() throws ValidationException {
    return NullByteSink.INSTANCE;
  }

  enum Type {
    TARBALL("tar.gz"),
    ZIP("zip");

    final String extension;

    Type(String extension) {
      this.extension = checkNotNull(extension);
    }
  }

  /**
   * We only need the hash of archives as we do not allow introspecting them.
   */
  private static final class NullByteSink extends ByteSink implements Serializable {
    private static final NullByteSink INSTANCE = new NullByteSink();

    @Override
    public OutputStream openStream() {
      return ByteStreams.nullOutputStream();
    }
  }

}
