/*
 * Copyright (C) 2024 Google LLC.
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

package com.google.copybara.tsjs.npm;

import com.google.common.collect.ImmutableList;
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

/** Object used to turn a ref into a version listed in the NPM registry. */
public class NpmVersionResolver implements VersionResolver {
  private final String packageName;
  private final String registryUrl;
  private final RemoteFileOptions remoteFileOptions;
  @Nullable private final AuthInterceptor auth;

  public NpmVersionResolver(
      String packageName,
      String registryUrl,
      RemoteFileOptions remoteFileOptions,
      @Nullable AuthInterceptor auth) {
    this.packageName = packageName;
    this.registryUrl = registryUrl;
    this.remoteFileOptions = remoteFileOptions;
    this.auth = auth;
  }

  /** Resolves the given reference as if it was an NPM Package version. */
  private NpmVersionInfo resolve(String ref) throws RepoException, ValidationException {
    // TODO depending on what ref could be, maybe ref could be semver-lang and that might resolve
    // to a bunch of versions?
    NpmVersionListResponseObject allVersions =
        NpmVersionList.forPackage(packageName, registryUrl, remoteFileOptions, auth).listVersions();
    if (ref != null) {
      if (!allVersions.getAllVersions().contains(ref)) {
        throw new CannotResolveRevisionException(
            String.format("Could not locate version with ref '%s' as a version.", ref));
      }
      return allVersions.getVersionInfo(ref);
    }
    // No ref should return latest version available?
    return allVersions.getLatestVersion();
  }

  /**
   * Uses the NPM registry to look up the distributed tarball for the given {@code ref}.
   *
   * @param ref e.g. 1.1.1
   * @param assemblyStrategy how to assemble the url after resolving {@code ref}.
   */
  @Override
  public Revision resolve(String ref, Function<String, Optional<String>> assemblyStrategy)
      throws ValidationException {
    try {
      NpmVersionInfo version = resolve(ref);
      RemoteArchiveVersion remoteArchiveVersion =
          new RemoteArchiveVersion(version.getTarball(), version.getVersion());
      return new RemoteArchiveRevision(remoteArchiveVersion);
    } catch (RepoException e) {
      // TODO should resolve also throw a repoexception?
      throw new ValidationException("repository error resolving reference", e);
    }
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    if (auth == null) {
      return ImmutableList.of();
    }
    return auth.describeCredentials();
  }
}
