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

package com.google.copybara.version;

import com.google.common.base.CharMatcher;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A version selector that, given a requested version, tries to find a
 * matching tag by using several fuzzing heuristics.
 */
public class CorrectorVersionSelector implements VersionSelector {

  private final Console console;

  public CorrectorVersionSelector(Console console) {
    this.console = console;
  }

  @Override
  public Optional<String> select(VersionList versionList, @Nullable String requestedRef,
      Console console)
      throws ValidationException, RepoException {
    if (requestedRef == null) {
      return Optional.empty();
    }
    String cleanedVersion = stripVersion(requestedRef);
    for (String tag : versionList.list()) {
      if (stripVersion(tag).equals(cleanedVersion)) {
        this.console.infoFmt("Assuming version %s references %s (%s)", requestedRef, tag,
            cleanedVersion);
        return Optional.of(tag);
      }
    }
    return Optional.empty();
  }

  private static String stripVersion(String version) {
    String strippedPrefix = CharMatcher.inRange('0', '9').negate().trimLeadingFrom(version);
    String normalizedSeparator = CharMatcher.anyOf(",;-_").replaceFrom(strippedPrefix, '.');
    String strippedVersion = "";
    int index = 0;
    CharMatcher isVersionPart = CharMatcher.inRange('0', '9').or(CharMatcher.is('.'));

    while (index < normalizedSeparator.length()) {
      if (normalizedSeparator.regionMatches(true, index, "RC", 0, 2)) {
        strippedVersion += "RC";
        index += 2;
        continue;
      }
      if (normalizedSeparator.regionMatches(true, index, "PL", 0, 2)) {
        strippedVersion += "PL";
        index += 2;
        continue;
      }
      if (isVersionPart.matches(normalizedSeparator.charAt(index))) {
        strippedVersion += normalizedSeparator.charAt(index);
        index++;
        continue;
      }
      // fast-forward through strings that might contain but not start with RC/PL
      while (index < normalizedSeparator.length()
          && !isVersionPart.matches(normalizedSeparator.charAt(index))) {
        index++;
      }
    }
    return strippedVersion;
  }
}
