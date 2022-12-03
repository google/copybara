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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.MoreFiles;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.revision.Change;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.copybara.util.Glob;
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionResolver;
import com.google.copybara.version.VersionSelector;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

/** An Origin class for remote files */
public class RemoteArchiveOrigin implements Origin<RemoteArchiveRevision> {

  private static final String LABEL_NAME = "RemoteArchiveOrigin";

  private final Author author;
  private final String message;
  private final GeneralOptions generalOptions;
  private final RemoteFileOptions remoteFileOptions;
  private final String archiveSourceUrl;
  private final VersionList versionList;
  private final VersionSelector versionSelector;
  private final RemoteFileType remoteFileType;
  private final VersionResolver versionResolver;

  RemoteArchiveOrigin(
      Author author,
      String message,
      GeneralOptions generalOptions,
      RemoteFileOptions remoteFileOptions,
      RemoteFileType remoteFileType,
      String archiveSourceUrl,
      VersionList versionList,
      VersionSelector versionSelector,
      VersionResolver versionResolver) {
    this.remoteFileType = remoteFileType;
    this.author = author;
    this.message = message;
    this.generalOptions = generalOptions;
    this.remoteFileOptions = remoteFileOptions;
    this.archiveSourceUrl = archiveSourceUrl;
    this.versionList = versionList;
    this.versionSelector = versionSelector;
    this.versionResolver = versionResolver;
  }

  private String resolveURLTemplate(String url, String version) throws LabelNotFoundException {
    return new LabelTemplate(url)
        .resolve(
            label -> {
              if (!label.equals("VERSION")) {
                throw new IllegalArgumentException(
                    String.format(
                        "Archive source templates only support '${VERSION}' labels, but found"
                            + " '%s'",
                        label));
              }
              return version;
            });
  }

  private RemoteArchiveRevision resolveWithResolver(
      @Nullable String version, Function<String, Optional<String>> urlAssemblyStrategy)
      throws ValidationException {
    Preconditions.checkArgument(
        this.versionResolver != null,
        "--version-selector-use-cli-ref or --forced is set to true, yet no version resolver was"
            + " provided.");
    return (RemoteArchiveRevision) this.versionResolver.resolve(version, urlAssemblyStrategy);
  }

  /**
   * @param versionRef the version to target. If left null/empty, we will deduce intended version
   *     from what was supplied to the RemoteArchiveOrigin constructor.
   */
  @Override
  public RemoteArchiveRevision resolve(@Nullable String versionRef)
      throws RepoException, ValidationException {
    // It's a versionless import.
    if (versionList == null || versionSelector == null) {
      return new RemoteArchiveRevision(new RemoteArchiveVersion(archiveSourceUrl, ""));
    }

    try {
      String version =
          versionSelector
              .select(versionList, versionRef, generalOptions.console())
              .orElseThrow(() -> new ValidationException("Version selector returned no results."));
      Function<String, Optional<String>> urlAssemblyStrategy =
          label -> {
            try {
              return Optional.ofNullable(resolveURLTemplate(archiveSourceUrl, label));
            } catch (LabelNotFoundException e) {
              return Optional.empty();
            }
          };

      if ((generalOptions.isVersionSelectorUseCliRef() || generalOptions.isForced())
          && !Strings.isNullOrEmpty(versionRef)) {
        return resolveWithResolver(version, urlAssemblyStrategy);
      }

      RemoteArchiveVersion remoteArchiveVersion =
          new RemoteArchiveVersion(resolveURLTemplate(archiveSourceUrl, version), version);
      return new RemoteArchiveRevision(remoteArchiveVersion);
    } catch (LabelNotFoundException | IllegalArgumentException | ValidationException e) {
      throw new ValidationException(
          String.format(
              "Could not resolve archive URL template %s with error '%s'",
              archiveSourceUrl, e.getMessage()));
    }
  }

  @Override
  public Reader<RemoteArchiveRevision> newReader(Glob originFiles, Authoring authoring) {
    return new Reader<RemoteArchiveRevision>() {

      private void writeArchiveAsIs(RemoteArchiveRevision ref, Path workdir, InputStream returned)
          throws IOException {
        String filename = ref.getUrl().substring(archiveSourceUrl.lastIndexOf("/") + 1);
        MoreFiles.asByteSink(workdir.resolve(filename)).writeFrom(returned);
      }

      private void writeArchiveByUnpacking(Path workdir, InputStream returned)
          throws IOException, ValidationException {
        ArchiveEntry archiveEntry;
        try (ArchiveInputStream inputStream =
            remoteFileOptions.createArchiveInputStream(returned, remoteFileType)) {
          while (((archiveEntry = inputStream.getNextEntry()) != null)) {
            if (!originFiles
                    .relativeTo(workdir.toAbsolutePath())
                    .matches(workdir.resolve(Path.of(archiveEntry.getName())))
                || archiveEntry.isDirectory()) {
              continue;
            }
            Files.createDirectories(workdir.resolve(archiveEntry.getName()).getParent());
            MoreFiles.asByteSink(workdir.resolve(archiveEntry.getName())).writeFrom(inputStream);
          }
        }
      }

      @Override
      public void checkout(RemoteArchiveRevision ref, Path workdir) throws ValidationException {
        try {
          // TODO(joshgoldman): Add richer ref object and ability to restrict download by host/url
          URL url = new URL(Objects.requireNonNull(ref.getUrl()));
          HttpStreamFactory transport = remoteFileOptions.getTransport();
          try (ProfilerTask ignored = generalOptions.profiler().start("remote_file_" + url);
              InputStream returned = transport.open(url)) {
            if (remoteFileType == RemoteFileType.AS_IS) {
              writeArchiveAsIs(ref, workdir, returned);
            } else {
              writeArchiveByUnpacking(workdir, returned);
            }
          }
        } catch (IOException e) {
          throw new ValidationException(
              String.format(
                  "Could not checkout archive file at %s: \n%s", ref.getUrl(), e.getMessage()));
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

  @Override
  public String getType() {
    return "remotefiles.origin";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        ImmutableSetMultimap.<String, String>builder()
            .put("type", getType())
            .put("url", archiveSourceUrl) // the unresolved url
            .putAll("root", originFiles.roots());
    return builder.build();
  }
}
