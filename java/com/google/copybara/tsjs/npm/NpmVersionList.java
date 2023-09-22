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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import net.starlark.java.eval.StarlarkValue;

/** Fetches versions available for a given package in the NPM Registry */
public class NpmVersionList implements VersionList, StarlarkValue, LabelsAwareModule {
  private final NpmPackageIdentifier pkg;
  private final Optional<String> listVersionsUrl;
  private final RemoteFileOptions remoteFileOptions;

  private static final GsonFactory GSON_FACTORY = GsonFactory.getDefaultInstance();

  public static NpmVersionList forPackage(String packageName, RemoteFileOptions remoteFileOptions)
      throws ValidationException {
    return new NpmVersionList(NpmPackageIdentifier.fromPackage(packageName), remoteFileOptions);
  }

  private NpmVersionList(NpmPackageIdentifier pkg, RemoteFileOptions remoteFileOptions) {
    this.pkg = pkg;
    String endpoint = pkg.toHumanReadableName();
    // returns JSON listing high-level package information, including distribution info for all
    // published versions
    this.listVersionsUrl = Optional.of(String.format("https://registry.npmjs.com/%s/", endpoint));
    // Specific versions can be listed using https://registry.npmjs.com/%s/<version> where <version>
    // can sometimes be specific dist tags (e.g. latest).
    this.remoteFileOptions = remoteFileOptions;
  }

  private String executeHttpQuery(String url) throws ValidationException {
    try (InputStream inputStream = remoteFileOptions.getTransport().open(new URL(url))) {
      return new String(inputStream.readAllBytes(), UTF_8);
    } catch (IOException | ValidationException e) {
      // TODO can we detect a 404? this would indicate some form of validation problem with user
      // input, vs a repoexception for something probably broken with the registry itself.
      throw new ValidationException(
          String.format(
              "Failed to query registry.npmjs.com for %s (URL: %s)",
              pkg.toHumanReadableName(), url),
          e);
    }
  }

  private <T> T executeHttpQuery(String url, Class<T> cls)
      throws RepoException, ValidationException {
    String jsonString = executeHttpQuery(url);
    try {
      return cls.cast(GSON_FACTORY.createJsonParser(jsonString).parse(cls, true));
    } catch (Exception e) {
      throw new RepoException(
          String.format("Failed to parse NPM registry response for version list at %s", url), e);
    }
  }

  @Override
  public ImmutableSet<String> list() throws RepoException, ValidationException {
    NpmVersionListResponseObject r = listVersions();
    return ImmutableSet.copyOf(r.getAllVersions());
  }

  public NpmVersionListResponseObject listVersions() throws RepoException, ValidationException {
    return executeHttpQuery(listVersionsUrl.get(), NpmVersionListResponseObject.class);
  }
}
