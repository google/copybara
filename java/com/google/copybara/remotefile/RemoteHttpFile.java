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
import com.google.common.primitives.Bytes;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Collectors;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** A starlark construct to download remote files via Http. */
@StarlarkBuiltin(
    name = "remote_http_file",
    documented = false,
    doc = "A file loaded via http(s). This is experimental.")
public abstract class RemoteHttpFile implements StarlarkValue {
  protected final String reference;
   private final HttpStreamFactory transport;
  private final Console console;
  protected final Profiler profiler;

  Optional<String> sha256 = Optional.empty();
  boolean downloaded = false;

  protected RemoteHttpFile(
      String reference, HttpStreamFactory transport, Console console, Profiler profiler) {
    this.reference = checkNotNull(reference);
    this.transport = checkNotNull(transport);
    this.console = checkNotNull(console);
    this.profiler = checkNotNull(profiler);
  }

  /**
   * Obtain the Url to download the file from
   */
  protected abstract URL getRemote() throws ValidationException;

  /**
   * Sink that receives the downloaded files.
   */
  protected abstract ByteSink getSink() throws ValidationException;


  protected synchronized void download() throws RepoException, ValidationException {
    if (downloaded) {
      return;
    }
    URL remote = getRemote();
    try {
      console.progressFmt("Fetching %s", remote);
      ByteSink sink = getSink();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (ProfilerTask task = profiler.start("remote_file_" + remote)) {
        try (DigestInputStream is = new DigestInputStream(transport.open(remote), digest)) {
          sink.writeFrom(is);
          sha256 = Optional.of(Bytes.asList(is.getMessageDigest().digest()).stream()
              .map(b -> String.format("%02X", b)).collect(Collectors.joining()).toLowerCase());
        }
        downloaded = true;
      }
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RepoException(String.format("Error downloading %s", remote), e);
    }
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "sha256",
      documented = false,
      doc = "Sha256 of the file.")
  public String getSha256() throws RepoException, ValidationException {
    download();
    return sha256.get();
  }

}
