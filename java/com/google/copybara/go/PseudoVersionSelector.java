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

import com.google.copybara.util.console.Console;
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionSelector;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Optional;
import javax.annotation.Nullable;

/** Given a requested version, if that version is a go pseudoversion, it returns the short sha1. */
public class PseudoVersionSelector implements VersionSelector {

  private static final Pattern GO_PSEUDO_VERSION =
      Pattern.compile("^v?\\d+[.]\\d+[.]\\d+-(?:[\\d+\\w]+[.])?\\d+-([a-f0-9]+)(?:\\+.*)?$");

  @Override
  public Optional<String> select(
      VersionList versionList, @Nullable String requestedRef, Console console) {
    Matcher matcher = GO_PSEUDO_VERSION.matcher(requestedRef);
    if (matcher.matches()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }
}
