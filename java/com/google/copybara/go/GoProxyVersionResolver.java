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

package com.google.copybara.go;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.RemoteArchiveRevision;
import com.google.copybara.remotefile.RemoteArchiveVersion;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.revision.Revision;
import com.google.copybara.version.VersionResolver;
import java.util.Optional;
import java.util.function.Function;

/** Object used to turn a ref into a version listed in go proxy. */
public class GoProxyVersionResolver implements VersionResolver {
  private final String module;
  private final RemoteFileOptions remoteFileOptions;

  public GoProxyVersionResolver(String module, RemoteFileOptions remoteFileOptions) {
    this.module = module;
    this.remoteFileOptions = remoteFileOptions;
  }

  /**
   * Will try to load go proxy version that the {@code ref} points to in go proxy. First with ref as
   * a version literal and if that does not work then try to resolve it as a .info reference.
   *
   * @param ref reference to version known to go proxy.
   */
  private String resolve(String ref) throws ValidationException {
    try {
      // try to resolve as version.
      ImmutableSet<String> version =
          GoProxyVersionList.forVersion(this.module, this.remoteFileOptions).list();
      if (!version.contains(ref)) {
        throw new ValidationException(
            String.format("Could not locate version with ref '%s' as a version.", ref));
      }
      return ref;
    } catch (ValidationException e) {
      // Darn, it failed, try to resolve as .info
      return Iterables.getOnlyElement(
          GoProxyVersionList.forInfo(this.module, ref, this.remoteFileOptions).list());
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
}
