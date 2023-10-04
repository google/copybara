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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.json.GsonParserUtil;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.version.VersionList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.starlark.java.eval.StarlarkValue;

/** Used to fetch a list of versions for a Rust crate at crates.io */
public final class RustCratesIoVersionList implements VersionList, StarlarkValue {

  private static final String CRATES_IO_INDEX_URL = "https://index.crates.io";

  private final String crateName;

  private final RemoteFileOptions remoteFileOptions;

  public static RustCratesIoVersionList forCrate(
      String crate, RemoteFileOptions remoteFileOptions) {
    return new RustCratesIoVersionList(crate, remoteFileOptions);
  }

  private RustCratesIoVersionList(String crateName, RemoteFileOptions remoteFileOptions) {
    this.crateName = crateName;
    this.remoteFileOptions = remoteFileOptions;
  }

  @Override
  public ImmutableSet<String> list() throws RepoException {
    return getVersionList().stream()
        .map(RustRegistryVersionObject::getVers)
        .collect(toImmutableSet());
  }

  ImmutableSet<RustRegistryVersionObject> getVersionList() throws RepoException {
    String url = CRATES_IO_INDEX_URL;

    int nameLength = crateName.length();
    String indexCrateName = Ascii.toLowerCase(crateName);

    if (nameLength <= 2) {
      // If the crate name's length is less than or equal to 2, then the version info is located at
      // /<name length>/<crate name>
      url += String.format("/%d/%s", nameLength, indexCrateName);
    } else if (nameLength == 3) {
      // If the crate name's length is equal to 3, then the version info is at:
      // /3/<first char>/<crate name>
      url += String.format("/%d/%c/%s", nameLength, indexCrateName.charAt(0), indexCrateName);
    } else {
      url +=
          String.format(
              "/%s/%s/%s",
              indexCrateName.substring(0, 2), indexCrateName.substring(2, 4), indexCrateName);
    }

    BufferedReader reader = new BufferedReader(new StringReader(executeHTTPQuery(url)));
    ImmutableSet.Builder<RustRegistryVersionObject> versionList = ImmutableSet.builder();
    String jsonString;
    try {
      while ((jsonString = reader.readLine()) != null) {
        versionList.add(
            (RustRegistryVersionObject)
                GsonParserUtil.parseString(jsonString, RustRegistryVersionObject.class, false));
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new RepoException(
          String.format("Failed to query crates.io-index for version list at %s", url), e);
    }

    return versionList.build();
  }

  private String executeHTTPQuery(String url) throws RepoException {
    try (InputStream inputStream = remoteFileOptions.getTransport().open(new URL(url))) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | ValidationException e) {
      throw new RepoException(
          String.format("Failed to query crates.io-index for version list at %s", url), e);
    }
  }
}
