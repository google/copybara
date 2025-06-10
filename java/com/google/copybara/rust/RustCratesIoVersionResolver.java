/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.remotefile.RemoteArchiveRevision;
import com.google.copybara.remotefile.RemoteArchiveVersion;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.revision.Revision;
import com.google.copybara.version.VersionResolver;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Class that can resolve a ref to a Rust crate version from crates.io */
public class RustCratesIoVersionResolver implements VersionResolver {
  private final String crate;
  private final RemoteFileOptions remoteFileOptions;
  private final boolean matchPreReleaseVersions;
  @Nullable private final AuthInterceptor auth;

  public RustCratesIoVersionResolver(
      String crate,
      RemoteFileOptions remoteFileOptions,
      boolean matchPreReleaseVersions,
      @Nullable AuthInterceptor auth) {
    this.crate = crate;
    this.remoteFileOptions = remoteFileOptions;
    this.matchPreReleaseVersions = matchPreReleaseVersions;
    this.auth = auth;
  }

  private String resolve(String ref) throws ValidationException {
    ImmutableSet<String> versionList = ImmutableSet.of();
    try {
      versionList =
          RustCratesIoVersionList.forCrate(
                  this.crate,
                  this.remoteFileOptions,
                  matchPreReleaseVersions,
                  /* ignoreYankedVersions= */ false,
                  auth)
              .list();
      if (!versionList.contains(ref)) {
        throw new CannotResolveRevisionException(
            String.format("Could not locate version with ref '%s' as a version.", ref));
      }
    } catch (RepoException e) {
      throw new ValidationException(
          String.format(
              "There was an issue querying the crates.io index for ref %s. The version list fetched"
                  + " from crates.io was %s.",
              ref, versionList),
          e);
    }

    return ref;
  }

  @Override
  public Revision resolve(String ref, Function<String, Optional<String>> assemblyStrategy)
      throws ValidationException {
    String version = resolve(ref);
    String fullUrl =
        assemblyStrategy
            .apply(version)
            .orElseThrow(
                () ->
                    new ValidationException(
                        String.format(
                            "Failed to assemble url template with provided assembly strategy."
                                + " Provided ref = '%s' and resolved version = '%s'.",
                            ref, version)));
    RemoteArchiveVersion remoteArchiveVersion = new RemoteArchiveVersion(fullUrl, version);
    return new RemoteArchiveRevision(remoteArchiveVersion);
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    if (auth == null) {
      return ImmutableList.of();
    }
    return auth.describeCredentials();
  }
}
