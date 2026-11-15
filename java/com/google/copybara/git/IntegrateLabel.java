/*
 * Copyright (C) 2017 Google Inc.
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

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * A label value that describes what to integrate.
 */
public interface IntegrateLabel {

  /** Get the merge message */
  String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) throws ValidationException;

  /**
   * Get the revision to integrate
   */
  GitRevision getRevision() throws RepoException, ValidationException;

  static IntegrateLabel genericGitRevision(
      GitRepository repository,
      String repoUrl,
      String rawLabelValue,
      GeneralOptions generalOptions,
      List<String> allowedRefPrefixes,
      boolean allowProtocolRef)
      throws RepoException, ValidationException {
    String rawLabel = rawLabelValue.trim();
    if (rawLabel.contains("://")) {
      ValidationException.checkCondition(
          allowProtocolRef,
          "Protocol markers (://) are not allowed in integrate ref '%s' unless"
              + " 'allow_protocol_ref' is set.",
          rawLabel);
      int spaceIdx = rawLabel.lastIndexOf(' ');
      String labelUrl = spaceIdx != -1 ? rawLabel.substring(0, spaceIdx) : rawLabel;
      String repoUrlPrefix = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
      boolean matchesDestination = labelUrl.equals(repoUrl) || labelUrl.startsWith(repoUrlPrefix);
      if (!matchesDestination) {
        if (allowedRefPrefixes.isEmpty()) {
          throw new ValidationException(
              String.format(
                  "Integrate URL in '%s' must match or have destination repository URL '%s' as a"
                      + " prefix.",
                  rawLabel, repoUrl));
        }
        ValidationException.checkCondition(
            IntegrateUrlPrefixes.matchesAny(labelUrl, allowedRefPrefixes),
            "Integrate URL in '%s' does not match destination repository URL '%s' or any allowed"
                + " prefix (%s).",
            rawLabel,
            repoUrl,
            allowedRefPrefixes);
      }
    }
    // would derive url from repo remote, but there is no guarantee a remote is configured.
    GitRevision gitRevision =
        GitRepoType.GIT.resolveRef(
            repository,
            repoUrl,
            rawLabel,
            generalOptions,
            /* describeVersion= */ false,
            /* partialFetch= */ false,
            Optional.empty(),
            /* recurseSubmodules= */ false);
    return genericGitRevision(gitRevision);
  }

  static IntegrateLabel genericGitRevision(GitRevision revision) {
    Preconditions.checkNotNull(revision);
    return new IntegrateLabel() {
      @Override
      public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd)
          throws ValidationException {
        return IntegrateLabel.withLabels("Merge of " + revision.getHash(), labelsToAdd);
      }

      @Override
      public GitRevision getRevision() {
        return revision;
      }

      @Override
      public String toString() {
        return "Merge of " + revision.getHash();
      }
    };
  }

  static String withLabels(String msg, ImmutableList<LabelFinder> labelsToAdd)
      throws ValidationException {
    ChangeMessage result = ChangeMessage.parseMessage(msg);
    for (LabelFinder labelFinder : labelsToAdd) {
      result = result.withLabel(
          labelFinder.getName(), labelFinder.getSeparator(), labelFinder.getValue());
    }
    return result.toString();
  }
}

/**
 * Matches an integrate label URL against the URL prefixes allowed by the configuration.
 *
 * <p>Matching is done on URL boundaries instead of plain string prefixes, so a prefix of {@code
 * https://github.com} does not allow {@code https://github.com.evil.example}, and a prefix of
 * {@code https://github.com/google} does not allow {@code https://github.com/google-evil}.
 */
final class IntegrateUrlPrefixes {

  private IntegrateUrlPrefixes() {}

  /** Returns true if {@code url} is contained in at least one of {@code allowedRefPrefixes}. */
  static boolean matchesAny(String url, Iterable<String> allowedRefPrefixes)
      throws ValidationException {
    for (String prefix : allowedRefPrefixes) {
      if (matches(url, prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if {@code url} is contained in {@code prefix} on a URL boundary. Both values are
   * parsed as URIs, and the scheme, host, effective port and path are compared.
   */
  static boolean matches(String url, String prefix) throws ValidationException {
    URI urlUri = parseUrl(url);
    URI prefixUri = parseAllowedRefPrefix(prefix);
    return equalsIgnoreCase(urlUri.getScheme(), prefixUri.getScheme())
        && equalsIgnoreCase(urlUri.getHost(), prefixUri.getHost())
        && effectivePort(urlUri) == effectivePort(prefixUri)
        && pathMatches(urlUri.getPath(), prefixUri.getPath());
  }

  /**
   * Validates an allowed prefix used in the configuration.
   *
   * <p>User information, queries and fragments are rejected because they are not part of a
   * repository identity and would make prefix matching ambiguous.
   */
  static void validateAllowedRefPrefix(String prefix) throws ValidationException {
    parseAllowedRefPrefix(prefix);
  }

  private static URI parseAllowedRefPrefix(String prefix) throws ValidationException {
    ValidationException.checkCondition(
        prefix != null && !prefix.trim().isEmpty(),
        "Allowed integrate URL prefix cannot be empty.");
    URI uri;
    try {
      uri = new URI(prefix.trim());
    } catch (URISyntaxException e) {
      throw new ValidationException(
          String.format("Invalid allowed integrate URL prefix '%s': %s", prefix, e.getMessage()),
          e);
    }
    ValidationException.checkCondition(
        uri.isAbsolute() && !uri.isOpaque(),
        "Allowed integrate URL prefix '%s' must be an absolute hierarchical URL.",
        prefix);
    ValidationException.checkCondition(
        uri.getRawUserInfo() == null,
        "Allowed integrate URL prefix '%s' must not contain user information.",
        prefix);
    ValidationException.checkCondition(
        uri.getRawQuery() == null && uri.getRawFragment() == null,
        "Allowed integrate URL prefix '%s' must not contain a query or fragment.",
        prefix);
    return uri;
  }

  private static URI parseUrl(String url) throws ValidationException {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new ValidationException(
          String.format("Invalid integrate URL '%s': %s", url, e.getMessage()), e);
    }
  }

  private static boolean pathMatches(String urlPath, String prefixPath) {
    String normalizedUrlPath = urlPath == null ? "" : urlPath;
    String normalizedPrefixPath = prefixPath == null ? "" : prefixPath;
    if (normalizedUrlPath.equals(normalizedPrefixPath)) {
      return true;
    }
    if (!normalizedUrlPath.startsWith(normalizedPrefixPath)) {
      return false;
    }
    return normalizedPrefixPath.endsWith("/")
        || normalizedUrlPath.charAt(normalizedPrefixPath.length()) == '/';
  }

  private static boolean equalsIgnoreCase(String left, String right) {
    if (left == null || right == null) {
      return left == right;
    }
    return Ascii.equalsIgnoreCase(left, right);
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    String scheme = uri.getScheme();
    if (scheme == null) {
      return -1;
    }
    return switch (Ascii.toLowerCase(scheme)) {
      case "http" -> 80;
      case "https" -> 443;
      default -> -1;
    };
  }
}
