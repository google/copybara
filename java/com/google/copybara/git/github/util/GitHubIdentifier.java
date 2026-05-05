/*
 * Copyright (C) 2026 Google LLC.
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

package com.google.copybara.git.github.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.copybara.util.ScpUtil.ScpUrl;
import com.google.copybara.util.ScpUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * Represents an identifier for a GitHub repository, extracting and storing its host, owner (or
 * organization), and repository name.
 *
 * <p>This class handles the parsing of various GitHub repository identifier formats, including
 * standard HTTPS URLs, SSH URLs, and legacy prefixes, normalizing them into a consistent
 * representation.
 */
public final class GitHubIdentifier {

  private static final String GITHUB_DOT_COM_API_URL = "https://api.github.com";
  private static final String GITHUB_DOT_COM_HOST_NAME = "github.com";

  private final String apiUrl;
  private final String hostName;
  private final String ownerOrOrganizationName;
  private final String repoName;

  private GitHubIdentifier(
      String apiUrl, String hostName, String ownerOrOrganizationName, String repoName) {
    this.apiUrl = apiUrl;
    this.hostName = hostName;
    this.ownerOrOrganizationName = ownerOrOrganizationName;
    this.repoName = repoName;
  }

  /**
   * Creates a {@link GitHubIdentifier} from a string identifier.
   *
   * @throws IllegalArgumentException if the URI is invalid or does not conform to the expected
   *     GitHub repository URL format.
   */
  public static GitHubIdentifier create(String identifier) {
    checkNotNull(identifier, "identifier must not be null");
    URI uri;
    try {
      uri = new URI(possiblyReformatIdentifierForCompatibility(identifier));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid URI: " + identifier, e);
    }

    String hostName = uri.getHost();
    checkArgument(!Strings.isNullOrEmpty(hostName), "URI must have a host: %s", identifier);

    String path = uri.getPath();
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (!path.isEmpty() && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    List<String> pathParts = Splitter.on('/').splitToList(path);
    checkArgument(
        pathParts.size() == 2,
        "URI path must contain exactly one '/' separating the owner or organization and the repo "
            + "name. Found path: '%s' in URL: %s",
        path,
        identifier);

    String apiUrl = determineApiUrl(hostName);
    String ownerOrOrganizationName = pathParts.get(0);
    String repoName = pathParts.get(1);

    return new GitHubIdentifier(apiUrl, hostName, ownerOrOrganizationName, repoName);
  }

  private static String possiblyReformatIdentifierForCompatibility(String identifier) {
    // ".git" suffix on repoName causes errors when passed to the GitHub API
    if (identifier.endsWith(".git")) {
      identifier = identifier.substring(0, identifier.length() - 4);
    }

    // GitHub accepts SCP URLs so Copybara does too but for simplicity we reformat to an SSH URL.
    Optional<ScpUrl> scpUrl = ScpUtil.parseScpUrl(identifier);
    if (scpUrl.isPresent()) {
      identifier = "ssh://git@" + scpUrl.get().host() + "/" + scpUrl.get().path();
    }
    return identifier;
  }

  public String getApiUrl() {
    return apiUrl;
  }

  /**
   * Returns the normalized HTTPS URL of the GitHub repository.
   *
   * <p>This reconstructs the URL using the stored host, owner, and repository name, ensuring a
   * standard format regardless of whether the identifier was originally created from an HTTPS, SSH,
   * or legacy URL.
   */
  public String getUrl() {
    return "https://" + hostName + "/" + getPath();
  }

  /** Returns the host name of the GitHub URL (e.g., "github.com"). */
  public String getHostName() {
    return hostName;
  }

  /**
   * Returns the owner or organization name from the GitHub URL. WRT GitHub.com this is considered
   * the owner. WRT GitHub Enterprise this is considered the organization.
   */
  public String getOwnerOrOrganizationName() {
    return ownerOrOrganizationName;
  }

  /** Returns the repository name from the GitHub URL. */
  public String getRepoName() {
    return repoName;
  }

  /** Returns the path in the format "owner/repo". */
  public String getPath() {
    return ownerOrOrganizationName + "/" + repoName;
  }

  private static String determineApiUrl(String hostName) {
    // GitHub.com has a unique API URL.
    // GitHub Enterprise instances have a specific format of API URL.
    if (hostName.equals(GITHUB_DOT_COM_HOST_NAME)) {
      return GITHUB_DOT_COM_API_URL;
    }
    return "https://" + hostName + "/api/v3";
  }
}
