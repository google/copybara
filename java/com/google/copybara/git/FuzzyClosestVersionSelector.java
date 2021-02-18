/*
 * Copyright (C) 2020 Google Inc.
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
package com.google.copybara.git;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A VersionSelector that heuristically tries to match a version to a git tag. This is best effort
 * and only recommended for testing.
 */
public class FuzzyClosestVersionSelector implements VersionSelector {

  @Override
  public String selectVersion(@Nullable String requestedRef, GitRepository repo, String url,
      Console console) throws ValidationException {
    return tryFindVersion(requestedRef, url, repo, console);
  }

  private static String tryFindVersion(
      String version, String url, GitRepository repo, Console console) throws ValidationException {
    ValidationException.checkCondition(!Strings.isNullOrEmpty(version),
        "Fuzzy version finding requires a ref to be explicitly specified");
    if (GitRevision.COMPLETE_SHA1_PATTERN.matcher(version).matches()) {
      return version;
    }
    List<String> tags = getTags(url, repo, console);
    if (tags.contains(version)) {
      return version;
    }
    String cleanedVersion = stripVersion(version);
    for (String tag : tags) {
      if (stripVersion(tag).equals(cleanedVersion)) {
        console.infoFmt("Assuming version %s references %s (%s)", version, tag, cleanedVersion);
        return tag;
      }
    }
    return version;
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

  private static ImmutableList<String> getTags(String url, GitRepository repo, Console console) {
    try {
      return repo.lsRemote(url, ImmutableList.of("refs/tags/*"))
          .keySet()
          .stream()
          .map(s -> s.substring("refs/tags/".length()))
          .collect(toImmutableList());
    } catch (RepoException | ValidationException e) {
      console.warnFmt("Unable to obtain tags for %s. %s", url, e);
    }
    return ImmutableList.of();
  }

  @Override
  public String asGitRefspec() {
    return "refs/tags/*";
  }
}
