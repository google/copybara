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
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.CommandOutputWithStatus;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

  Optional<Path> file = Optional.empty();

  protected RemoteHttpFile(
      Path storageDir, String reference, String extension, HttpTransport transport,
      Console console) {
    this.storageDir = checkNotNull(storageDir);
    this.reference = checkNotNull(reference);
    this.extension = checkNotNull(extension);
    this.transport = checkNotNull(transport);
    this.console = checkNotNull(console);
  }

  /**
   * Obtain the Url to download the file from
   */
  protected abstract URL getRemote() throws ValidationException;

  protected synchronized void download() throws RepoException, ValidationException {
    if (file.isPresent()) {
      return;
    }
    URL remote = getRemote();
    try {
      HttpRequest req = transport.createRequestFactory().buildGetRequest(new GenericUrl(remote));
      Path newFile = storageDir.resolve(String.format("%s.%s", CharMatcher.anyOf("/\\\n\t ")
          .replaceFrom(reference, '_'), extension));
      console.progressFmt("Fetching %s", remote);
      try (InputStream is = req.execute().getContent()) {
        MoreFiles.createParentDirectories(newFile);
        MoreFiles.asByteSink(newFile).writeFrom(is);
      }
      file = Optional.of(newFile);
    } catch (IOException e) {
      throw new RepoException(String.format("Error downloading %s", remote), e);
    }
  }

  @SkylarkCallable(
      name = "sha256",
      documented = false,
      doc = "Sha256 of the file.")
  public String getSha256() throws RepoException, ValidationException {
    download();
    try {
      CommandOutputWithStatus result = new CommandRunner(new Command(
          new String[] {"sha256sum", file.get().toAbsolutePath().toString()}))
          .execute();
      return Iterables.getFirst(Splitter.on(' ').limit(2).split(result.getStdout()), "");
    } catch (CommandException e) {
      throw new RepoException(String.format("Error computing sha256 of %s", file.get()), e);
    }
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
