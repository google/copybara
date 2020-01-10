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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.common.base.CharMatcher;
import com.google.common.io.ByteSink;
import com.google.common.io.MoreFiles;
import com.google.common.primitives.Bytes;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A starlark construct to download remote files via Http.
 */
@SkylarkModule(
    name = "remote_http_file",
    documented = false,
    doc = "A file loaded via http(s). This is experimental.",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
public abstract class RemoteHttpFile implements StarlarkValue {
  protected final String reference;
  protected final String extension;
  private final HttpTransport transport;
  private final Path storageDir;
  private final Console console;
  protected final Profiler profiler;

  Optional<Path> file = Optional.empty();
  Optional<String> sha256 = Optional.empty();
  boolean downloaded = false;

  protected RemoteHttpFile(
      Path storageDir, String reference, String extension, HttpTransport transport,
      Console console, Profiler profiler) {
    this.storageDir = checkNotNull(storageDir);
    this.reference = checkNotNull(reference);
    this.extension = checkNotNull(extension);
    this.transport = checkNotNull(transport);
    this.console = checkNotNull(console);
    this.profiler = checkNotNull(profiler);
  }

  /**
   * Obtain the Url to download the file from
   */
  protected abstract URL getRemote() throws ValidationException;

  protected synchronized void download() throws RepoException, ValidationException {
    if (downloaded) {
      return;
    }
    URL remote = getRemote();
    try {
      HttpRequest req = transport.createRequestFactory().buildGetRequest(new GenericUrl(remote));
      Path newFile = storageDir.resolve(String.format("%s.%s", CharMatcher.anyOf("/\\\n\t ")
          .replaceFrom(reference, '_'), extension));
      console.progressFmt("Fetching %s", remote);
      ByteSink sink = MoreFiles.asByteSink(newFile);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try(ProfilerTask task = profiler.start("remote_file_" + remote)) {
        try (DigestInputStream is = new DigestInputStream(req.execute().getContent(), digest)) {
          MoreFiles.createParentDirectories(newFile);
          sink.writeFrom(is);
          sha256 = Optional.of(Bytes.asList(is.getMessageDigest().digest()).stream()
              .map(b -> String.format("%02X", b)).collect(Collectors.joining()).toLowerCase());
        }
        file = Optional.of(newFile);
        downloaded = true;
      }
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RepoException(String.format("Error downloading %s", remote), e);
    }
  }

  @SkylarkCallable(
      name = "sha256",
      documented = false,
      doc = "Sha256 of the file.")
  public String getSha256() throws RepoException, ValidationException {
    download();
    return sha256.get();
  }

  @SkylarkCallable(
      name = "contents",
      documented = false,
      doc = "Contents of the file.")
  public String getContents() throws RepoException, ValidationException {
    download();
    try {
      return new String(Files.readAllBytes(file.get()), UTF_8);
    } catch (IOException e) {
      throw new RepoException(String.format("Error reading %s", file.get()), e);
    }
  }
}
