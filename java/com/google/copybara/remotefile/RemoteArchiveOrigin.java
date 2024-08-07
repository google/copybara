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

import static com.google.copybara.Origin.Reader.ChangesResponse.noChanges;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.MoreFiles;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.remotefile.extractutil.ExtractUtil;
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
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

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

  private Optional<String> getUrlAssemblyStrategy(String label) {
    try {
      return Optional.ofNullable(resolveURLTemplate(archiveSourceUrl, label));
    } catch (LabelNotFoundException e) {
      return Optional.empty();
    }
  }

  /**
   * This is used to resolve new refs.
   *
   * @param versionRef the version to target. If left null/empty, we will deduce intended version
   *     from what was supplied to the RemoteArchiveOrigin constructor.
   */
  @Override
  public RemoteArchiveRevision resolve(@Nullable String versionRef)
      throws RepoException, ValidationException {
    boolean canUseResolverOnCliRef =
        this.generalOptions.isVersionSelectorUseCliRef() || this.generalOptions.isForced();
    this.generalOptions
        .console()
        .warnFmtIf(
            !canUseResolverOnCliRef
                && !Strings.isNullOrEmpty(versionRef)
                && this.versionResolver != null,
            "Resolve version ref for '%s' was detected, but will not apply the supplied resolver."
                + " Consider setting --force or --use-version-selector-ref to true.",
            versionRef);

    // It's a versionless import.
    if (versionList == null || versionSelector == null) {
      return new RemoteArchiveRevision(new RemoteArchiveVersion(archiveSourceUrl, versionRef));
    }

    try {
      String version =
          versionSelector
              .select(versionList, versionRef, generalOptions.console())
              .orElseThrow(() -> new ValidationException("Version selector returned no results."));

      if (canUseResolverOnCliRef
          && !Strings.isNullOrEmpty(versionRef)
          && this.versionResolver != null) {
        return (RemoteArchiveRevision)
            this.versionResolver.resolve(version, this::getUrlAssemblyStrategy);
      }
      RemoteArchiveVersion remoteArchiveVersion =
          new RemoteArchiveVersion(resolveURLTemplate(archiveSourceUrl, version), version);
      return new RemoteArchiveRevision(remoteArchiveVersion);
    } catch (LabelNotFoundException | IllegalArgumentException | ValidationException e) {
      throw new ValidationException(
          String.format(
              "Could not resolve archive URL template %s with error '%s' and the cause (if any) was"
                  + " '%s'",
              archiveSourceUrl, e.getMessage(), e.getCause()));
    }
  }

  /** This is used to resolve the baseline. */
  @Override
  public RemoteArchiveRevision resolveLastRev(String versionRef)
      throws RepoException, ValidationException {
    Preconditions.checkState(
        !Strings.isNullOrEmpty(versionRef),
        "Last migrated revision reference must not be null or empty.");
    if (versionResolver != null) {
      return (RemoteArchiveRevision)
          this.versionResolver.resolve(versionRef, this::getUrlAssemblyStrategy);
    }

    this.generalOptions
        .console()
        .warnFmt(
            "No version resolver was supplied, will attempt to resolve baseline version by url"
                + " template.");
    String fullUrl =
        this.getUrlAssemblyStrategy(versionRef)
            .orElseThrow(
                () ->
                    new ValidationException(
                        String.format(
                            "Could not construct remote archive version from url='%s' and"
                                + " ref='%s'",
                            archiveSourceUrl, versionRef)));
    return new RemoteArchiveRevision(new RemoteArchiveVersion(fullUrl, versionRef));
  }

  @Override
  public Reader<RemoteArchiveRevision> newReader(Glob originFiles, Authoring authoring) {
    return new Reader<RemoteArchiveRevision>() {

      private void writeArchiveAsIs(RemoteArchiveRevision ref, Path workdir, InputStream returned)
          throws IOException {
        String filename = ref.getUrl().substring(archiveSourceUrl.lastIndexOf("/") + 1);
        MoreFiles.asByteSink(workdir.resolve(filename)).writeFrom(returned);
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
              ExtractUtil.extractArchive(
                  returned, workdir, RemoteFileType.toExtractType(remoteFileType), originFiles);
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
        if (versionSelector == null) {
          return ChangesResponse.forChanges(ImmutableList.of(change(toRef)));
        }

        try {
          Optional<String> selectedVersion =
              versionSelector.select(
                  new VersionList.SetVersionList(
                      ImmutableSet.of(toRef.fixedReference(), fromRef.fixedReference())),
                  /* requestedRef= */ null,
                  generalOptions.console());

          if (selectedVersion.isPresent()
              && selectedVersion.get().equals(fromRef.fixedReference())) {
            generalOptions
                .console()
                .warnFmt(
                    "The baseline ref [%s] is newer than incoming ref [%s]. The change response"
                        + " will have no changes generated because the current baseline is newer.",
                    fromRef.fixedReference(), toRef.fixedReference());
            return noChanges(EmptyReason.TO_IS_ANCESTOR);
          }
        } catch (ValidationException e) {
          generalOptions
              .console()
              .warnFmt(
                  "An error has occurred while validating the order of changes between %s and %s:"
                      + " '%s'. Defaulting to a changelist with only the incoming ref.",
                  fromRef.fixedReference(), toRef.fixedReference(), e.getMessage());
        }
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
