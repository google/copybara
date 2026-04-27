/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.go;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.exception.CannotResolveRevisionException;
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

/** Object used to turn a ref into a version listed in go proxy. */
public class GoProxyVersionResolver implements VersionResolver {
  private final String module;
  private final RemoteFileOptions remoteFileOptions;
  @Nullable private final AuthInterceptor auth;

  public GoProxyVersionResolver(
      String module, RemoteFileOptions remoteFileOptions, @Nullable AuthInterceptor auth) {
    this.module = module;
    this.remoteFileOptions = remoteFileOptions;
    this.auth = auth;
  }

  private String resolveFromVersionList(String ref) throws ValidationException {
    ImmutableSet<String> versions =
        GoProxyVersionList.forVersion(module, remoteFileOptions, auth).list();
    // Go proxy list endpoint only returns versions with a "v" prefix.
    // Optimistically add "v" to ref if absent and before trying resolve it as a version.
    String proxyRef = ref.startsWith("v") ? ref : "v" + ref;
    if (versions.contains(proxyRef)) {
      return proxyRef;
    }
    throw new CannotResolveRevisionException(
        String.format(
            "Failed to resolve ref '%s' as a version. Available versions: %s", ref, versions));
  }

  private String resolveFromInfo(String ref) throws ValidationException {
    return Iterables.getOnlyElement(
        GoProxyVersionList.forInfo(module, ref, remoteFileOptions, auth).list());
  }

  /**
   * Will try to load go proxy version that the {@code ref} points to in go proxy. First with ref as
   * a version literal and if that does not work then try to resolve it as a .info reference.
   *
   * @param ref reference to version known to go proxy (e.g., "1.2.3", "v1.2.3", "main", <hash>).
   */
  private String resolve(String ref) throws ValidationException {
    try {
      return resolveFromVersionList(ref);
    } catch (ValidationException e) {
      // Failed to resolve ref as a version. Ref could be a pseudo-version or branch/commit hash.
      // trying to resolve via .info.
    }
    try {
      return resolveFromInfo(ref);
    } catch (ValidationException e) {
      // Failed to resolve ref as a .info reference. Check for missing "v" prefix and try again.
      if (!ref.startsWith("v")) {
        return resolveFromInfo("v" + ref);
      }
      throw e;
    }
  }

  /**
   * Uses go proxy to look up {@code ref} as an offered version or a known branch/revision with a
   * released tied to it.
   *
   * @param ref e.g. v1.1.1 or main.info
   * @param assemblyStrategy how to assemble the url after resolving {@code ref}.
   */
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
