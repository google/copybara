/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.MoreFiles;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** An Origin class for remote files */
class RemoteArchiveOrigin implements Origin<RemoteArchiveRevision> {

  private static final String LABEL_NAME = "RemoteArchiveOrigin";

  final String fileType;
  private final Author author;
  private final String message;
  private final HttpStreamFactory transport;
  private final Profiler profiler;
  private final RemoteFileOptions remoteFileOptions;

  RemoteArchiveOrigin(
      String fileType,
      Author author,
      String message,
      HttpStreamFactory transport,
      Profiler profiler,
      RemoteFileOptions remoteFileOptions) {
    this.fileType = fileType;
    this.author = author;
    this.message = message;
    this.transport = transport;
    this.profiler = profiler;
    this.remoteFileOptions = remoteFileOptions;
  }

  @Override
  public RemoteArchiveRevision resolve(String reference) throws RepoException, ValidationException {
    checkNotNull(reference);
    return new RemoteArchiveRevision(Path.of(reference));
  }

  @Override
  public Reader<RemoteArchiveRevision> newReader(Glob originFiles, Authoring authoring) {
    return new Reader<RemoteArchiveRevision>() {
      @Override
      public void checkout(RemoteArchiveRevision ref, Path workdir) throws ValidationException {
        try {
          // TODO(joshgoldman): Add richer ref object and ability to restrict download by host/url
          URL url = new URL(ref.path.toString());
          InputStream returned = transport.open(new URL(ref.path.toString()));
          try (ProfilerTask ignored = profiler.start("remote_file_" + url);
              ZipInputStream zipInputStream = remoteFileOptions.getZipInputStream(returned)) {
            ZipEntry zipEntry;
            while (((zipEntry = zipInputStream.getNextEntry()) != null)) {
              if (originFiles
                      .relativeTo(workdir.toAbsolutePath())
                      .matches(workdir.resolve(Path.of(zipEntry.getName())))
                  && !zipEntry.isDirectory()) {
                Files.createDirectories(workdir.resolve(zipEntry.getName()).getParent());
                MoreFiles.asByteSink(workdir.resolve(zipEntry.getName())).writeFrom(zipInputStream);
              }
            }
          }
        } catch (IOException e) {
          throw new ValidationException(
              String.format("Could not unzip file: %s \n%s", ref.path, e.getMessage()));
        }
      }

      @Override
      public ChangesResponse<RemoteArchiveRevision> changes(
          RemoteArchiveRevision fromRef, RemoteArchiveRevision toRef) throws RepoException {
        return ChangesResponse.forChanges(ImmutableList.of(change(toRef)));
      }

      @Override
      public Change<RemoteArchiveRevision> change(RemoteArchiveRevision ref) throws RepoException {
        return new Change<>(ref, author, message, ref.readTimestamp(), ImmutableListMultimap.of());
      }

      @Override
      public void visitChanges(RemoteArchiveRevision start, ChangesVisitor visitor)
          throws RepoException {
        visitor.visit(change(start));
      }
    };
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
