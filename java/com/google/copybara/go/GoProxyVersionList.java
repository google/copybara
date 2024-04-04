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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionList;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkValue;

/** Used to fetch versions available for a given module at go proxy */
@StarlarkBuiltin(name = "goproxy_version_list", doc = "Fetch versions from goproxy")
public class GoProxyVersionList implements VersionList, StarlarkValue, LabelsAwareModule {
  private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
  private final Optional<String> listVersionsURL;
  private final Optional<String> latestVersionURL;
  private final Optional<String> dotInfoURL;
  private final RemoteFileOptions remoteFileOptions;
  private final String module;

  private static final GsonFactory GSON_FACTORY = GsonFactory.getDefaultInstance();

  public static GoProxyVersionList forInfo(
      String module, String dotInfo, RemoteFileOptions remoteFileOptions) {
    return new GoProxyVersionList(module, dotInfo, remoteFileOptions);
  }

  public static GoProxyVersionList forVersion(String module, RemoteFileOptions remoteFileOptions) {
    return new GoProxyVersionList(module, remoteFileOptions);
  }

  private GoProxyVersionList(String module, RemoteFileOptions remoteFileOptions) {
    this.module = module;
    this.dotInfoURL = Optional.empty();
    // returns plain text
    this.listVersionsURL =
        Optional.of(
            String.format("https://proxy.golang.org/%s/@v/list", normalizeModuleName(module)));
    // returns json. This is an optionally implemented endpoint, that goproxy recommends as fallback
    // if /@v/list is empty
    this.latestVersionURL =
        Optional.of(
            String.format("https://proxy.golang.org/%s/@latest", normalizeModuleName(module)));
    this.remoteFileOptions = remoteFileOptions;
  }

  private GoProxyVersionList(String module, String dotInfo, RemoteFileOptions remoteFileOptions) {
    this.module = module;
    this.dotInfoURL = getDotInfoURL(module, dotInfo);
    this.listVersionsURL = Optional.empty();
    this.latestVersionURL = Optional.empty();
    this.remoteFileOptions = remoteFileOptions;
  }

  private Optional<String> getDotInfoURL(String module, String dotInfo) {
    return Optional.of(
        String.format(
            "https://proxy.golang.org/%s/@v/%s.info", normalizeModuleName(module), dotInfo));
  }

  /** Takes upper case A-Z characters and replaces them with !a-z */
  private String normalizeModuleName(String module) {
    return Ascii.toLowerCase(UPPERCASE.matcher(module).replaceAll("!$0"));
  }

  private String executeHTTPQuery(String url) throws RepoException {
    try (InputStream inputStream = remoteFileOptions.getTransport().open(new URL(url))) {
      return new String(inputStream.readAllBytes(), UTF_8);
    } catch (IOException | ValidationException e) {
      throw new RepoException(
          String.format("Failed to query proxy.golang.org for version list at %s", url), e);
    }
  }

  private <T> T executeHTTPQuery(String url, Class<T> cls) throws RepoException {
    try {
      String jsonString = executeHTTPQuery(url);
      return cls.cast(GSON_FACTORY.createJsonParser(jsonString).parse(cls, true));
    } catch (IllegalArgumentException | IOException e) {
      throw new RepoException(
          String.format("Failed to query proxy.golang.org for version list at %s", url), e);
    }
  }

  @Override
  public ImmutableSet<String> list() throws ValidationException {
    try {
      // API caller has a very specific revision/branch release in mind. Just return that or fail
      // trying.
      if (dotInfoURL.isPresent()) {
        // TODO(linjordan): /@v/${branch.info} returns optional fields e.g. {"Time": ...,
        // "Version":..., "Origin": ...}.
        // if in the future, we want to capture or read "Origin" info, consider created a new GSON
        // representation instead of the reuse of GoVersionObject we see here
        GoVersionObject versionObject = getVersionObject(dotInfoURL);
        return ImmutableSet.of(versionObject.getVersion());
      }

      String versionListResponseString = executeHTTPQuery(listVersionsURL.get());
      if (!Strings.isNullOrEmpty(versionListResponseString)) {
        return ImmutableSet.copyOf(versionListResponseString.split("\n"));
      }
      // try the back up endpoint.
      GoVersionObject versionObject = getVersionObject(latestVersionURL);
      return ImmutableSet.of(versionObject.getVersion());
    } catch (RepoException e) {
      throw new ValidationException("Failed to obtain go proxy version list", e);
    }
  }

  private GoVersionObject getVersionObject(Optional<String> dotInfoURL) throws RepoException {
    return executeHTTPQuery(dotInfoURL.get(), GoVersionObject.class);
  }

  @StarlarkMethod(
      name = "get_info",
      doc =
          "Return the results of an info query. An object is only returned if a ref was specified.",
      parameters = {
        @Param(
            name = "ref",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "The reference to query for. This is optional, and the default will be the latest"
                    + " version, or the ref if passed into this object during creation.",
            named = true,
            defaultValue = "None"),
      },
      allowReturnNones = true)
  @Nullable
  public GoVersionObject getInfoQuery(Object ref) throws ValidationException {
    Optional<String> maybeDotInfo = Optional.ofNullable(SkylarkUtil.convertFromNoneable(ref, null));
    try {
      if (maybeDotInfo.isPresent()) {
        return getVersionObject(getDotInfoURL(module, maybeDotInfo.get()));
      } else if (dotInfoURL.isPresent()) {
        return getVersionObject(dotInfoURL);
      } else if (latestVersionURL.isPresent()) {
        return getVersionObject(latestVersionURL);
      }
    } catch (RepoException e) {
      throw new ValidationException("Failed to obtain go proxy version info", e);
    }

    return null;
  }
}
