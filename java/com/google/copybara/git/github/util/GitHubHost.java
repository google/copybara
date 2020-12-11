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

package com.google.copybara.git.github.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.net.URI;
import java.util.Optional;

/** An object that parses GitHub urls in their components (project, name, etc.) */
public class GitHubHost {

  /** Host for http://github.com (Non-Enterprise) */
  public static final GitHubHost GITHUB_COM = new GitHubHost("github.com");

  private final Pattern gitHubPrUrlPattern;
  private final String host;

  public GitHubHost(String host) {
    this.host = checkNotNull(host);
    this.gitHubPrUrlPattern = Pattern.compile("https://\\Q" + host + "\\E/(.+)/pull/([0-9]+)");
  }

  /**
   * Return the username part of a github url. For example in https://github.com/foo/bar/baz, 'foo'
   * would be the user.
   */
  public String getUserNameFromUrl(String url) throws ValidationException {
    String project = getProjectNameFromUrl(url);
    int i = project.indexOf("/");
    return i == -1 ? project : project.substring(0, i);
  }

  /**
   * Given a GitHub host name and a url that represents a GitHub repository, return the project
   * name, e.g. org/repo.
   */
  public String getProjectNameFromUrl(String url) throws ValidationException {
    checkCondition(!Strings.isNullOrEmpty(url), "Empty url");

    String gitProtocolPrefix = "git@" + host + ":";
    if (url.startsWith(gitProtocolPrefix)) {
      return url.substring(gitProtocolPrefix.length()).replaceAll("([.]git|/)$", "");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Cannot find project name from url " + url);
    }
    if (uri.getScheme() == null) {
      uri = URI.create("notimportant://" + url);
    }
    checkCondition(
        host.equals(uri.getHost()), "Not a github url: %s. Expected host: %s", url, host);

    String name = uri.getPath().replaceAll("^/", "").replaceAll("([.]git|/)$", "");
    Matcher firstTwo = Pattern.compile("^([^/]+/[^/]+).*$").matcher(name);
    if (firstTwo.matches()) {
      name = firstTwo.group(1);
    }

    checkCondition(!Strings.isNullOrEmpty(name), "Cannot find project name from url %s", url);
    return name;
  }

  /** Returns true if url is a GitHub url for a given GitHub or Enterprise host. */
  public boolean isGitHubUrl(String url) {
    try {
      getProjectNameFromUrl(url);
      return true;
    } catch (ValidationException e) {
      return false;
    }
  }

  public String projectAsUrl(String project) {
    return "https://" + host + "/" + project;
  }

  public String normalizeUrl(String url) throws ValidationException {
    return projectAsUrl(getProjectNameFromUrl(url));
  }
  /** Given a reference, parse it as a Github PR data if it is a url for a PR. */
  public Optional<GitHubPrUrl> maybeParseGithubPrUrl(String ref) {
    Matcher matcher = gitHubPrUrlPattern.matcher(ref);
    return matcher.matches()
        ? Optional.of(new GitHubPrUrl(matcher.group(1), Integer.parseInt(matcher.group(2))))
        : Optional.empty();
  }

  /** A GitHub PR project and number */
  public static class GitHubPrUrl {

    private final String project;
    private final int prNumber;

    GitHubPrUrl(String project, int prNumber) {
      this.project = project;
      this.prNumber = prNumber;
    }

    public String getProject() {
      return project;
    }

    public int getPrNumber() {
      return prNumber;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("project", project)
          .add("prNumber", prNumber)
          .toString();
    }
  }
}
